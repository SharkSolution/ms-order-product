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
-- ── Impacto en Producción — MEDIDO, no estimado ───────────────────────
--
--   · Cierres afectados entre el 2026-07-30 y el 2026-08-19: **13**
--   · Monto acumulado de QR en esos cierres: **$5.982.600**
--   · `qr_registrado` en los trece: **0**
--
-- ── ⚠️ LA PREMISA DE LA PRIMERA VERSIÓN ERA FALSA ─────────────────────
--
-- La primera versión de esta migración daba por hecho que `qr_payments` era la
-- fuente de verdad del QR y que el 401 solo impedía llegar a ella.
--
-- **Los datos lo desmienten: `qr_payments` tiene TRES filas en toda su
-- historia.** Nunca hubo nada contra qué conciliar. El administrador
-- prácticamente no ha usado ese registro.
--
-- Y ahí está el defecto grave de aquella versión: con `conciliado_core` como
-- fuente preferente, un `ms-core-app` que responde correctamente **200 con
-- amount = 0** —porque no hay registro— habría hecho que cada cierre reportara
-- CERO esperado en QR, cuando el negocio recibe del orden de **$460.000 diarios**
-- por ese medio. El arreglo habría sido peor que el defecto.
--
-- ── Tres columnas, tres hechos, ninguno destruido ─────────────────────
--
-- El error de fondo era guardar UN monto de QR y discutir de dónde salía. Hay
-- tres hechos distintos y los tres valen:
--
--   `qr_pos`              lo que el POS registró como cobrado por QR ese día
--                         (suma de `orders` con `payment_method = 'QR'`).
--                         Es el único que existe SIEMPRE, porque sale de las
--                         ventas mismas.
--   `qr_manual_cajero`    lo que el cajero teclea al cerrar.
--   `qr_conciliado_core`  lo que devuelva `ms-core-app`, si devuelve algo.
--
-- `total_counted_qr` sigue siendo el monto que manda en el cuadre y **no cambia
-- de valor respecto a hoy**: el local cierra con el mismo número. Lo que cambia
-- es que ahora se sabe de dónde salió y con qué otros dos hechos convive.
--
-- ── LA REGLA DURA ─────────────────────────────────────────────────────
--
-- **Un conciliado de 0 con un manual mayor que 0 NUNCA se convierte en un total
-- de 0.** Eso es `sin_registro_externo`, confianza 0, y el total sigue usando el
-- manual. Es exactamente el escenario que los datos revelaron y que la primera
-- versión habría producido trece veces.
--
-- El invariante lo sostiene la BASE (`ck_daily_closures_qr_cero_externo`), no el
-- código, por la misma razón que argumenta `V17:5-8`.
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

ALTER TABLE daily_closures ADD COLUMN IF NOT EXISTS qr_fuente          TEXT;
ALTER TABLE daily_closures ADD COLUMN IF NOT EXISTS qr_confianza       SMALLINT;
ALTER TABLE daily_closures ADD COLUMN IF NOT EXISTS qr_capturado_en    TIMESTAMPTZ;
ALTER TABLE daily_closures ADD COLUMN IF NOT EXISTS qr_detalle         TEXT;

-- Los tres hechos, cada uno en su columna. Ninguno se destruye para producir
-- otro; el que manda en el cuadre sigue siendo `total_counted_qr`.
ALTER TABLE daily_closures ADD COLUMN IF NOT EXISTS qr_pos             NUMERIC(15,2);
ALTER TABLE daily_closures ADD COLUMN IF NOT EXISTS qr_manual_cajero   NUMERIC(15,2);
ALTER TABLE daily_closures ADD COLUMN IF NOT EXISTS qr_conciliado_core NUMERIC(15,2);

COMMENT ON COLUMN daily_closures.qr_pos IS
    'Suma de las ventas del dia con payment_method = QR. El unico de los tres que '
    'existe SIEMPRE, porque sale de las ventas mismas.';
COMMENT ON COLUMN daily_closures.qr_manual_cajero IS
    'Lo que teclea el cajero al cerrar.';
COMMENT ON COLUMN daily_closures.qr_conciliado_core IS
    'Lo que devolvio ms-core-app, si devolvio algo. NULL = no se pudo consultar o '
    'no hay registro. Ojo: qr_payments tiene 3 filas en toda su historia.';

COMMENT ON COLUMN daily_closures.qr_fuente IS
    'De donde salio total_counted_qr: conciliado_core | pos | manual_cajero | '
    'sin_registro_externo | fallo_integracion. NULL = cierre anterior a V34, no '
    'reclasificable.';
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
           OR qr_fuente IN ('conciliado_core',       -- ms-core-app respondio con un monto real
                            'pos',                   -- se uso la suma de ventas por QR del POS
                            'manual_cajero',         -- se uso el valor tecleado
                            'sin_registro_externo',  -- core respondio 0 o 404; hay manual > 0
                            'fallo_integracion'));   -- 401, timeout, 5xx, respuesta ilegible

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
        -- Conciliado contra una segunda fuente: es el unico con confianza > 0.
        OR (qr_fuente = 'conciliado_core' AND qr_confianza >= 1)
        -- Todo lo demas es un solo origen sin verificar contra nada.
        OR (qr_fuente IN ('pos', 'manual_cajero', 'sin_registro_externo', 'fallo_integracion')
            AND qr_confianza = 0)
    );

-- Un fallo sin explicacion no sirve para diagnosticar nada: si la fuente es
-- fallo_integracion, el detalle tecnico es obligatorio.
ALTER TABLE daily_closures DROP CONSTRAINT IF EXISTS ck_daily_closures_qr_detalle;
ALTER TABLE daily_closures ADD CONSTRAINT ck_daily_closures_qr_detalle
    CHECK (qr_fuente IS DISTINCT FROM 'fallo_integracion'
           OR (qr_detalle IS NOT NULL AND length(btrim(qr_detalle)) > 0));

-- ── LA REGLA DURA, sostenida por la base ─────────────────────────────
--
-- Un conciliado de 0 con un manual mayor que 0 NO puede producir un total de 0.
-- Si el externo dice cero pero el cajero conto dinero, el hecho es
-- `sin_registro_externo` y el total usa el manual.
--
-- Es el escenario que los datos revelaron —qr_payments con tres filas en toda su
-- historia— y que la primera version de esta migracion habria producido trece
-- veces. Que lo impida la BASE y no el codigo es lo mismo que argumenta V17:5-8:
-- un chequeo en el codigo protege del codigo de hoy.
ALTER TABLE daily_closures DROP CONSTRAINT IF EXISTS ck_daily_closures_qr_cero_externo;
ALTER TABLE daily_closures ADD CONSTRAINT ck_daily_closures_qr_cero_externo
    CHECK (
        qr_fuente IS DISTINCT FROM 'conciliado_core'
        OR qr_conciliado_core IS NULL
        OR qr_conciliado_core <> 0
        OR coalesce(qr_manual_cajero, 0) = 0
    );

-- Y el total nunca puede ser cero habiendo manual: es la misma regla vista desde
-- el resultado en vez de desde la fuente.
ALTER TABLE daily_closures DROP CONSTRAINT IF EXISTS ck_daily_closures_qr_total_no_se_pierde;
ALTER TABLE daily_closures ADD CONSTRAINT ck_daily_closures_qr_total_no_se_pierde
    CHECK (
        qr_fuente IS NULL
        OR coalesce(qr_manual_cajero, 0) = 0
        OR coalesce(total_counted_qr, 0) <> 0
    );

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
    SELECT 6 - count(*) INTO faltan
    FROM pg_constraint
    WHERE conrelid = 'daily_closures'::regclass
      AND conname IN ('ck_daily_closures_qr_fuente',
                      'ck_daily_closures_qr_confianza',
                      'ck_daily_closures_qr_coherencia',
                      'ck_daily_closures_qr_detalle',
                      'ck_daily_closures_qr_cero_externo',
                      'ck_daily_closures_qr_total_no_se_pierde');

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
-- ALTER TABLE daily_closures DROP CONSTRAINT IF EXISTS ck_daily_closures_qr_total_no_se_pierde;
-- ALTER TABLE daily_closures DROP CONSTRAINT IF EXISTS ck_daily_closures_qr_cero_externo;
-- ALTER TABLE daily_closures DROP COLUMN IF EXISTS qr_conciliado_core;
-- ALTER TABLE daily_closures DROP COLUMN IF EXISTS qr_manual_cajero;
-- ALTER TABLE daily_closures DROP COLUMN IF EXISTS qr_pos;
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
