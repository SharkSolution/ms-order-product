-- =====================================================================
-- V34 — EL MONTO DE QR DEL CIERRE DEJA DE SER UN NÚMERO SIN PROCEDENCIA.
--
-- ── El incidente ──────────────────────────────────────────────────────
--
-- `ExecuteDailyClosureUseCase` consultaba `/qr-payments/by-date` de `ms-core-app`
-- con `restTemplate.getForEntity(url, JsonNode.class)`, SIN cabeceras. El
-- 2026-07-30 se añadió `JwtTenantFilter` a ese servicio y la ruta pasó a exigir
-- un JWT de negocio: desde entonces devuelve 401.
--
-- El llamador atrapaba toda excepción (`ExecuteDailyClosureUseCase.java:211-213`),
-- dejaba un `log.warn` que además culpaba a "posible falta de internet", y
-- cuadraba el cierre con el valor manual del cajero.
--
-- **El defecto grave no es el 401. Es que el resultado fuera indistinguible.**
-- En `daily_closures` un cierre conciliado contra el registro del administrador y
-- uno cuadrado a mano tras un fallo de autenticación se veían exactamente igual:
-- un `NUMERIC` en `total_counted_qr`. Por eso el problema duró tres semanas sin
-- que nadie pudiera detectarlo mirando los datos.
--
-- ── Impacto en Producción ─────────────────────────────────────────────
--
-- Cierres afectados desde el 2026-07-30: [PENDIENTE — consulta de impacto]
--
-- No se estima. La consulta que da el número —de solo lectura— está en
-- `docs/CONSULTAS-VIGILANCIA.md` §2. Hasta tenerlo, este hueco se queda escrito:
-- un número inventado en una cabecera de migración es peor que un hueco.
--
-- ── Qué añade ─────────────────────────────────────────────────────────
--
-- Reglas 5 y 6 de LINEAMIENTOS_DESARROLLO_DATA_FIRST: todo dato que sale de una
-- fuente viaja con su nivel de confianza y con su fuente explícita.
--
--   qr_fuente       enum CERRADO. De dónde salió el número.
--   qr_confianza    0–3. Qué tan fiable es.
--   qr_capturado_en cuándo se resolvió (regla 6 pide `fecha_captura`).
--   qr_detalle      mensaje técnico del fallo. NO analizable.
--
-- ── Por qué CHECK y no un tipo ENUM de Postgres ───────────────────────
--
-- Es el patrón de la casa: `sites.pos_mode` (V23:27) y `table_sessions.status`
-- (V25:23) usan TEXT + CHECK. Añadir un valor es un `ALTER ... DROP/ADD
-- CONSTRAINT` en una migración normal; con un tipo ENUM nativo, `ALTER TYPE ...
-- ADD VALUE` no puede correr dentro de una transacción, lo que complica el
-- despliegue sin dar nada a cambio. Cerrado es cerrado en los dos casos.
--
-- Los tres valores, y NO hay un cuarto de tipo "otro" (regla 10):
--
--   conciliado_core     ms-core-app respondió y el monto salió de su registro.
--   manual_cajero       la consulta funcionó y respondió 404 (no hay pago QR
--                       registrado ese día): se usa el valor del cajero.
--   fallo_integracion   no se pudo conciliar (401, timeout, 5xx, respuesta
--                       ilegible). El cierre se completa igual, marcado, con el
--                       motivo técnico real en qr_detalle.
--
-- Un 404 NO es un fallo: es la respuesta correcta a "¿hay algo?" cuando no hay
-- nada (`QrPaymentController.java:35-37`). Y un 404 tampoco se registra como
-- `conciliado_core` con monto cero: "no hay registro" no es "el registro dice
-- cero". Separar esos dos casos, que hasta ahora caían en el mismo `catch`, es
-- lo que vuelve el problema detectable.
--
-- ── Los cierres ya existentes ─────────────────────────────────────────
--
-- No se pueden reclasificar: no hay forma de saber, mirando una fila de antes de
-- hoy, si su QR se concilió o no. Se dejan con `qr_fuente = NULL`, que significa
-- exactamente eso — "de antes de que esto se registrara"— y no se confunde con
-- ninguno de los tres valores. Inventarles una fuente sería fabricar historia,
-- que es justo lo que la regla 6 existe para impedir.
--
-- Por eso la columna es NULLABLE y sin DEFAULT. En cuanto el código nuevo esté
-- desplegado, toda fila nueva la trae.
--
-- ── Aditiva y reversible ──────────────────────────────────────────────
--
-- Cuatro columnas nuevas, nullables, sin default. No toca ninguna fila ni
-- ninguna columna existente. El código viejo desplegado contra este esquema
-- sigue funcionando: simplemente deja las columnas en NULL.
--
-- Bloque DOWN al final, comentado.
--
-- ── NUMERACIÓN ────────────────────────────────────────────────────────
--
-- La última escrita es V33 (aislamiento de las tablas de administración, aún sin
-- aplicar). V19 quedó sin usar a propósito, ver V21:21-23. Esta es la V34.
--
-- ⚠️ V34 es INDEPENDIENTE de V33: no comparten tablas y se pueden aplicar en
-- cualquier orden. V33 sigue bloqueada por PRE-REQUISITOS-RLS.md; V34 no lo está.
-- =====================================================================

-- Cambio de catálogo (microsegundos), pero si la tabla está ocupada se prefiere
-- fallar antes que frenar un cierre de caja. Mismo criterio que V32:52.
SET lock_timeout = '3s';

ALTER TABLE daily_closures ADD COLUMN IF NOT EXISTS qr_fuente       TEXT;
ALTER TABLE daily_closures ADD COLUMN IF NOT EXISTS qr_confianza    SMALLINT;
ALTER TABLE daily_closures ADD COLUMN IF NOT EXISTS qr_capturado_en TIMESTAMPTZ;
ALTER TABLE daily_closures ADD COLUMN IF NOT EXISTS qr_detalle      TEXT;

COMMENT ON COLUMN daily_closures.qr_fuente IS
    'De donde salio total_counted_qr: conciliado_core | manual_cajero | fallo_integracion. '
    'NULL = cierre anterior a V34, no reclasificable.';
COMMENT ON COLUMN daily_closures.qr_confianza IS
    'Nivel de confianza 0-3 del monto de QR (regla 5). 2 = conciliado contra ms-core-app; '
    '0 = sin conciliar.';
COMMENT ON COLUMN daily_closures.qr_capturado_en IS
    'Cuando se resolvio el monto de QR (regla 6, fecha_captura).';
COMMENT ON COLUMN daily_closures.qr_detalle IS
    'Mensaje TECNICO del fallo de integracion. Campo de diagnostico, NO analizable: '
    'lo analizable es qr_fuente.';

-- Enum cerrado. Se admite NULL solo por el histórico; el código nuevo siempre
-- escribe uno de los tres valores.
ALTER TABLE daily_closures DROP CONSTRAINT IF EXISTS ck_daily_closures_qr_fuente;
ALTER TABLE daily_closures ADD CONSTRAINT ck_daily_closures_qr_fuente
    CHECK (qr_fuente IS NULL
           OR qr_fuente IN ('conciliado_core', 'manual_cajero', 'fallo_integracion'));

-- La escala es 0–3 (regla 5). Fuera de ese rango es un error de programación,
-- no un dato.
ALTER TABLE daily_closures DROP CONSTRAINT IF EXISTS ck_daily_closures_qr_confianza;
ALTER TABLE daily_closures ADD CONSTRAINT ck_daily_closures_qr_confianza
    CHECK (qr_confianza IS NULL OR (qr_confianza >= 0 AND qr_confianza <= 3));

-- Coherencia entre las dos columnas: un dato conciliado no puede declararse sin
-- confianza, y uno sin conciliar no puede declararse fiable. Sostenido por la
-- BASE y no solo por el codigo, como el resto de invariantes de dinero de este
-- esquema (ck_split_cuadra en V29:55).
ALTER TABLE daily_closures DROP CONSTRAINT IF EXISTS ck_daily_closures_qr_coherencia;
ALTER TABLE daily_closures ADD CONSTRAINT ck_daily_closures_qr_coherencia
    CHECK (
        qr_fuente IS NULL
        OR (qr_fuente = 'conciliado_core'   AND qr_confianza >= 1)
        OR (qr_fuente IN ('manual_cajero', 'fallo_integracion') AND qr_confianza = 0)
    );

-- Un fallo sin explicacion no sirve para diagnosticar nada: si la fuente es
-- fallo_integracion, el detalle tecnico es obligatorio.
ALTER TABLE daily_closures DROP CONSTRAINT IF EXISTS ck_daily_closures_qr_detalle;
ALTER TABLE daily_closures ADD CONSTRAINT ck_daily_closures_qr_detalle
    CHECK (qr_fuente IS DISTINCT FROM 'fallo_integracion'
           OR (qr_detalle IS NOT NULL AND length(btrim(qr_detalle)) > 0));

-- La consulta de vigilancia (docs/CONSULTAS-VIGILANCIA.md) filtra por fuente y
-- fecha: ese es el indice que importa. Parcial, porque lo que se busca son los
-- fallos, que deberian ser pocos.
CREATE INDEX IF NOT EXISTS idx_daily_closures_qr_fallo
    ON daily_closures (tenant_id, closure_date)
    WHERE qr_fuente = 'fallo_integracion';

-- Regla dura del proyecto desde V22: toda migracion deja explicito el permiso
-- del rol de aplicacion. Las columnas nuevas heredan los GRANT de la tabla, pero
-- se repite para que no dependa de eso.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE daily_closures TO app_user;

-- ---------------------------------------------------------------------
-- Verificacion: que las restricciones quedaron puestas. Si algo fallo, la
-- migracion se marca en rojo en vez de dejar el enum a medio cerrar.
-- ---------------------------------------------------------------------
DO $verificar$
DECLARE
    faltan INT;
BEGIN
    SELECT 4 - count(*) INTO faltan
    FROM pg_constraint
    WHERE conrelid = 'daily_closures'::regclass
      AND conname IN ('ck_daily_closures_qr_fuente',
                      'ck_daily_closures_qr_confianza',
                      'ck_daily_closures_qr_coherencia',
                      'ck_daily_closures_qr_detalle');

    IF faltan <> 0 THEN
        RAISE EXCEPTION 'Faltan % restricciones de qr_fuente/qr_confianza en daily_closures', faltan;
    END IF;
END $verificar$;


-- =====================================================================
-- DOWN — rollback explicito.
--
-- Se pierden la procedencia y la confianza de los cierres registrados desde que
-- se aplico. El monto (total_counted_qr) NO se toca: el cuadre historico se
-- mantiene al centavo.
--
-- ALTER TABLE daily_closures DROP CONSTRAINT IF EXISTS ck_daily_closures_qr_detalle;
-- ALTER TABLE daily_closures DROP CONSTRAINT IF EXISTS ck_daily_closures_qr_coherencia;
-- ALTER TABLE daily_closures DROP CONSTRAINT IF EXISTS ck_daily_closures_qr_confianza;
-- ALTER TABLE daily_closures DROP CONSTRAINT IF EXISTS ck_daily_closures_qr_fuente;
-- DROP INDEX IF EXISTS idx_daily_closures_qr_fallo;
-- ALTER TABLE daily_closures DROP COLUMN IF EXISTS qr_detalle;
-- ALTER TABLE daily_closures DROP COLUMN IF EXISTS qr_capturado_en;
-- ALTER TABLE daily_closures DROP COLUMN IF EXISTS qr_confianza;
-- ALTER TABLE daily_closures DROP COLUMN IF EXISTS qr_fuente;
--
-- OJO: aplicar el DOWN con el codigo nuevo desplegado ROMPE el cierre de caja
-- (Hibernate fallaria al mapear DailyClosure). Misma leccion de V21:15-16.
-- =====================================================================
