-- =====================================================================
-- V27 — Los planes salen de constantes de Java y pasan a la base.
--
-- Hasta aquí el mapa plan → módulos vivía en `PlanCatalog.PLAN_MODULES`, así
-- que crear un plan o cambiar qué incluye exigía editar código y redesplegar.
-- El propio archivo lo tenía anotado como deuda ("en F3.3 esto se mueve a DB
-- gestionable desde el panel"). Esto es eso.
--
-- Tablas GLOBALES (sin tenant_id): un plan es del catálogo de SureSell, no de
-- un negocio. Mismo patrón que `super_admins` (V8): RLS con política abierta
-- para app_user, y el acceso lo controla la app —solo un JWT de super-admin
-- llega a /admin/**.
--
-- Se siembran `basico` y `pro` EXACTAMENTE como estaban en código, para que el
-- comportamiento no cambie al desplegar.
-- =====================================================================

CREATE TABLE IF NOT EXISTS plans (
    id          TEXT        PRIMARY KEY,          -- 'basico', 'pro', 'asociado'…
    name        TEXT        NOT NULL,             -- nombre visible: "Pro"
    description TEXT,
    active      BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS plan_modules (
    plan_id  TEXT NOT NULL REFERENCES plans (id) ON DELETE CASCADE,
    module   TEXT NOT NULL,
    PRIMARY KEY (plan_id, module)
);

-- LECCIÓN de V20/V22: en esta base toda tabla nueva necesita GRANT explícito
-- para `app_user`. Flyway corre como `postgres` y la migración pasa verde
-- igual; la aplicación conecta como `app_user` y recibe "permission denied".
-- RLS NO sustituye a los permisos: son dos capas y la de permisos va primero.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE plans TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE plan_modules TO app_user;

ALTER TABLE plans ENABLE ROW LEVEL SECURITY;
CREATE POLICY app_rw_plans ON plans
    FOR ALL TO app_user USING (true) WITH CHECK (true);

ALTER TABLE plan_modules ENABLE ROW LEVEL SECURITY;
CREATE POLICY app_rw_plan_modules ON plan_modules
    FOR ALL TO app_user USING (true) WITH CHECK (true);

-- --- Semilla: los planes tal cual estaban en PlanCatalog ---------------
INSERT INTO plans (id, name, description) VALUES
    ('basico', 'Básico', 'POS, historial, cierre de caja y cocina.'),
    ('pro',    'Pro',    'Todo lo del básico + descuentos, meseros y panel de administración.')
ON CONFLICT (id) DO NOTHING;

-- basico = ventas, historial, cierre, cocina
INSERT INTO plan_modules (plan_id, module)
SELECT 'basico', m FROM unnest(ARRAY['ventas','historial','cierre','cocina']) AS m
ON CONFLICT DO NOTHING;

-- pro = los del POS + los del panel que entraban en PANEL_PRO
INSERT INTO plan_modules (plan_id, module)
SELECT 'pro', m FROM unnest(ARRAY[
    'ventas','historial','cierre','descuentos','cocina','meseros',
    'panel','analitica','menu','gastos'
]) AS m
ON CONFLICT DO NOTHING;
