-- =====================================================================
-- V33 — LAS 17 TABLAS DE ADMINISTRACIÓN PASAN A AISLAR DE VERDAD POR NEGOCIO.
--
-- ⚠️ NO APLICAR SIN LEER PRIMERO `PRE-REQUISITOS-RLS.md` (raíz del repo).
--    Esta migración es correcta y es la que falta, pero aplicarla antes de que
--    ese documento esté resuelto deja al panel de administración sin ver sus
--    propios datos. Los pre-requisitos están ahí, con archivo y línea.
--
-- ── El defecto ────────────────────────────────────────────────────────
--
-- V28 creó las 17 tablas del módulo de administración con RLS activada y esta
-- política (V28:160-162):
--
--     CREATE POLICY app_rw_<tabla> ON <tabla> FOR ALL TO app_user
--         USING (true) WITH CHECK (true);
--
-- `USING (true)` significa: toda fila es visible para `app_user`, sea del
-- negocio que sea. No aísla nada. Y `ms-core-app`, que es quien lee estas
-- tablas, tampoco filtra por su cuenta:
--
--   · ninguna de sus 25 entidades filtra por tenant — `EntidadDeNegocio` mapea
--     `tenant_id` con insertable=false/updatable=false, o sea solo para leer de
--     quién es la fila, nunca como criterio;
--   · no hay un solo @Where ni @Filter de Hibernate en el servicio;
--   · el acceso típico es un findAll() pelado
--     (EmployeeRepositoryImpl.java:19-20).
--
-- Resultado: un negocio que entra al panel ve los empleados, la nómina, los
-- gastos, la cartera y el inventario de TODOS los negocios de la plataforma.
-- Datos afectados: documento de identidad y teléfono de clientes finales
-- (`valeras`, `accounts_receivable`), salarios (`employees`, `payrolls`),
-- y la operación completa (`supplies`, `expenses`, `suppliers`).
--
-- Hoy no ha estallado porque en la práctica opera un solo cliente en ese
-- módulo. El defecto se activa con el SEGUNDO cliente, no con un ataque.
--
-- ── Qué se arregló antes y qué quedó ──────────────────────────────────
--
-- V32 detectó la mitad visible del problema —el `DEFAULT 'shark-burger'` que
-- V28 había dejado en estas mismas 17 tablas— y lo corrigió: el default pasó a
-- salir del negocio en sesión. Pero V32 cambió el DEFAULT, no la POLÍTICA. Lo
-- que quedó después de aquel arreglo es un panel que ESCRIBE en el negocio
-- correcto y LEE el de todos — que es peor que estar roto, porque parece
-- funcionar.
--
-- ── El arreglo ────────────────────────────────────────────────────────
--
-- Exactamente el patrón de V1:48-65, que ya está probado en 22 tablas y que
-- estas 17 nunca recibieron:
--
--   · ENABLE + FORCE ROW LEVEL SECURITY
--   · política por `current_setting('app.tenant_id', true)`, en USING y en
--     WITH CHECK
--   · GRANT explícito a `app_user` (lección de V20/V22: la migración corre como
--     owner y pasa verde aunque falten los permisos; la aplicación conecta con
--     otro rol y recibe "permission denied")
--
-- Sobre FORCE: sin él la política no aplica al dueño de la tabla, y en esta
-- base Flyway corre como dueño. Con él, cualquier migración FUTURA que toque
-- estas tablas tendrá que fijar `app.tenant_id` primero — exactamente lo que ya
-- tuvo que hacer V31:66 (`PERFORM set_config('app.tenant_id', t, true)`).
-- Es una molestia conocida y es el precio correcto.
--
-- Sobre el default seguro: sin negocio en sesión, `current_setting(...,true)`
-- devuelve NULL, y `tenant_id = NULL` es NULL (no cierto), así que no se ve
-- ninguna fila. Falla cerrado, igual que el resto del esquema.
--
-- ── VERIFICACIÓN PREVIA OBLIGATORIA (solo lectura) ────────────────────
--
-- Antes de aplicar, correr esto contra Producción y comprobar que no aparece
-- ningún `tenant_id` inesperado. Si alguna fila quedó con el negocio equivocado
-- —por ejemplo escrita con el viejo default 'shark-burger' cuando pertenecía a
-- otro—, al cerrar la política ese negocio dejará de verla y parecerá que se
-- perdieron datos. Corregir la atribución ANTES, nunca después:
--
--     SELECT 'supplies' AS tabla, tenant_id, count(*) FROM supplies GROUP BY 2
--     UNION ALL SELECT 'employees', tenant_id, count(*) FROM employees GROUP BY 2
--     UNION ALL SELECT 'payrolls', tenant_id, count(*) FROM payrolls GROUP BY 2
--     UNION ALL SELECT 'expenses', tenant_id, count(*) FROM expenses GROUP BY 2
--     UNION ALL SELECT 'valeras', tenant_id, count(*) FROM valeras GROUP BY 2
--     UNION ALL SELECT 'accounts_receivable', tenant_id, count(*)
--                 FROM accounts_receivable GROUP BY 2
--     ORDER BY 1, 2;
--
-- (El listado completo de las 17 está abajo; estas seis son las que tienen
-- datos personales o dinero.)
--
-- ── ROLLBACK ──────────────────────────────────────────────────────────
--
-- Inmediato y sin pérdida de datos: devuelve la política abierta y quita FORCE.
-- El bloque DOWN está al final del archivo, comentado. Volver atrás REABRE el
-- agujero, así que solo se hace si el panel se queda ciego y hay que restituir
-- el servicio mientras se corrige la causa.
--
-- ── NUMERACIÓN ────────────────────────────────────────────────────────
--
-- La última aplicada es V32. V19 quedó sin usar a propósito (ver V21:21-23),
-- así que la siguiente libre es V33.
-- =====================================================================

-- Con el local operando esto no puede quedarse esperando un lock: son cambios
-- de catálogo (microsegundos), pero si una tabla está ocupada se prefiere
-- fallar la migración antes que frenar una venta. Mismo criterio que V32:52.
SET lock_timeout = '3s';

DO $aislar$
DECLARE
    t TEXT;
    tablas TEXT[] := ARRAY[
        'supply_categories', 'supplies', 'supply_consumptions',
        'weekly_inventory_counts', 'suppliers', 'supplier_requests',
        'supplier_request_items', 'shopping_items', 'employees', 'payrolls',
        'expense_categories', 'expenses', 'valeras', 'meal_preparations',
        'accounts_receivable', 'debt_transactions', 'qr_payments'
    ];
BEGIN
    FOREACH t IN ARRAY tablas LOOP

        -- Fuera la política abierta que dejó V28.
        EXECUTE format('DROP POLICY IF EXISTS app_rw_%I ON public.%I', t, t);

        -- Y fuera también un posible resto de una ejecución previa de ESTA
        -- migración, para que sea idempotente.
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation_%I ON public.%I', t, t);

        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', t);

        EXECUTE format(
            'CREATE POLICY tenant_isolation_%I ON public.%I '
            'USING (tenant_id = current_setting(''app.tenant_id'', true)) '
            'WITH CHECK (tenant_id = current_setting(''app.tenant_id'', true))', t, t);

        -- Índice por tenant: con la política filtrando por esta columna en cada
        -- consulta, sin él todas pasan a escanear la tabla entera. Las 22 tablas
        -- del patrón original lo tienen desde V1/V2; estas 17 nunca lo
        -- recibieron porque nunca filtraron por tenant.
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON public.%I (tenant_id)',
                       'idx_' || t || '_tenant', t);

        -- Lección de V20/V22: el permiso es una capa distinta de RLS y se evalúa
        -- primero. Se reafirma aunque V28 ya lo diera.
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.%I TO app_user', t);
    END LOOP;
END $aislar$;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_user;

-- ---------------------------------------------------------------------
-- Verificación: que no quede ninguna de las 17 con una política permisiva
-- que no mire `app.tenant_id`. Si algo salió mal, la migración falla aquí y
-- Flyway la marca en rojo, en vez de dejar el agujero medio cerrado.
-- ---------------------------------------------------------------------
DO $verificar$
DECLARE
    abiertas INT;
    sin_force INT;
BEGIN
    SELECT count(*) INTO abiertas
    FROM pg_policies p
    WHERE p.schemaname = 'public'
      AND p.tablename = ANY (ARRAY[
            'supply_categories','supplies','supply_consumptions',
            'weekly_inventory_counts','suppliers','supplier_requests',
            'supplier_request_items','shopping_items','employees','payrolls',
            'expense_categories','expenses','valeras','meal_preparations',
            'accounts_receivable','debt_transactions','qr_payments'])
      AND (p.qual IS NULL OR p.qual NOT LIKE '%app.tenant_id%');

    IF abiertas > 0 THEN
        RAISE EXCEPTION
            'Quedaron % politicas que no filtran por app.tenant_id en las tablas de administracion',
            abiertas;
    END IF;

    SELECT count(*) INTO sin_force
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = 'public'
    WHERE c.relkind = 'r'
      AND c.relname = ANY (ARRAY[
            'supply_categories','supplies','supply_consumptions',
            'weekly_inventory_counts','suppliers','supplier_requests',
            'supplier_request_items','shopping_items','employees','payrolls',
            'expense_categories','expenses','valeras','meal_preparations',
            'accounts_receivable','debt_transactions','qr_payments'])
      AND NOT (c.relrowsecurity AND c.relforcerowsecurity);

    IF sin_force > 0 THEN
        RAISE EXCEPTION 'Quedaron % tablas de administracion sin ENABLE+FORCE de RLS', sin_force;
    END IF;
END $verificar$;

-- La propiedad que de verdad importa —que un negocio NO vea las filas de
-- otro— no se puede comprobar aquí: Flyway corre como dueño de las tablas y
-- los roles con BYPASSRLS se saltan la política, así que un SELECT de prueba
-- pasaría por el motivo equivocado y no probaría nada. Se comprueba
-- conectándose como `app_user`, igual que
-- CloudTenantIsolationTest#sinNegocioEnSesionElInsertSeRechaza. Mismo
-- razonamiento que V32:96-100.


-- =====================================================================
-- DOWN — rollback explícito.
--
-- Devuelve el estado exacto que dejó V28: política abierta, sin FORCE. Los
-- índices por tenant se dejan: no afectan a la visibilidad y quitarlos solo
-- haría más lentas las consultas.
--
-- ⚠️ Ejecutar esto REABRE el agujero de aislamiento entre negocios. Solo tiene
-- sentido como medida de urgencia si el panel se queda sin ver sus propios
-- datos y hay que restituir el servicio mientras se corrige la causa (que
-- estará, casi con seguridad, en la lista de PRE-REQUISITOS-RLS.md).
--
-- DO $revertir$
-- DECLARE
--     t TEXT;
--     tablas TEXT[] := ARRAY[
--         'supply_categories', 'supplies', 'supply_consumptions',
--         'weekly_inventory_counts', 'suppliers', 'supplier_requests',
--         'supplier_request_items', 'shopping_items', 'employees', 'payrolls',
--         'expense_categories', 'expenses', 'valeras', 'meal_preparations',
--         'accounts_receivable', 'debt_transactions', 'qr_payments'
--     ];
-- BEGIN
--     FOREACH t IN ARRAY tablas LOOP
--         EXECUTE format('DROP POLICY IF EXISTS tenant_isolation_%I ON public.%I', t, t);
--         EXECUTE format('ALTER TABLE public.%I NO FORCE ROW LEVEL SECURITY', t);
--         EXECUTE format('DROP POLICY IF EXISTS app_rw_%I ON public.%I', t, t);
--         EXECUTE format('CREATE POLICY app_rw_%I ON public.%I FOR ALL TO app_user '
--                        || 'USING (true) WITH CHECK (true)', t, t);
--         EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.%I TO app_user', t);
--     END LOOP;
-- END $revertir$;
-- =====================================================================
