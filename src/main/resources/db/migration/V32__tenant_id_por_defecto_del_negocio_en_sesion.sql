-- =====================================================================
-- V32 — EL `tenant_id` SALE DEL NEGOCIO EN SESIÓN, NO DE UNA CONSTANTE.
--
-- Dos defectos de multi-tenencia que se destaparon al fallar la creación de
-- meseros desde el panel (`500` con "new row violates row-level security
-- policy for table waiters").
--
-- ── Defecto 1 · 28 tablas sin valor por defecto ───────────────────────────
--
-- `ms-core-app` es tenant-aware para LEER —`TenantAwareDataSource` fija
-- `app.tenant_id` en cada conexión— pero NINGUNA de sus 28 entidades JPA mapea
-- la columna `tenant_id`. Así que sus INSERT la omiten:
--
--     insert into waiters (active, commission_percentage, daily_sale_goal, name)
--
-- Sin la columna y sin default, `tenant_id` queda NULL, el `WITH CHECK` de la
-- política da falso y la fila se rechaza. Leer funcionaba, escribir nunca.
--
-- ── Defecto 2 · 17 tablas con 'shark-burger' QUEMADO como default ─────────
--
-- Peor que el anterior. `expenses`, `supplies`, `employees`, `valeras`,
-- `payrolls`… tenían `DEFAULT 'shark-burger'`: el nombre de UN cliente dentro
-- del esquema. Para ese cliente funcionaba de casualidad; para cualquier otro
-- negocio, el INSERT del panel se rechazaría (su tenant no coincide con el
-- default) — o sea que **el panel estaba roto para todo cliente nuevo**.
--
-- ── El arreglo ────────────────────────────────────────────────────────────
--
-- El default pasa a ser el negocio de la sesión. Con eso:
--
--   · Un INSERT que omite la columna recibe el tenant correcto.
--   · Deja de haber un nombre de cliente en el esquema.
--   · Es A PRUEBA DE FALLO: sin `app.tenant_id` fijado, el default es NULL y
--     RLS rechaza la fila. Nunca se escribe "a ciegas" en el tenant de otro.
--
-- ⚠️ EL `nullif` NO ES ADORNO. En una conexión sin tocar, `current_setting`
-- devuelve **cadena vacía**, no NULL. Sin el `nullif`, la fila quedaría con
-- `tenant_id = ''` y la política la ACEPTARÍA —porque `'' = ''` es cierto—,
-- creando filas huérfanas que no son de ningún negocio. Verificado contra la
-- base de Producción antes de dejarlo así.
--
-- Un valor explícito siempre gana sobre el default, así que `ms-order-product`
-- —que sí lo pone desde Java con `TenantEntityListener`— no cambia en nada.
--
-- REVERSIBLE. Para volver atrás en una tabla:
--   ALTER TABLE <tabla> ALTER COLUMN tenant_id DROP DEFAULT;
-- =====================================================================

-- Con el local operando, esto NO puede quedarse esperando un lock: es un cambio
-- de catálogo (microsegundos), pero si una tabla está ocupada se prefiere
-- fallar la migración antes que frenar una venta.
SET lock_timeout = '3s';

DO $$
DECLARE
    t TEXT;
BEGIN
    FOR t IN
        SELECT c.relname
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = 'public'
        JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'tenant_id' AND a.attnum > 0
        WHERE c.relkind = 'r' AND c.relrowsecurity
        ORDER BY c.relname
    LOOP
        -- Se usa %L y no comillas a mano: escribir la cadena vacía escapada dentro
        -- de un format() anidado son OCHO comillas seguidas, y ocho producen '''',
        -- que es una cadena con un apóstrofe adentro — no la vacía. Ya pasó.
        EXECUTE format(
            'ALTER TABLE public.%I ALTER COLUMN tenant_id SET DEFAULT nullif(current_setting(%L, true), %L)',
            t, 'app.tenant_id', '');
    END LOOP;
END $$;

-- Verificación: ninguna tabla con RLS debería quedar sin default ni con un
-- nombre de negocio escrito a mano.
DO $$
DECLARE
    pendientes INT;
BEGIN
    SELECT count(*) INTO pendientes
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = 'public'
    JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'tenant_id' AND a.attnum > 0
    LEFT JOIN pg_attrdef d ON d.adrelid = c.oid AND d.adnum = a.attnum
    WHERE c.relkind = 'r' AND c.relrowsecurity
      AND (d.adbin IS NULL
           OR pg_get_expr(d.adbin, d.adrelid) NOT LIKE '%app.tenant_id%'
           OR pg_get_expr(d.adbin, d.adrelid) NOT LIKE '%NULLIF%');

    IF pendientes > 0 THEN
        RAISE EXCEPTION 'Quedaron % tablas con RLS cuyo tenant_id no sale de la sesión', pendientes;
    END IF;
END $$;

-- La propiedad de fallo cerrado —sin negocio en sesión la fila se rechaza— NO
-- se puede comprobar acá: Flyway corre como dueño de las tablas y los roles con
-- BYPASSRLS se saltan la política, así que un INSERT de prueba pasaría por el
-- motivo equivocado y no probaría nada. Se comprueba conectándose como
-- `app_user`, en CloudTenantIsolationTest#sinNegocioEnSesionElInsertSeRechaza.
