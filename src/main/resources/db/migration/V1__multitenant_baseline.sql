-- =====================================================================
-- Multi-tenancy baseline (B1) — patrón tenant_id + Row-Level Security.
-- Aplica al esquema Postgres cloud. Ver docs/40-multitenant.md.
--
-- Modela las tablas core de órdenes con tenant_id. El resto de tablas
-- (closures, coupons, menu, etc.) se añaden con el mismo patrón en migraciones
-- siguientes. NO se ejecuta contra el SQLite local (spring.flyway.enabled=false).
-- =====================================================================

CREATE TABLE orders (
    uuid_id             UUID PRIMARY KEY,
    tenant_id           TEXT NOT NULL,
    id_order            BIGINT,
    pager_color         TEXT,
    pager_number        TEXT,
    created_at          TIMESTAMP,
    status              TEXT,
    payment_method      TEXT,
    subtotal            NUMERIC(15,2),
    total               NUMERIC(15,2),
    discount_code       TEXT,
    discount_percentage NUMERIC(15,2),
    discount_amount     NUMERIC(15,2),
    synced              BOOLEAN,
    is_printed          BOOLEAN
);

CREATE TABLE order_item (
    uuid_id        UUID PRIMARY KEY,
    tenant_id      TEXT NOT NULL,
    id_order_item  BIGINT,
    order_id       BIGINT,
    product_id     TEXT,
    quantity       INT,
    unit_price     NUMERIC(15,2),
    total_price    NUMERIC(15,2),
    instructions   TEXT,
    combo_group    INT
);

CREATE INDEX idx_orders_tenant ON orders (tenant_id);
CREATE INDEX idx_order_item_tenant ON order_item (tenant_id);

-- Habilitar y FORZAR RLS. FORCE hace que la política aplique incluso al owner
-- de la tabla (aunque los superusuarios y roles con BYPASSRLS siempre la saltan;
-- por eso la app se conecta con un rol normal, ver abajo).
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders FORCE ROW LEVEL SECURITY;
ALTER TABLE order_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_item FORCE ROW LEVEL SECURITY;

-- Aislamiento: una fila solo es visible/insertable si su tenant_id coincide con
-- el tenant del contexto de sesión (app.tenant_id). Si no está fijado,
-- current_setting(...,true) devuelve NULL y no se ve ninguna fila (default seguro).
CREATE POLICY tenant_isolation_orders ON orders
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));

CREATE POLICY tenant_isolation_order_item ON order_item
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));

-- Rol de aplicación: NO superusuario, sin BYPASSRLS, para que RLS aplique.
-- La app se conecta como este rol. En producción la contraseña viene de
-- variables de entorno / secret manager, no del código.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'app_user') THEN
        CREATE ROLE app_user LOGIN PASSWORD 'app_pw';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON orders, order_item TO app_user;
