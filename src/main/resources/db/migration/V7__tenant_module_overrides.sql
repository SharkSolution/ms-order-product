-- =====================================================================
-- Overrides de módulos por tenant (F3, docs/160 / docs/50 §3).
--
-- Encima del mapa plan→módulos (PlanCatalog), permite REGALAR (enabled=true) o
-- QUITAR (enabled=false) un módulo puntual a un negocio sin cambiar su plan.
-- Módulos efectivos = (módulos del plan ∪ grants) − revokes.
--
-- Tabla GLOBAL de configuración (como tenants/users): se lee en el login (sin
-- tenant en contexto) y la gestiona el admin. RLS con política abierta para
-- app_user (el aislamiento por-tenant lo hace la app, filtrando por tenant_id).
-- =====================================================================

CREATE TABLE tenant_modules (
    tenant_id  TEXT    NOT NULL REFERENCES tenants(id),
    module     TEXT    NOT NULL,
    enabled    BOOLEAN NOT NULL,
    PRIMARY KEY (tenant_id, module)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON tenant_modules TO app_user;

ALTER TABLE tenant_modules ENABLE ROW LEVEL SECURITY;
CREATE POLICY app_rw_tenant_modules ON tenant_modules
    FOR ALL TO app_user USING (true) WITH CHECK (true);
