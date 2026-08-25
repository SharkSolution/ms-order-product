-- =====================================================================
-- V38 — LOS CONTADORES DE NUMERACIÓN Y LAS DOS BITÁCORAS PASAN A AISLAR
--       DE VERDAD POR NEGOCIO.
--
-- Tablas: tenant_order_counters, order_counter_audit, site_mode_audit.
--
-- ⚠️ `tenant_modules` NO ENTRA EN ESTA MIGRACIÓN. Es la cuarta tabla con
--    política abierta y se dejó fuera a propósito: se lee DURANTE EL LOGIN,
--    antes de que exista negocio en sesión. Ver §"La cuarta tabla" abajo.
--
-- ── El defecto ────────────────────────────────────────────────────────
--
-- V5:25-26 y V28:216-217/304-307 crearon estas tres tablas con RLS activada y
-- la política de siempre:
--
--     CREATE POLICY app_rw_<algo> ON <tabla> FOR ALL TO app_user
--         USING (true) WITH CHECK (true);
--
-- `USING (true)` no aísla nada. Medido en Staging el 2026-08-25, conectando
-- como `app_user` (el rol de la aplicación, sin BYPASSRLS) tras sembrar filas
-- de cuatro negocios:
--
--     SELECT set_config('app.tenant_id', 'qa-alfa', false);
--
--     tabla                  | visibles | ajenas_visibles
--     -----------------------+----------+----------------
--     tenant_order_counters  |        4 |               3
--     order_counter_audit    |        2 |               1
--     site_mode_audit        |        2 |               1
--
-- Y con la cadena vacía —que es lo que `TenantAwareDataSource.java:54` fija
-- cuando no hay negocio en contexto— se siguen viendo las 4, las 2 y las 2.
-- Falla ABIERTO, que es justo al revés de lo que hace el resto del esquema.
--
-- Qué queda expuesto:
--
--   · `tenant_order_counters` es el consecutivo FISCAL de cada sede. Leerlo
--     ajeno revela el volumen de ventas de otro negocio; escribirlo ajeno
--     mueve su numeración fiscal.
--   · `order_counter_audit` y `site_mode_audit` son las bitácoras de las dos
--     acciones más delicadas del sistema —mover un consecutivo a mano y
--     cambiar el modo de una sede—. Una bitácora que otro negocio puede leer
--     y escribir no es una bitácora.
--
-- Como en V33: hoy no ha estallado porque opera un solo cliente. El defecto se
-- activa con el SEGUNDO, no con un ataque.
--
-- ── La cuarta tabla: por qué `tenant_modules` se queda fuera ──────────
--
-- `AuthService.login:103` llama a `effectiveModulesFor`, que en
-- `AuthRepository.java:116` hace `SELECT module, enabled FROM tenant_modules
-- WHERE tenant_id = ?`. `/auth/**` está exento del filtro de negocio
-- (`TenantContextFilter.java:50`), así que esa consulta sale con
-- `app.tenant_id = ''`. La propia cabecera de V7:8-10 ya lo decía: "se lee en
-- el login (sin tenant en contexto)".
--
-- Cerrar su política aquí NO daría un error: daría CERO overrides. El login
-- seguiría respondiendo 200 y el JWT saldría con los módulos del plan a secas.
-- En Staging hay 6 overrides de `shark-burger`; los seis desaparecerían de cada
-- login sin que nada lo reporte. Es exactamente el modo de fallo que esta
-- migración existe para eliminar, así que forzarla aquí sería cambiar un
-- agujero por otro.
--
-- Va a la tanda de `users` y `password_resets`, que tienen el mismo problema y
-- se resuelven con el mismo patrón: una función `SECURITY DEFINER` acotada que
-- lee lo justo, en vez de una política que el login no puede satisfacer.
--
-- ── El arreglo ────────────────────────────────────────────────────────
--
-- El patrón de V33, que a su vez copia V1:48-65. Tres diferencias deliberadas
-- respecto al bucle de V33, y las tres importan:
--
-- 1. LAS POLÍTICAS VIEJAS SE BORRAN POR CATÁLOGO, NO POR NOMBRE ADIVINADO.
--    V33 hacía `DROP POLICY IF EXISTS app_rw_%I` armando el nombre a partir de
--    la tabla. Aquí eso NO funcionaría: la política de `tenant_order_counters`
--    se llama `app_rw_order_counters` (V5:25), sin el prefijo `tenant_`. El
--    `DROP ... IF EXISTS` no encontraría nada, no daría error, y la política
--    abierta SOBREVIVIRÍA junto a la nueva. Postgres combina las políticas
--    permisivas con OR: `USING (true) OR tenant_id = ...` es `true`. La
--    migración habría pasado verde, la política nueva existiría, y el
--    aislamiento seguiría sin existir. Por eso se recorre `pg_policies` y se
--    borra lo que haya, se llame como se llame.
--
-- 2. LOS PERMISOS NO SE AMPLÍAN. V33 hacía `GRANT SELECT, INSERT, UPDATE,
--    DELETE` a ciegas sobre las 17. Estas tres no tienen esos permisos y no
--    deben tenerlos: las dos bitácoras son SELECT+INSERT (V28:212, V28:302) y
--    `tenant_order_counters` es SELECT+INSERT+UPDATE sin DELETE (V5:19). Una
--    bitácora sobre la que se puede hacer UPDATE o DELETE deja de servir para
--    lo único que sirve. Se reafirma lo que ya hay, ni un permiso más.
--
-- 3. EL ÍNDICE SOLO SE CREA DONDE FALTA. `tenant_order_counters` ya tiene
--    `tenant_id` como columna líder de su PK (V28:186-187), así que un índice
--    aparte sería muerto. Las dos bitácoras solo tienen `PRIMARY KEY (id)`
--    (V28:202, V28:293) y sí lo necesitan: con la política filtrando por
--    `tenant_id` en cada consulta, sin índice todas escanean la tabla entera.
--
-- ── Lo que esta migración NO arregla, y conviene saberlo ───────────────
--
-- El trigger `set_order_id_order()` (V28:226) es `SECURITY DEFINER`, así que
-- corre como el dueño de la tabla. En esta base el dueño es `postgres`, y
-- `postgres` tiene `BYPASSRLS = true` (verificado en `pg_roles`, Staging
-- 2026-08-25). Un rol con BYPASSRLS salta RLS aunque la tabla esté en FORCE.
--
-- Consecuencia doble, y las dos hay que decirlas:
--   · BUENA: el camino que atiende el 99% del tráfico —el UPSERT del contador
--     en cada venta— no puede romperse con esta migración. No hay riesgo de
--     dejar de vender.
--   · MALA: ese mismo camino tampoco queda aislado por RLS. Lo que lo mantiene
--     correcto es que el trigger filtra por `NEW.tenant_id`, no la política.
--     V38 cierra el acceso DIRECTO de `app_user` (que es por donde entra
--     `AltaDeNegocioService.java:160` y por donde entraría un segundo cliente
--     leyendo), no el del trigger.
--
-- ── VERIFICACIÓN PREVIA OBLIGATORIA (solo lectura) ────────────────────
--
-- Antes de aplicar, contra el entorno destino: si alguna fila quedó atribuida
-- al negocio equivocado, al cerrar la política su dueño real dejará de verla y
-- parecerá que se perdieron datos. Corregir la atribución ANTES, nunca después.
--
--     SELECT 'tenant_order_counters' AS tabla, tenant_id, count(*)
--       FROM tenant_order_counters GROUP BY 1, 2
--     UNION ALL SELECT 'order_counter_audit', tenant_id, count(*)
--       FROM order_counter_audit GROUP BY 1, 2
--     UNION ALL SELECT 'site_mode_audit', tenant_id, count(*)
--       FROM site_mode_audit GROUP BY 1, 2
--     ORDER BY 1, 2;
--
-- Y comprobar que no hay filas huérfanas, que tras FORCE serían invisibles
-- para todo el mundo:
--
--     SELECT count(*) FROM tenant_order_counters c
--      WHERE NOT EXISTS (SELECT 1 FROM tenants t WHERE t.id = c.tenant_id);
--
-- ── EFECTO EN PROCEDIMIENTOS MANUALES ─────────────────────────────────
--
-- `docs/arquitectura/NUMERACION-POR-SEDE.md` §5 mueve el consecutivo a mano
-- con un `INSERT INTO order_counter_audit ... SELECT` + `UPDATE
-- tenant_order_counters`, ejecutado desde consola. Ese procedimiento corre hoy
-- como `postgres`, que tiene BYPASSRLS, así que seguirá funcionando — pero si
-- algún día se ejecuta con `app_user`, insertará CERO filas sin dar error.
-- El documento se actualiza en este mismo cambio para que fije
-- `app.tenant_id` dentro de la transacción, como ya hace V31:66.
--
-- ── ROLLBACK ──────────────────────────────────────────────────────────
--
-- Inmediato y sin pérdida de datos: devuelve la política abierta y quita FORCE.
-- El bloque DOWN está al final del archivo, comentado. Volver atrás REABRE el
-- agujero; solo se hace si algo se queda ciego y hay que restituir el servicio
-- mientras se corrige la causa.
--
-- ── NUMERACIÓN ────────────────────────────────────────────────────────
--
-- La última aplicada es V37 (Producción y Staging, 2026-08-24). V19 quedó sin
-- usar a propósito (ver V21:21-23), así que la siguiente libre es V38.
--
-- ⚠️ `discovery/DESPLIEGUE-FASE-2.md:290` también llama V38 a otro trabajo
--    distinto (poner NOT NULL a las columnas del modelo temporal). Ese trabajo
--    tendrá que tomar V39; este número lo reservaban ya
--    `discovery/RETOMAR-AQUI.md:76` y `discovery/RUNBOOK_DESPLIEGUE.md:75`.
-- =====================================================================

-- Con el local operando esto no puede quedarse esperando un lock: son cambios
-- de catálogo (microsegundos), pero si una tabla está ocupada se prefiere
-- fallar la migración antes que frenar una venta. Mismo criterio que V32:52
-- y V33:104.
SET lock_timeout = '3s';

DO $aislar$
DECLARE
    t   TEXT;
    pol TEXT;
    tablas TEXT[] := ARRAY[
        'tenant_order_counters', 'order_counter_audit', 'site_mode_audit'
    ];
BEGIN
    FOREACH t IN ARRAY tablas LOOP

        -- Fuera TODA política existente, por catálogo. Ver §El arreglo, punto 1:
        -- borrar por nombre adivinado dejaría viva `app_rw_order_counters` y el
        -- OR de políticas permisivas anularía esta migración en silencio.
        FOR pol IN
            SELECT policyname FROM pg_policies
            WHERE schemaname = 'public' AND tablename = t
        LOOP
            EXECUTE format('DROP POLICY %I ON public.%I', pol, t);
        END LOOP;

        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', t);

        -- Sin cláusula TO, igual que V1 y V33: así la política alcanza también
        -- al dueño de la tabla, que es lo que hace que FORCE signifique algo.
        -- Sin negocio en sesión, current_setting(...,true) devuelve NULL y
        -- `tenant_id = NULL` es NULL, no cierto: no se ve ninguna fila.
        EXECUTE format(
            'CREATE POLICY tenant_isolation_%I ON public.%I '
            'USING (tenant_id = current_setting(''app.tenant_id'', true)) '
            'WITH CHECK (tenant_id = current_setting(''app.tenant_id'', true))', t, t);
    END LOOP;
END $aislar$;

-- Índice por tenant SOLO donde falta (§El arreglo, punto 3). Las dos bitácoras
-- nacieron con PRIMARY KEY (id) y nada más; `tenant_order_counters` ya tiene
-- tenant_id como columna líder de su PK y no lleva índice nuevo.
CREATE INDEX IF NOT EXISTS idx_order_counter_audit_tenant
    ON public.order_counter_audit (tenant_id);
CREATE INDEX IF NOT EXISTS idx_site_mode_audit_tenant
    ON public.site_mode_audit (tenant_id);

-- Permisos reafirmados EXACTAMENTE como estaban (§El arreglo, punto 2).
-- Lección de V20/V22: el permiso es una capa distinta de RLS y se evalúa
-- primero, así que se reafirma aunque V5/V28 ya lo dieran. Pero no se amplía:
-- las dos bitácoras siguen sin UPDATE ni DELETE.
GRANT SELECT, INSERT, UPDATE ON TABLE public.tenant_order_counters TO app_user;
GRANT SELECT, INSERT         ON TABLE public.order_counter_audit   TO app_user;
GRANT SELECT, INSERT         ON TABLE public.site_mode_audit       TO app_user;
GRANT USAGE, SELECT ON SEQUENCE public.order_counter_audit_id_seq TO app_user;
GRANT USAGE, SELECT ON SEQUENCE public.site_mode_audit_id_seq     TO app_user;

-- ── Verificación: la migración FALLA si algo quedó a medias ───────────
--
-- No comprueba "existe una política llamada tenant_isolation_*" —eso sería
-- cierto también en el escenario roto del punto 1, con la política abierta
-- viva al lado—. Comprueba que NO queda ninguna política permisiva abierta y
-- que FORCE está puesto. Es la diferencia entre verificar que se hizo algo y
-- verificar que el resultado es el que se buscaba.
DO $verificar$
DECLARE
    t      TEXT;
    tablas TEXT[] := ARRAY[
        'tenant_order_counters', 'order_counter_audit', 'site_mode_audit'
    ];
    n_pol      INT;
    n_abiertas INT;
    forzada    BOOLEAN;
BEGIN
    FOREACH t IN ARRAY tablas LOOP

        SELECT count(*) INTO n_pol
        FROM pg_policies WHERE schemaname = 'public' AND tablename = t;

        IF n_pol <> 1 THEN
            RAISE EXCEPTION
                'V38: %.% quedó con % políticas; se esperaba exactamente 1. '
                'Con más de una, Postgres las combina con OR y basta que una '
                'sea abierta para anular el aislamiento.', 'public', t, n_pol;
        END IF;

        -- Ninguna política puede tener `true` como condición.
        SELECT count(*) INTO n_abiertas
        FROM pg_policies
        WHERE schemaname = 'public' AND tablename = t
          AND (coalesce(qual, '') = 'true' OR coalesce(with_check, '') = 'true');

        IF n_abiertas > 0 THEN
            RAISE EXCEPTION 'V38: %.% conserva una política abierta (USING/CHECK true).',
                'public', t;
        END IF;

        SELECT c.relforcerowsecurity INTO forzada
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relname = t;

        IF NOT coalesce(forzada, false) THEN
            RAISE EXCEPTION
                'V38: %.% no tiene FORCE ROW LEVEL SECURITY; la política no '
                'aplicaría al dueño de la tabla.', 'public', t;
        END IF;
    END LOOP;

    -- Y que el índice que justifica el coste esté realmente ahí.
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public'
                   AND indexname = 'idx_order_counter_audit_tenant')
       OR NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public'
                   AND indexname = 'idx_site_mode_audit_tenant') THEN
        RAISE EXCEPTION 'V38: falta alguno de los índices por tenant de las bitácoras.';
    END IF;
END $verificar$;

-- Lo que este archivo NO puede comprobar por sí solo, y hay que medir aparte:
--   · que `app_user` (no el dueño) ve solo lo suyo. Esta migración corre como
--     `postgres`, que tiene BYPASSRLS: desde aquí TODO se ve, siempre. Una
--     comprobación de aislamiento escrita aquí dentro daría verde en los dos
--     escenarios, el bueno y el malo, y por eso no está escrita aquí.
--     Se mide con `app_user` y `set_config`, fuera de Flyway.
--   · que el login y la creación de órdenes siguen funcionando. Eso solo lo
--     dice una llamada a la API real.

-- =====================================================================
-- DOWN — rollback explícito. Reabre el agujero: solo para restituir servicio.
-- =====================================================================
--
-- DO $revertir$
-- DECLARE
--     t   TEXT;
--     pol TEXT;
--     tablas TEXT[] := ARRAY[
--         'tenant_order_counters', 'order_counter_audit', 'site_mode_audit'
--     ];
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
-- Los índices se dejan: no estorban y evitan un escaneo completo si se
-- vuelve a aplicar.
-- DROP INDEX IF EXISTS public.idx_order_counter_audit_tenant;
-- DROP INDEX IF EXISTS public.idx_site_mode_audit_tenant;
