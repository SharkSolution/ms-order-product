-- =====================================================================
-- V30 — PIN DEL MESERO (PLAN-UX-MESEROS #20).
--
-- Hasta ahora la app de meseros no verificaba NADA: se tocaba un nombre en una
-- lista y se operaba como esa persona — tomar pedidos, abrir turno con una base
-- y cerrarlo declarando cuánto efectivo hay. Cualquiera con el teléfono en la
-- mano podía cerrar el turno de otro y dejarle un faltante que después le
-- cobran a él.
--
-- El PIN lo configura EL PROPIO MESERO, no el administrador: una clave que otro
-- conoce no lo protege de nada, que es justo de lo que se trata.
--
-- ADITIVA Y REVERSIBLE. Una columna nueva, NULLABLE y sin default:
--
--   NULL = el mesero todavía no configuró su PIN  ->  entra sin PIN, como
--          siempre. Los meseros que ya existen no se enteran hasta que cada
--          uno decida ponerse el suyo.
--
-- Se guarda el HASH (BCrypt), nunca el PIN. Un PIN de 4 dígitos es corto por
-- diseño —tiene que poder teclearse con una bandeja en la otra mano— así que lo
-- que lo protege no es su longitud sino que no se pueda leer de la base y que
-- los intentos estén limitados en el servicio.
--
-- Rollback (se pierden los PIN configurados; todos vuelven a entrar sin clave):
--   ALTER TABLE waiters DROP COLUMN IF EXISTS pin_hash;
-- =====================================================================

ALTER TABLE waiters ADD COLUMN IF NOT EXISTS pin_hash TEXT;

COMMENT ON COLUMN waiters.pin_hash IS
    'BCrypt del PIN que el propio mesero configura. NULL = sin PIN, entra directo.';

-- Regla dura del proyecto: toda migración deja explícito el permiso del rol de
-- aplicación. Las columnas nuevas heredan los GRANT de la tabla, pero se repite
-- para que no dependa de eso.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE waiters TO app_user;
