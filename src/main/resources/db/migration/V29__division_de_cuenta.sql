-- =====================================================================
-- V29 — DIVISIÓN DE CUENTA entre N comensales (último pendiente del bloque 3
-- de docs/v2-final/05-FEATURES-FALTANTES-V2.md).
--
-- Repartir el total de una mesa entre N no da pesos enteros. La decisión de
-- negocio tomada es: **el negocio absorbe el redondeo, nunca el comensal**.
-- Nunca se cobra de más; los pesos sobrantes no se le cobran a nadie.
--
-- Ese residuo NO puede desaparecer en silencio: el catálogo comercial promete
-- cierres "auditables al peso". Por eso estas dos columnas — el residuo queda
-- registrado en la mesa que lo generó Y en el cierre de caja que lo reporta.
--
-- ADITIVA Y REVERSIBLE. Sin DROP, sin NOT NULL sobre columnas existentes.
-- Todas las columnas nuevas traen DEFAULT, así que las 3.639 órdenes y los
-- cierres ya existentes no se enteran.
--
-- Rollback (solo si hace falta; se pierden los ajustes registrados):
--   ALTER TABLE table_sessions DROP COLUMN IF EXISTS rounding_adjustment;
--   ALTER TABLE table_sessions DROP COLUMN IF EXISTS split_persons;
--   ALTER TABLE daily_closures DROP COLUMN IF EXISTS rounding_adjustment;
-- =====================================================================

-- Por MESA: cuánto se dejó de cobrar en esa cuenta y entre cuántos se dividió.
ALTER TABLE table_sessions
    ADD COLUMN IF NOT EXISTS rounding_adjustment NUMERIC(15,2) NOT NULL DEFAULT 0;

ALTER TABLE table_sessions
    ADD COLUMN IF NOT EXISTS split_persons INTEGER;

-- Por CIERRE: el total del día, guardado en el documento que se audita.
ALTER TABLE daily_closures
    ADD COLUMN IF NOT EXISTS rounding_adjustment NUMERIC(15,2) NOT NULL DEFAULT 0;

-- Las columnas nuevas heredan los GRANT de la tabla, pero se repiten por la
-- regla dura del proyecto: toda migración deja explícito el permiso del rol de
-- aplicación. Sin esto, RLS + un GRANT faltante = "error siempre" en la feature.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE table_sessions TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE daily_closures TO app_user;
