-- =====================================================================
-- V35 — EL TERMINAL EXISTE EN EL MODELO.
--
-- ── El hueco ──────────────────────────────────────────────────────────
--
-- Hoy el concepto no existe en ninguna parte:
--
--   $ grep -rniE "terminal|device_id|caja_id|register_id" \
--         src/main/java src/main/resources/db/migration
--   (cero resultados)
--
-- Dos cajas del mismo local son indistinguibles en la base. Eso impide:
--   · saber qué caja hizo una venta (detección de robo por caja);
--   · un consecutivo por terminal;
--   · y, sobre todo, distinguir un evento nacido offline de uno nacido en línea
--     — que es lo que V36 viene a arreglar y para lo que necesita esta tabla.
--
-- ── Por qué ahora y no después ────────────────────────────────────────
--
-- Casi todo se puede retrofitear. La historia no. Una venta que ocurrió hoy sin
-- terminal registrado no se le puede atribuir mañana: el dato no existe. Doce
-- meses de serie sin terminal no sirven para analizar por caja.
--
-- ── Identidad: la genera el CLIENTE, no el servidor ───────────────────
--
-- `terminals.id` es un UUID que el POS genera en su primer arranque y persiste
-- localmente. NO es un identificador que asigne el servidor.
--
-- El motivo es el diferenciador del producto: el terminal tiene que poder vender
-- desde el primer arranque, sin conexión. Si el id lo asignara el servidor,
-- un local sin internet no podría abrir caja. Con un UUID local, el terminal
-- opera desde el segundo cero y se registra cuando consigue la primera
-- sincronización.
--
-- Y por eso mismo **el servidor NUNCA rechaza un terminal desconocido**: lo da
-- de alta. Rechazarlo convertiría un problema de registro en una venta perdida.
--
-- ── El problema del estado local perdido ──────────────────────────────
--
-- Si se reinstala el POS o se borra el almacenamiento del navegador, el UUID y
-- la secuencia desaparecen. Sin nada más, el servidor vería la secuencia
-- reiniciarse en 1 y no podría distinguir eso de un ataque de repetición.
--
-- La solución es que la identidad de un evento sea la TERNA
-- `(terminal_id, epoch, seq)` y no el par `(terminal_id, seq)`:
--
--   · `seq`   monotónico dentro de un epoch.
--   · `epoch` sube cuando el cliente detecta que perdió su estado.
--
-- Con eso, un reinicio de secuencia deja de ser un hueco inexplicable y pasa a
-- ser un hecho registrado: "este terminal empezó una vida nueva el día X".
--
-- Dos casos distintos, y conviene no confundirlos:
--
--   a) Se pierde `seq` pero SOBREVIVE el UUID → el cliente sube `epoch` y
--      reinicia `seq`. La continuidad del terminal se conserva.
--   b) Se pierde también el UUID → es, honestamente, un terminal nuevo. Se da
--      de alta como tal. No se intenta adivinar que es el mismo de antes:
--      inventar esa continuidad sería fabricar un dato que nadie podría
--      distinguir después de uno real.
--
-- El UUID vive en el store `meta` de IndexedDB del POS (`db.ts:104`), que es más
-- duradero que el estado de la aplicación. El caso (b) exige borrado explícito
-- del almacenamiento o reinstalación.
--
-- ── Qué NO lleva esta tabla ───────────────────────────────────────────
--
-- No lleva `ultimo_seq`. Sería una fila caliente por terminal, actualizada en
-- cada venta, y con dos cajas vendiendo a la vez se convierte en un punto de
-- contención en el camino crítico del cobro. La garantía de unicidad la da un
-- índice sobre `orders` (V36), que no tiene ese coste.
--
-- `ultima_conexion_en` se actualiza en el REGISTRO y en el latido, no en cada
-- orden, por lo mismo.
--
-- ── Impacto ───────────────────────────────────────────────────────────
--
-- Tabla nueva. No toca ninguna fila ni columna existente. Ningún servicio la
-- lee todavía. Cero filas al aplicar; se puebla sola conforme los terminales se
-- registren.
--
-- ── NUMERACIÓN — coordinada entre ramas ───────────────────────────────
--
-- La última aplicada es `V32`. Hay dos migraciones escritas y sin fusionar:
-- `V33` (aislamiento de las tablas de administración, rama `fix/rls-tablas-admin`)
-- y `V34` (procedencia del QR del cierre, rama `fix/cierre-caja-integridad`).
-- Esta es `V35` para no colisionar con ninguna de las dos.
--
-- Es la misma coordinación entre ramas paralelas que ya hizo la casa en el
-- cluster F5 (ver la nota de `V12:5`: "Numeración coordinada entre ramas f5:
-- V11=meseros, V13=pagos, V14=bajas").
--
-- ⚠️ ORDEN DE APLICACIÓN: `V33` y `V34` deben aplicarse ANTES que esta. Flyway
-- rechaza una migración con número inferior a la última aplicada salvo que se
-- active `outOfOrder`, que este proyecto no usa.
-- =====================================================================

-- Cambio de catálogo. Si la tabla está ocupada se prefiere fallar antes que
-- frenar una venta. Mismo criterio que V32:52.
SET lock_timeout = '3s';

CREATE TABLE IF NOT EXISTS terminals (
    -- UUID generado por el cliente en su primer arranque. Ver cabecera.
    id                  UUID        PRIMARY KEY,

    tenant_id           TEXT        NOT NULL,

    -- La sede a la que pertenece. NULLABLE: un terminal puede registrarse antes
    -- de que el administrador le asigne sede, y no puede quedarse sin vender
    -- por eso. El código lo trata como "sede por defecto del negocio", igual
    -- que hace el trigger del consecutivo con `orders.site_id` (V28:235-240).
    site_id             BIGINT      REFERENCES sites(id),

    -- Nombre corto legible: 'CAJA01', 'BARRA', 'DOMICILIOS'. Lo pone el
    -- administrador; el alta automática lo deja NULL porque el servidor no
    -- tiene forma de saber cómo llama el negocio a esa caja, y un nombre
    -- inventado es peor que ninguno.
    codigo              TEXT,

    -- Nombre largo opcional, para la interfaz.
    alias               TEXT,

    -- Enum CERRADO (reglas 9 y 10 de LINEAMIENTOS_DESARROLLO_DATA_FIRST).
    -- No hay valor "otro": si aparece un estado nuevo, se agrega con su
    -- migración.
    --   activo    opera normalmente
    --   inactivo  dado de baja temporalmente; sus ventas históricas se conservan
    --   retirado  el equipo ya no existe. NUNCA se borra la fila: sus ventas
    --             históricas siguen apuntando aquí
    estado              TEXT        NOT NULL DEFAULT 'activo',

    -- Alta: la primera vez que el servidor supo de este terminal.
    registrado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Último contacto conocido. Se actualiza en el registro y en el latido,
    -- NO en cada orden (ver cabecera).
    ultima_conexion_en  TIMESTAMPTZ,

    -- Epoch más alto que el servidor ha visto de este terminal. Sirve para
    -- detectar de un vistazo cuántas veces perdió su estado local.
    epoch_visto         INT         NOT NULL DEFAULT 1,

    CONSTRAINT ck_terminals_estado
        CHECK (estado IN ('activo', 'inactivo', 'retirado')),

    -- El epoch empieza en 1 y solo sube.
    CONSTRAINT ck_terminals_epoch CHECK (epoch_visto >= 1)
);

COMMENT ON TABLE terminals IS
    'Cajas fisicas. El id lo genera el cliente en su primer arranque para poder '
    'vender offline desde el segundo cero; el servidor da de alta a los '
    'desconocidos, nunca los rechaza.';
COMMENT ON COLUMN terminals.epoch_visto IS
    'Epoch mas alto visto. Sube cuando el terminal pierde su estado local y '
    'reinicia la secuencia. Un salto aqui es un hecho, no un error.';
COMMENT ON COLUMN terminals.codigo IS
    'Nombre corto que le da el negocio (CAJA01). NULL en el alta automatica: el '
    'servidor no puede saberlo y no se lo inventa.';

CREATE INDEX IF NOT EXISTS idx_terminals_tenant ON terminals (tenant_id);

-- El código es único DENTRO del negocio, y solo cuando existe: el alta
-- automática deja varios terminales con `codigo` NULL y no deben chocar entre
-- sí. Índice parcial, mismo recurso que `ux_orders_tenant_idempotency` (V17:19).
CREATE UNIQUE INDEX IF NOT EXISTS ux_terminals_tenant_codigo
    ON terminals (tenant_id, codigo)
    WHERE codigo IS NOT NULL;

-- Patrón RLS de V1:48-79, el mismo que ya protege 22 tablas. No se inventa uno
-- nuevo: ENABLE + FORCE + política por app.tenant_id + GRANT explícito.
ALTER TABLE terminals ENABLE ROW LEVEL SECURITY;
ALTER TABLE terminals FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies
                   WHERE tablename = 'terminals' AND policyname = 'tenant_isolation') THEN
        EXECUTE 'CREATE POLICY tenant_isolation ON terminals
                 USING (tenant_id = current_setting(''app.tenant_id'', true))
                 WITH CHECK (tenant_id = current_setting(''app.tenant_id'', true))';
    END IF;
END $$;

-- LECCIÓN DE V20/V22: la migración corre como owner y pasa verde aunque falten
-- los GRANT; la aplicación conecta como `app_user` y recibe "permission denied".
-- RLS no sustituye a los permisos: son dos capas y la de permisos va primero.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE terminals TO app_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_user;

-- ---------------------------------------------------------------------
-- Verificación: que la tabla quedó con aislamiento real y no abierta, que es
-- el error que V28 cometió en 17 tablas y que V33 viene a corregir.
-- ---------------------------------------------------------------------
DO $verificar$
DECLARE
    abiertas INT;
    sin_force INT;
BEGIN
    SELECT count(*) INTO abiertas
    FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'terminals'
      AND (qual IS NULL OR qual NOT LIKE '%app.tenant_id%');
    IF abiertas > 0 THEN
        RAISE EXCEPTION 'terminals quedo con % politica(s) que no filtran por app.tenant_id', abiertas;
    END IF;

    SELECT count(*) INTO sin_force
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = 'public'
    WHERE c.relname = 'terminals'
      AND NOT (c.relrowsecurity AND c.relforcerowsecurity);
    IF sin_force > 0 THEN
        RAISE EXCEPTION 'terminals quedo sin ENABLE+FORCE de RLS';
    END IF;
END $verificar$;


-- =====================================================================
-- DOWN — rollback explícito.
--
-- Seguro mientras V36 no esté aplicada. Con V36 aplicada hay que quitar antes
-- la clave foránea `orders.terminal_id`, o el DROP falla.
--
-- Se pierden los terminales registrados. Las ventas que los referencien
-- quedarían huérfanas, así que esto solo tiene sentido como marcha atrás
-- inmediata, antes de que ningún POS se haya registrado.
--
-- DROP INDEX IF EXISTS ux_terminals_tenant_codigo;
-- DROP INDEX IF EXISTS idx_terminals_tenant;
-- DROP POLICY IF EXISTS tenant_isolation ON terminals;
-- DROP TABLE IF EXISTS terminals;
-- =====================================================================
