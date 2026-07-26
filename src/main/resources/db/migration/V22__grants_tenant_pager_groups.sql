-- V22 — FIX de V20: permisos faltantes en tenant_pager_groups.
--
-- V20 creó la tabla pero NO le dio grants a `app_user`. Flyway corre como
-- `postgres` (dueño de la tabla), así que la migración pasó verde; pero la
-- aplicación conecta como `app_user` y recibía "permission denied" →
-- GET /orders/pager-availability y GET /account/pagers respondían 500.
--
-- El 500 se propagaba a todo lo demás: sin disponibilidad de rastreadores el
-- POS no marcaba los ocupados, autoseleccionaba uno en uso y la creación de la
-- orden fallaba con 409 "El pager ... ya está en uso", además de llenar la
-- pantalla de modales de error.
--
-- LECCIÓN: en esta base, toda tabla nueva necesita su GRANT explícito para
-- `app_user`. RLS NO sustituye a los permisos: son dos capas distintas y la de
-- permisos se evalúa primero.

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE tenant_pager_groups TO app_user;

-- La tabla usa IDENTITY: sin permiso sobre la secuencia, el INSERT falla.
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_user;
