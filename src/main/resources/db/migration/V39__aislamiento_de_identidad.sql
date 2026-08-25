-- =====================================================================
-- V39 — `users` Y `password_resets` PASAN A AISLAR DE VERDAD POR NEGOCIO.
--
-- Es el último candado del aislamiento entre negocios. Con esta migración y la
-- V40, ninguna tabla con `tenant_id` queda con política abierta.
--
-- ── El defecto ────────────────────────────────────────────────────────
--
-- V4:47-48 y V9:24-26 crearon las dos tablas con la política de siempre:
--
--     CREATE POLICY app_rw_<tabla> ON <tabla> FOR ALL TO app_user
--         USING (true) WITH CHECK (true);
--
-- Y a diferencia de las 17 de V33 o las 3 de V38, aquí la cabecera de V4:33-35
-- lo dice con todas las letras: *"El aislamiento entre negocios aquí es
-- responsabilidad de la app (el login ocurre sin tenant en contexto), NO de una
-- política por tenant."*
--
-- Esa frase describe una decisión consciente, no un descuido. El problema es
-- que **la app no cumple su parte**: `AuthRepository.findUserByEmail:43-49`
-- busca por email sin filtrar por negocio, y `buscarReset` busca por hash de
-- token sin filtrar por negocio. No pueden hacerlo: en los dos casos el negocio
-- es lo que se está averiguando.
--
-- Demostrado en Staging (`ENSAYO-STAGING.md`): con `app.tenant_id = 'qa-alfa'`,
-- `users` devuelve la cuenta de `qa-beta`. Lo que queda expuesto son
-- credenciales: el hash de contraseña de todos los usuarios de la plataforma, y
-- los tokens vivos de recuperación de cualquiera. Con un segundo cliente, eso
-- deja de ser una nota de auditoría.
--
-- ── Por qué esto NO se resuelve como V33 y V38 ────────────────────────
--
-- Las 20 tablas anteriores se leen SIEMPRE dentro de una petición autenticada,
-- así que bastaba con cerrar la política. Aquí no:
--
--   · `/auth/login` busca el usuario **para averiguar cuál es su negocio**.
--     Pedirle que fije el negocio antes es pedirle el dato que va a buscar.
--   · `/auth/reset-password` llega con un token y nada más. No hay sesión, no
--     hay JWT, y el negocio está justamente en la fila que hay que encontrar.
--
-- Es huevo y gallina de verdad, no un olvido de `set_config`. La salida es una
-- función `SECURITY DEFINER` de alcance mínimo: un agujero **del tamaño exacto**
-- de la consulta que hace falta, en vez de una política abierta que deja pasar
-- cualquier consulta.
--
-- ── Las cuatro funciones, y por qué son cuatro y no dos ───────────────
--
-- El encargo pedía dos. Al trazar TODOS los accesos aparecieron dos más que
-- también son cross-tenant por naturaleza y que, sin cubrirlos, romperían en
-- silencio:
--
-- 1. `buscar_usuario_para_login(email)` — el login y `/auth/forgot-password`.
--
-- 2. `buscar_token_de_reset(hash)` — `/auth/reset-password`.
--
-- 3. `existe_email(email)` — **`users.email` es UNIQUE GLOBAL** (V4:23), así que
--    comprobar si está libre es forzosamente una pregunta cross-tenant. Sin
--    esta función, `AuthService.register:134` y `createUser:274` verían siempre
--    "libre", el INSERT chocaría contra el índice único y el usuario recibiría
--    un 500 donde hoy recibe un 409 con su explicación. Devuelve un booleano y
--    nada más: es el mínimo absoluto.
--
-- 4. `contar_usuarios_por_negocio()` — `SuperAdminRepository.listTenants:48`
--    cuenta los usuarios de CADA negocio para el panel del KAM, que es
--    cross-tenant por definición. Sin ella los conteos pasarían todos a 0 sin
--    error: un número equivocado en pantalla, que es peor que una pantalla en
--    blanco. Devuelve conteos, nunca filas de usuario.
--
-- ── Lo que NO lleva función, y es la mayoría ──────────────────────────
--
-- Otros cinco accesos a `users` ocurren sin negocio en sesión y **todos se
-- arreglan fijando el negocio**, porque en todos ellos ya se conoce:
--
--   · `register` — tras crear el negocio (se resuelve en el código, no aquí)
--   · `resetPassword` — el negocio sale de la fila del token
--   · `forgotPassword` — el negocio sale del usuario recién encontrado
--   · `AltaDeNegocioService` — ya fija el negocio, pero DESPUÉS del INSERT de
--     `users`; basta con moverlo antes
--   · `createUser`, `changePassword`, `listUsers`, `UsuarioDeLaPeticion` — van
--     dentro de petición autenticada y no necesitan nada
--
-- Darles una función privilegiada a estos habría sido ampliar superficie sin
-- motivo. La regla que se siguió: **función solo donde el negocio es
-- desconocido en el momento de la consulta.**
--
-- ── Las cuatro condiciones de las funciones ───────────────────────────
--
-- 1. DEVUELVEN EL MÍNIMO. Ninguna hace `SELECT *`.
--    `buscar_usuario_para_login` devuelve cinco columnas y **no devuelve
--    `users.id`** —que el encargo sí listaba— porque el login no lo usa:
--    `issueToken` firma con (tenant, email, rol) y `AuthResponse` no lo
--    expone. Devolverlo solo aumentaría lo que se saca de una tabla de
--    credenciales sin que nadie lo consumiera.
--    `buscar_token_de_reset` devuelve el email y el negocio **solo si el token
--    es válido**; para un token vencido o usado van en NULL.
--
-- 2. `STABLE`, nunca `VOLATILE`, y `SET search_path = public, pg_temp`
--    explícito. Sin fijar el `search_path`, una función `SECURITY DEFINER` se
--    puede secuestrar creando un objeto homónimo en un esquema que vaya antes
--    en la ruta de búsqueda: el atacante ejecuta código con los privilegios del
--    dueño. Es la vía de inyección clásica de este patrón.
--
-- 3. `REVOKE ALL FROM PUBLIC` y `GRANT EXECUTE` solo a `app_user`. Por defecto
--    Postgres concede EXECUTE a PUBLIC en toda función nueva: sin el REVOKE,
--    los roles `anon` y `authenticated` de PostgREST podrían llamarlas por la
--    API REST de Supabase. Eso convertiría la función en un endpoint público
--    que devuelve hashes de contraseña.
--
-- 4. Cada una lleva su `COMMENT ON FUNCTION` explicando por qué existe.
--
-- ── El oráculo de enumeración: lo que esto NO empeora ─────────────────
--
-- `buscar_usuario_para_login` devuelve una fila si el email existe y ninguna si
-- no. Eso permite distinguir cuentas registradas de no registradas, y no hay
-- forma de evitarlo: es la pregunta que el login tiene que hacer.
--
-- No empeora nada, y conviene tenerlo escrito:
--   · `POST /auth/login` ya expone esa misma diferencia, y de hecho `AuthService`
--     se esfuerza en NO exponerla: devuelve el mismo 401 "Credenciales
--     inválidas" para "no existe" y para "clave mala" (`AuthService:89-94`).
--   · La función no es alcanzable desde fuera: solo `app_user` puede ejecutarla.
--   · `/auth/login` tiene cupo de 10 fallos por IP cada 15 minutos
--     (`RegisterRateLimiter`, bucket LOGIN), que es lo que hace inviable barrer
--     una lista de correos.
-- `existe_email` sí responde en claro, pero sus tres llamadores exigen la clave
-- de registro del KAM o un JWT de admin: no hay camino público hasta ella.
--
-- ── VERIFICACIÓN PREVIA OBLIGATORIA (solo lectura) ────────────────────
--
--     SELECT tenant_id, count(*) FROM users GROUP BY 1 ORDER BY 1;
--     SELECT count(*) FROM users u
--      WHERE NOT EXISTS (SELECT 1 FROM tenants t WHERE t.id = u.tenant_id);
--     SELECT tenant_id, count(*) FROM password_resets GROUP BY 1 ORDER BY 1;
--
-- Una fila de `users` atribuida al negocio equivocado deja de ser visible al
-- cerrar la política, y su dueño **no podrá entrar**. Corregir ANTES.
--
-- ── ROLLBACK ──────────────────────────────────────────────────────────
--
-- El bloque DOWN está al final, comentado. Devuelve las políticas abiertas y
-- quita FORCE; las funciones se pueden dejar (no estorban) o borrar. Volver
-- atrás REABRE el agujero: solo para restituir servicio.
--
-- ── NUMERACIÓN ────────────────────────────────────────────────────────
--
-- La última es V38 (contadores y bitácoras). V19 quedó sin usar a propósito
-- (V21:21-23). `tenant_modules` va en V40, con el arreglo del endpoint del KAM
-- que si no dejaría de escribir en silencio.
-- =====================================================================

SET lock_timeout = '3s';

-- ---------------------------------------------------------------------
-- 1. buscar_usuario_para_login
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.buscar_usuario_para_login(p_email TEXT)
RETURNS TABLE (email TEXT, tenant_id TEXT, password_hash TEXT, rol TEXT, activo BOOLEAN)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT u.email, u.tenant_id, u.password_hash, u.role, (u.status = 'active')
    FROM public.users u
    WHERE lower(u.email) = lower(p_email);
$$;

COMMENT ON FUNCTION public.buscar_usuario_para_login(TEXT) IS
'Unico camino por el que se puede leer `users` sin negocio en sesion.

Existe porque el login busca al usuario PARA AVERIGUAR cual es su negocio:
pedirle que fije `app.tenant_id` antes seria pedirle el dato que va a buscar.
Es huevo y gallina de verdad, no un olvido de set_config.

Devuelve cinco columnas y ni una mas. NO devuelve users.id: el login no lo usa
(issueToken firma con tenant+email+rol) y sacarlo de una tabla de credenciales
sin que nadie lo consuma solo aumenta lo expuesto.

Llamadores: AuthService.login y AuthService.forgotPassword. Todo lo demas que
lee `users` va por RLS normal.';

-- ---------------------------------------------------------------------
-- 2. buscar_token_de_reset
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.buscar_token_de_reset(p_hash TEXT)
RETURNS TABLE (estado TEXT, email TEXT, tenant_id TEXT)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT
        CASE WHEN r.used THEN 'usado'
             WHEN r.expires_at <= now() THEN 'vencido'
             ELSE 'valido' END,
        CASE WHEN NOT r.used AND r.expires_at > now() THEN r.email END,
        CASE WHEN NOT r.used AND r.expires_at > now() THEN r.tenant_id END
    FROM public.password_resets r
    WHERE r.token_hash = p_hash;
$$;

COMMENT ON FUNCTION public.buscar_token_de_reset(TEXT) IS
'Unico camino por el que se puede leer `password_resets` sin negocio en sesion.

Una peticion de /auth/reset-password llega con un token y nada mas: sin JWT, sin
sesion, y el negocio esta justamente en la fila que hay que encontrar.

Devuelve el email y el negocio SOLO si el token es valido; para uno vencido o ya
usado van en NULL. Son los dos unicos datos personales de la fila y para decidir
que un token no sirve no hacen ninguna falta.

`estado` es un enum cerrado: valido | vencido | usado (regla 10 de LINEAMIENTOS).
Sin fila = el token no existe, y el llamador lo traduce a `no_existe`. La
precedencia es deliberada: usado gana a vencido, porque saber que el flujo llego
al final alguna vez cambia por donde se busca el problema.

El mensaje HTTP sigue siendo el mismo para los cuatro casos, a proposito: aqui se
distingue para el log, no para la respuesta.';

-- ---------------------------------------------------------------------
-- 3. existe_email
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.existe_email(p_email TEXT)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT EXISTS (SELECT 1 FROM public.users u WHERE lower(u.email) = lower(p_email));
$$;

COMMENT ON FUNCTION public.existe_email(TEXT) IS
'Si un email ya esta tomado, en cualquier negocio de la plataforma.

users.email es UNIQUE GLOBAL (V4:23), asi que la pregunta es forzosamente
cross-tenant. Sin esta funcion, register y createUser verian siempre "libre", el
INSERT chocaria contra el indice unico y el usuario recibiria un 500 donde hoy
recibe un 409 con su explicacion.

Devuelve un booleano y nada mas: el minimo absoluto. Sus tres llamadores exigen
la clave de registro del KAM o un JWT de admin, asi que no hay camino publico
hasta ella.';

-- ---------------------------------------------------------------------
-- 4. contar_usuarios_por_negocio
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.contar_usuarios_por_negocio()
RETURNS TABLE (tenant_id TEXT, usuarios BIGINT)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT u.tenant_id, count(*) FROM public.users u GROUP BY u.tenant_id;
$$;

COMMENT ON FUNCTION public.contar_usuarios_por_negocio() IS
'Cuantos usuarios tiene cada negocio. Para el panel del KAM, que es cross-tenant
por definicion (SuperAdminRepository.listTenants).

Sin ella los conteos pasarian todos a 0 sin dar error: un numero equivocado en
pantalla, que es peor que una pantalla en blanco.

Devuelve conteos, NUNCA filas de usuario. Ni emails, ni hashes, ni roles.';

-- ---------------------------------------------------------------------
-- Permisos de las cuatro. Por defecto Postgres concede EXECUTE a PUBLIC en
-- toda funcion nueva: sin el REVOKE, los roles `anon` y `authenticated` de
-- PostgREST podrian llamarlas por la API REST de Supabase, y eso convertiria
-- `buscar_usuario_para_login` en un endpoint publico que devuelve hashes.
-- ---------------------------------------------------------------------
REVOKE ALL ON FUNCTION public.buscar_usuario_para_login(TEXT)  FROM PUBLIC;
REVOKE ALL ON FUNCTION public.buscar_token_de_reset(TEXT)      FROM PUBLIC;
REVOKE ALL ON FUNCTION public.existe_email(TEXT)               FROM PUBLIC;
REVOKE ALL ON FUNCTION public.contar_usuarios_por_negocio()    FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.buscar_usuario_para_login(TEXT) TO app_user;
GRANT EXECUTE ON FUNCTION public.buscar_token_de_reset(TEXT)     TO app_user;
GRANT EXECUTE ON FUNCTION public.existe_email(TEXT)              TO app_user;
GRANT EXECUTE ON FUNCTION public.contar_usuarios_por_negocio()   TO app_user;

-- ---------------------------------------------------------------------
-- Cierre de las politicas. Mismo patron que V33/V38: se borra POR CATALOGO,
-- no por nombre construido — la leccion de V38, donde la politica de
-- tenant_order_counters se llamaba `app_rw_order_counters` y un DROP por
-- nombre la habria dejado viva, anulando el aislamiento con todo en verde.
-- ---------------------------------------------------------------------
DO $aislar$
DECLARE
    t   TEXT;
    pol TEXT;
    tablas TEXT[] := ARRAY['users', 'password_resets'];
BEGIN
    FOREACH t IN ARRAY tablas LOOP
        FOR pol IN
            SELECT policyname FROM pg_policies
            WHERE schemaname = 'public' AND tablename = t
        LOOP
            EXECUTE format('DROP POLICY %I ON public.%I', pol, t);
        END LOOP;

        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', t);

        -- Sin clausula TO, igual que V1/V33/V38: asi alcanza tambien al dueno,
        -- que es lo que hace que FORCE signifique algo.
        EXECUTE format(
            'CREATE POLICY tenant_isolation_%I ON public.%I '
            'USING (tenant_id = current_setting(''app.tenant_id'', true)) '
            'WITH CHECK (tenant_id = current_setting(''app.tenant_id'', true))', t, t);
    END LOOP;
END $aislar$;

-- `users` ya tiene idx_users_tenant desde V4:31. `password_resets` solo tenia
-- un indice sobre lower(email) (V9:20): con la politica filtrando por tenant_id
-- en cada consulta, sin este toda consulta escanea la tabla entera. Y esa tabla
-- no se purga nunca: crece indefinidamente (ver NOTAS.md).
CREATE INDEX IF NOT EXISTS idx_password_resets_tenant
    ON public.password_resets (tenant_id);

-- Permisos reafirmados tal como estaban, sin ampliar (leccion de V20/V22: el
-- permiso es una capa distinta de RLS y se evalua primero).
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.users           TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.password_resets TO app_user;
GRANT USAGE, SELECT ON SEQUENCE public.users_id_seq TO app_user;

-- ---------------------------------------------------------------------
-- Verificacion: la migracion FALLA si algo quedo a medias.
--
-- No comprueba "existe una politica tenant_isolation_*" —eso seria cierto
-- tambien con la politica abierta viva al lado—. Comprueba que NO queda
-- ninguna abierta, que FORCE esta puesto, y que las cuatro funciones son
-- SECURITY DEFINER, STABLE, con search_path fijado y sin EXECUTE para PUBLIC.
-- ---------------------------------------------------------------------
DO $verificar$
DECLARE
    t       TEXT;
    f       TEXT;
    tablas  TEXT[] := ARRAY['users', 'password_resets'];
    funcs   TEXT[] := ARRAY['buscar_usuario_para_login', 'buscar_token_de_reset',
                            'existe_email', 'contar_usuarios_por_negocio'];
    n_pol      INT;
    n_abiertas INT;
    forzada    BOOLEAN;
    fila       RECORD;
BEGIN
    FOREACH t IN ARRAY tablas LOOP
        SELECT count(*) INTO n_pol
        FROM pg_policies WHERE schemaname = 'public' AND tablename = t;
        IF n_pol <> 1 THEN
            RAISE EXCEPTION
                'V39: %.% quedo con % politicas; se esperaba exactamente 1. Con mas '
                'de una, Postgres las combina con OR y basta que una sea abierta '
                'para anular el aislamiento.', 'public', t, n_pol;
        END IF;

        SELECT count(*) INTO n_abiertas
        FROM pg_policies
        WHERE schemaname = 'public' AND tablename = t
          AND (coalesce(qual, '') = 'true' OR coalesce(with_check, '') = 'true');
        IF n_abiertas > 0 THEN
            RAISE EXCEPTION 'V39: %.% conserva una politica abierta.', 'public', t;
        END IF;

        SELECT c.relforcerowsecurity INTO forzada
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relname = t;
        IF NOT coalesce(forzada, false) THEN
            RAISE EXCEPTION 'V39: %.% no tiene FORCE ROW LEVEL SECURITY.', 'public', t;
        END IF;
    END LOOP;

    FOREACH f IN ARRAY funcs LOOP
        SELECT p.prosecdef, p.provolatile, p.proconfig, p.proacl
          INTO fila
        FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public' AND p.proname = f;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'V39: falta la funcion public.%', f;
        END IF;
        IF NOT fila.prosecdef THEN
            RAISE EXCEPTION 'V39: public.% no es SECURITY DEFINER; no serviria '
                            'para leer sin negocio en sesion.', f;
        END IF;
        IF fila.provolatile <> 's' THEN
            RAISE EXCEPTION 'V39: public.% no es STABLE.', f;
        END IF;
        IF fila.proconfig IS NULL
           OR NOT EXISTS (SELECT 1 FROM unnest(fila.proconfig) c
                          WHERE c LIKE 'search_path=%') THEN
            RAISE EXCEPTION
                'V39: public.% no fija search_path. Una funcion SECURITY DEFINER '
                'sin search_path fijado se puede secuestrar con un objeto homonimo '
                'en otro esquema.', f;
        END IF;
        -- proacl NULL significa "permisos por defecto", y el defecto de una
        -- funcion es EXECUTE para PUBLIC. O sea que NULL aqui es el fallo.
        IF fila.proacl IS NULL THEN
            RAISE EXCEPTION
                'V39: public.% conserva los permisos por defecto, que incluyen '
                'EXECUTE para PUBLIC: seria alcanzable desde PostgREST.', f;
        END IF;
        IF EXISTS (SELECT 1 FROM unnest(fila.proacl) a WHERE a::text LIKE '=X/%') THEN
            RAISE EXCEPTION 'V39: public.% tiene EXECUTE concedido a PUBLIC.', f;
        END IF;
    END LOOP;
END $verificar$;

-- Lo que este archivo NO puede comprobar por si solo:
--   · que `app_user` (no el dueno) ve solo lo suyo. Esta migracion corre como
--     `postgres`, que tiene BYPASSRLS: desde aqui TODO se ve, siempre. Una
--     comprobacion de aislamiento escrita aqui daria verde en los dos
--     escenarios y por eso no esta escrita aqui.
--   · que el login, el registro y la recuperacion de contrasena siguen
--     funcionando. Eso solo lo dice una llamada a la API real.

-- =====================================================================
-- DOWN — rollback explicito. Reabre el agujero: solo para restituir servicio.
-- =====================================================================
--
-- DO $revertir$
-- DECLARE
--     t   TEXT;
--     pol TEXT;
--     tablas TEXT[] := ARRAY['users', 'password_resets'];
-- BEGIN
--     FOREACH t IN ARRAY tablas LOOP
--         FOR pol IN
--             SELECT policyname FROM pg_policies
--             WHERE schemaname = 'public' AND tablename = t
--         LOOP
--             EXECUTE format('DROP POLICY %I ON public.%I', pol, t);
--         END LOOP;
--         EXECUTE format('ALTER TABLE public.%I NO FORCE ROW LEVEL SECURITY', t);
--         EXECUTE format('CREATE POLICY app_rw_%I ON public.%I FOR ALL TO app_user '
--                        || 'USING (true) WITH CHECK (true)', t, t);
--     END LOOP;
-- END $revertir$;
--
-- Las funciones NO hay que borrarlas para revertir: con la politica abierta
-- sobran, pero no estorban ni amplian nada (solo app_user puede ejecutarlas).
-- Si aun asi se quieren fuera:
-- DROP FUNCTION IF EXISTS public.buscar_usuario_para_login(TEXT);
-- DROP FUNCTION IF EXISTS public.buscar_token_de_reset(TEXT);
-- DROP FUNCTION IF EXISTS public.existe_email(TEXT);
-- DROP FUNCTION IF EXISTS public.contar_usuarios_por_negocio();
-- DROP INDEX IF EXISTS public.idx_password_resets_tenant;
