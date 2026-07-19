-- =====================================================================
-- Super-admin (KAM) GLOBAL — usuario fuera de los tenants que administra TODOS
-- los negocios (soporte, planes, módulos) desde el panel. F3, Inc.3, docs/160.
--
-- Tabla GLOBAL (no lleva tenant_id): los endpoints /admin/** no están scopeados por
-- tenant. RLS con política abierta para app_user (el acceso lo controla la app: solo
-- un JWT de super-admin válido llega a esos endpoints).
-- =====================================================================

CREATE TABLE super_admins (
    id             BIGSERIAL   PRIMARY KEY,
    email          TEXT        NOT NULL UNIQUE,
    password_hash  TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

GRANT SELECT, INSERT, UPDATE, DELETE ON super_admins TO app_user;
GRANT USAGE, SELECT ON SEQUENCE super_admins_id_seq TO app_user;

ALTER TABLE super_admins ENABLE ROW LEVEL SECURITY;
CREATE POLICY app_rw_super_admins ON super_admins
    FOR ALL TO app_user USING (true) WITH CHECK (true);

-- Semilla staging: KAM demo. Clave 'shark2026' (mismo hash BCrypt del demo). Rotar en prod.
INSERT INTO super_admins (email, password_hash) VALUES
    ('kam@suresell.co', '$2a$10$lM1WJngu0T/FrD9PaW15QeR/PbGuZPXn7mRqrmChmQCupcfHw7jP.')
    ON CONFLICT (email) DO NOTHING;
