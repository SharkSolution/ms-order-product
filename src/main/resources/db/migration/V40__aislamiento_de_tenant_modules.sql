-- =====================================================================
-- V40 — `tenant_modules` PASA A AISLAR DE VERDAD POR NEGOCIO.
--
-- Es la última tabla con `tenant_id` y política abierta. Con esta migración
-- aplicada, ninguna queda.
--
-- ── El defecto ────────────────────────────────────────────────────────
--
-- V7:23-24 la creó con la política de siempre:
--
--     CREATE POLICY app_rw_tenant_modules ON tenant_modules FOR ALL TO app_user
--         USING (true) WITH CHECK (true);
--
-- La cabecera de V7:8-10 explicaba por qué: *"Tabla GLOBAL de configuración
-- (como tenants/users): se lee en el login (sin tenant en contexto) y la
-- gestiona el admin."* Y era cierto.
--
-- Lo que hay dentro son los módulos regalados o revocados a cada negocio: qué
-- ve y qué no ve cada cliente en su panel. Con política abierta, un negocio ve
-- —y puede modificar— la configuración comercial de los demás.
--
-- ── Por qué esto NO necesita una función privilegiada ─────────────────
--
-- `users` y `password_resets` (V39) necesitan funciones `SECURITY DEFINER`
-- porque se consultan para AVERIGUAR cuál es el negocio. Aquí no pasa eso.
--
-- Se lee en `AuthService.login:107`, vía `effectiveModulesFor`. Pero para esa
-- línea el negocio ya está resuelto: lo devolvió `findTenant` cuatro líneas
-- antes. El único problema era que nadie lo fijaba en la sesión.
--
-- Así que la solución es la barata, y darle a esta tabla una función
-- privilegiada habría sido ampliar superficie sin ningún motivo. La regla que
-- separa los dos casos: **función solo donde el negocio es desconocido en el
-- momento de la consulta.**
--
-- Comprobado antes de escribir nada, contra `tenant_order_counters` (ya cerrada
-- por V38), conectando como `app_user` en Staging:
--
--     SELECT set_config('app.tenant_id', '', false);   -- lo que hace el pool
--     SELECT count(*) FROM tenant_order_counters;      -- 0
--     BEGIN;
--       SELECT set_config('app.tenant_id', 'qa-alfa', true);
--       SELECT count(*) FROM tenant_order_counters;    -- 1
--     COMMIT;
--     SELECT count(*) FROM tenant_order_counters;      -- 0 otra vez
--
-- ── LO QUE VA EN ESTE MISMO CAMBIO, Y POR QUÉ NO PUEDE IR APARTE ──────
--
-- 🔴 `/admin/tenants/{id}/modules` escribe `tenant_modules` SIN negocio en
--    sesión. `SuperAdminController:100` → `AuthService.setModuleOverrides` →
--    `AuthRepository.upsertOverride:122-127`. La ruta `/admin/**` está exenta
--    del `TenantContextFilter` (:51), y ese camino nunca hizo `set_config` —a
--    diferencia de `AltaDeNegocioService:149` y `SuperAdminService:252`.
--
--    Cerrar la política sin arreglar eso primero deja el endpoint escribiendo
--    en cero filas y devolviendo 200: el KAM regala un módulo, la pantalla
--    confirma el cambio, y el negocio no lo tiene. Es exactamente el modo de
--    fallo que estas migraciones vienen a eliminar, fabricado de nuevo.
--
--    El arreglo va en `AuthService.setModuleOverrides` y `getModuleConfig`, en
--    el mismo commit que este archivo.
--
-- ── El riesgo concreto, y cómo se comprobó ────────────────────────────
--
-- Si algún camino de login no fija el negocio, la política cerrada NO da error:
-- devuelve CERO overrides. El login responde 200 y el JWT sale con los módulos
-- del plan a secas. Un negocio con módulos regalados los pierde en cada login y
-- nada lo reporta.
--
-- En Staging `shark-burger` tiene SEIS overrides (cartera, compras, empleados,
-- insumos, nomina, valeras). La comprobación de esta migración no es "el login
-- responde 200" —eso respondería igual estando roto— sino **que esos seis
-- módulos siguen en la respuesta del login**. Probarlo con un negocio recién
-- creado, que no tiene overrides, no distinguiría nada.
--
-- ── VERIFICACIÓN PREVIA OBLIGATORIA (solo lectura) ────────────────────
--
--     SELECT tenant_id, module, enabled FROM tenant_modules ORDER BY 1, 2;
--     SELECT count(*) FROM tenant_modules m
--      WHERE NOT EXISTS (SELECT 1 FROM tenants t WHERE t.id = m.tenant_id);
--
-- Un override atribuido al negocio equivocado deja de aplicarse al cerrar la
-- política, y el negocio pierde el módulo. Corregir ANTES.
--
-- ── ROLLBACK ──────────────────────────────────────────────────────────
--
-- Bloque DOWN al final, comentado. Inmediato y sin pérdida de datos.
--
-- ── NUMERACIÓN ────────────────────────────────────────────────────────
--
-- La última es V39 (`users` y `password_resets`). V19 quedó sin usar a
-- propósito (V21:21-23).
-- =====================================================================

SET lock_timeout = '3s';

DO $aislar$
DECLARE
    pol TEXT;
BEGIN
    -- Por catálogo, no por nombre construido. La política de esta tabla SÍ se
    -- llama `app_rw_tenant_modules`, pero el bucle de V38 aprendió que fiarse
    -- del nombre deja políticas abiertas vivas al lado de la nueva, y Postgres
    -- las combina con OR.
    FOR pol IN
        SELECT policyname FROM pg_policies
        WHERE schemaname = 'public' AND tablename = 'tenant_modules'
    LOOP
        EXECUTE format('DROP POLICY %I ON public.tenant_modules', pol);
    END LOOP;

    ALTER TABLE public.tenant_modules ENABLE ROW LEVEL SECURITY;
    ALTER TABLE public.tenant_modules FORCE ROW LEVEL SECURITY;

    -- Sin cláusula TO, igual que V1/V33/V38/V39.
    CREATE POLICY tenant_isolation_tenant_modules ON public.tenant_modules
        USING (tenant_id = current_setting('app.tenant_id', true))
        WITH CHECK (tenant_id = current_setting('app.tenant_id', true));
END $aislar$;

-- Índice por tenant: NO hace falta crear uno. La PRIMARY KEY es
-- (tenant_id, module) (V7:17), o sea que `tenant_id` ya es la columna líder de
-- un btree y la política lo aprovecha. Crear `idx_tenant_modules_tenant` sería
-- un índice muerto — mismo criterio que en V38 con `tenant_order_counters`.

-- Permisos reafirmados tal como estaban (V7:20), sin ampliar.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.tenant_modules TO app_user;

-- ── Verificación: la migración FALLA si algo quedó a medias ───────────
DO $verificar$
DECLARE
    n_pol      INT;
    n_abiertas INT;
    forzada    BOOLEAN;
    lider      TEXT;
BEGIN
    SELECT count(*) INTO n_pol FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'tenant_modules';
    IF n_pol <> 1 THEN
        RAISE EXCEPTION 'V40: tenant_modules quedo con % politicas; se esperaba 1.', n_pol;
    END IF;

    SELECT count(*) INTO n_abiertas FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'tenant_modules'
      AND (coalesce(qual, '') = 'true' OR coalesce(with_check, '') = 'true');
    IF n_abiertas > 0 THEN
        RAISE EXCEPTION 'V40: tenant_modules conserva una politica abierta.';
    END IF;

    SELECT c.relforcerowsecurity INTO forzada
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relname = 'tenant_modules';
    IF NOT coalesce(forzada, false) THEN
        RAISE EXCEPTION 'V40: tenant_modules no tiene FORCE ROW LEVEL SECURITY.';
    END IF;

    -- Y que el indice que se decidio NO crear siga siendo innecesario: si algun
    -- dia se cambiara la PK, esta comprobacion avisaria en vez de dejar la tabla
    -- escaneandose entera en cada login.
    SELECT a.attname INTO lider
    FROM pg_index i
    JOIN pg_class c ON c.oid = i.indrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = i.indkey[0]
    WHERE n.nspname = 'public' AND c.relname = 'tenant_modules' AND i.indisprimary;
    IF lider IS DISTINCT FROM 'tenant_id' THEN
        RAISE EXCEPTION
            'V40: la PK de tenant_modules ya no empieza por tenant_id (es %). '
            'Con la politica filtrando por esa columna hace falta un indice '
            'propio o cada login escanea la tabla entera.', lider;
    END IF;
END $verificar$;

-- ── EL CIERRE ────────────────────────────────────────────────────────
-- Comprobacion final del trabajo entero (V33 + V38 + V39 + V40): ninguna tabla
-- con `tenant_id` puede quedar con politica abierta.
--
-- Las cuatro tablas GLOBALES por diseño no tienen `tenant_id` y por eso no
-- entran: `tenants` ES el catalogo de negocios, y `super_admins`, `plans` y
-- `plan_modules` son de plataforma. Si alguna llegara a tener `tenant_id`, esta
-- comprobacion la exigiria aislada, que es lo correcto.
DO $cierre$
DECLARE
    fila   RECORD;
    sueltas TEXT := '';
BEGIN
    FOR fila IN
        SELECT c.relname AS tabla
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_attribute a ON a.attrelid = c.oid
        WHERE n.nspname = 'public' AND c.relkind = 'r'
          AND a.attname = 'tenant_id' AND a.attnum > 0 AND NOT a.attisdropped
          AND (
            -- sin RLS, o sin FORCE, o con alguna politica abierta
            NOT c.relrowsecurity
            OR NOT c.relforcerowsecurity
            OR EXISTS (SELECT 1 FROM pg_policies p
                       WHERE p.schemaname = 'public' AND p.tablename = c.relname
                         AND (coalesce(p.qual, '') = 'true'
                              OR coalesce(p.with_check, '') = 'true'))
            -- o sin ninguna politica: RLS activo sin politicas niega todo, lo
            -- cual "aisla" pero rompe la tabla; tambien hay que verlo.
            OR NOT EXISTS (SELECT 1 FROM pg_policies p
                           WHERE p.schemaname = 'public' AND p.tablename = c.relname)
          )
        ORDER BY 1
    LOOP
        sueltas := sueltas || fila.tabla || ' ';
    END LOOP;

    IF sueltas <> '' THEN
        RAISE EXCEPTION
            'V40: quedan tablas con tenant_id sin aislamiento real: %. '
            'Este era el criterio de cierre del trabajo de aislamiento.', sueltas;
    END IF;

    RAISE NOTICE 'V40: ninguna tabla con tenant_id queda sin aislamiento real.';
END $cierre$;

-- Lo que este archivo NO puede comprobar por si solo:
--   · que `app_user` ve solo lo suyo (esta migracion corre como el dueno, que
--     tiene BYPASSRLS: desde aqui todo se ve siempre);
--   · que el login de un negocio CON overrides los sigue devolviendo. Eso solo
--     lo dice una llamada a la API real, y es la comprobacion que importa.

-- =====================================================================
-- DOWN — rollback explicito. Reabre el agujero.
-- =====================================================================
--
-- DO $revertir$
-- DECLARE
--     pol TEXT;
-- BEGIN
--     FOR pol IN
--         SELECT policyname FROM pg_policies
--         WHERE schemaname = 'public' AND tablename = 'tenant_modules'
--     LOOP
--         EXECUTE format('DROP POLICY %I ON public.tenant_modules', pol);
--     END LOOP;
--     ALTER TABLE public.tenant_modules NO FORCE ROW LEVEL SECURITY;
--     CREATE POLICY app_rw_tenant_modules ON public.tenant_modules
--         FOR ALL TO app_user USING (true) WITH CHECK (true);
-- END $revertir$;
