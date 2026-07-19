-- =====================================================================
-- Reset de contraseña por email (F3, Inc.5, docs/160).
--
-- El backend genera un token de un solo uso con expiración; envía el link por
-- email (Edge Function de Supabase). Se guarda el HASH (SHA-256) del token, no el
-- token en claro (una fuga de DB no expone tokens usables).
--
-- Tabla global (como users): la consulta el flujo de reset (sin tenant en contexto).
-- =====================================================================

CREATE TABLE password_resets (
    token_hash  TEXT        PRIMARY KEY,
    email       TEXT        NOT NULL,
    tenant_id   TEXT        NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN     NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_resets_email ON password_resets (lower(email));

GRANT SELECT, INSERT, UPDATE, DELETE ON password_resets TO app_user;

ALTER TABLE password_resets ENABLE ROW LEVEL SECURITY;
CREATE POLICY app_rw_password_resets ON password_resets
    FOR ALL TO app_user USING (true) WITH CHECK (true);
