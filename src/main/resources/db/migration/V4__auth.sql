-- =====================================================================
-- Auth real (F1) — tablas globales de tenants y usuarios.
-- Reemplaza el login demo (clave compartida + tenant tecleado) por
-- usuarios con email+contraseña que DERIVAN su tenant. Ver docs/110-plan-auth-real.md.
--
-- Estas tablas son GLOBALES (catálogo de negocios/usuarios): NO llevan la
-- política RLS por app.tenant_id, porque el login ocurre ANTES de tener un
-- tenant en contexto. Su acceso queda restringido a nivel de aplicación: solo
-- AuthController/AuthService (exentos del TenantContextFilter) las tocan; ningún
-- endpoint de negocio las expone. Ver docs/110 §3 (opción a).
-- =====================================================================

CREATE TABLE tenants (
    id          TEXT PRIMARY KEY,                    -- slug; == claim tenant_id del JWT
    name        TEXT        NOT NULL,                -- nombre visible del negocio
    plan        TEXT        NOT NULL DEFAULT 'pro',  -- basico | pro (define módulos)
    status      TEXT        NOT NULL DEFAULT 'active', -- active | suspended
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id             BIGSERIAL   PRIMARY KEY,
    email          TEXT        NOT NULL UNIQUE,      -- credencial de login (case-insensitive por app)
    password_hash  TEXT        NOT NULL,             -- BCrypt
    tenant_id      TEXT        NOT NULL REFERENCES tenants(id),
    role           TEXT        NOT NULL DEFAULT 'admin', -- admin | cajero | ...
    status         TEXT        NOT NULL DEFAULT 'active', -- active | disabled
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_tenant ON users (tenant_id);

-- El rol de aplicación (app_user, sin BYPASSRLS) necesita leer/escribir estas
-- tablas. El aislamiento entre negocios aquí es responsabilidad de la app (el
-- login ocurre sin tenant en contexto), NO de una política por tenant.
GRANT SELECT, INSERT, UPDATE, DELETE ON tenants, users TO app_user;
GRANT USAGE, SELECT ON SEQUENCE users_id_seq TO app_user;

-- RLS: en Supabase toda tabla nueva de `public` queda con RLS activado por
-- defecto, y sin políticas devuelve 0 filas incluso a app_user (que no es owner).
-- Se activa explícitamente (idempotente; en Postgres plano hay que hacerlo) y se
-- da a app_user una política ALL abierta: puede operar sobre el catálogo global,
-- mientras anon/authenticated (roles de PostgREST) siguen sin acceso al no tener
-- GRANT. No es aislamiento por-tenant: estas tablas SON el catálogo de tenants.
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE users   ENABLE ROW LEVEL SECURITY;
CREATE POLICY app_rw_tenants ON tenants FOR ALL TO app_user USING (true) WITH CHECK (true);
CREATE POLICY app_rw_users   ON users   FOR ALL TO app_user USING (true) WITH CHECK (true);

-- ------------------------------------------------------------------
-- Semilla: tenant demo + usuario admin, para no romper staging.
-- Clave 'shark2026' (hash BCrypt $2a$10). Cambiar/rotar en producción.
-- ------------------------------------------------------------------
INSERT INTO tenants (id, name, plan) VALUES ('shark-burger', 'Shark Burger', 'pro')
    ON CONFLICT (id) DO NOTHING;

INSERT INTO users (email, password_hash, tenant_id, role) VALUES
    ('admin@sharkburger.co',
     '$2a$10$lM1WJngu0T/FrD9PaW15QeR/PbGuZPXn7mRqrmChmQCupcfHw7jP.',
     'shark-burger', 'admin')
    ON CONFLICT (email) DO NOTHING;
