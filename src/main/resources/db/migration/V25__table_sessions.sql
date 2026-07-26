-- V25 — Cuentas abiertas por mesa (Inc. 3 del modo Restaurante).
--
-- LA PIEZA CLAVE ES EL ÍNDICE ÚNICO PARCIAL de abajo: garantiza a nivel de BASE
-- DE DATOS que una mesa no puede tener dos cuentas abiertas a la vez. Un chequeo
-- en código sería check-then-act y dos cajas cobrando a la vez lo atravesarían
-- —es la misma lección del doble INSERT de órdenes (V17)—.
--
-- Aditiva y reversible. Rollback en docs/migraciones/V23-V25-modo-restaurante.md.

CREATE TABLE IF NOT EXISTS table_sessions (
    id          uuid PRIMARY KEY,
    tenant_id   text      NOT NULL,
    site_id     bigint    REFERENCES sites(id),
    table_id    bigint    NOT NULL REFERENCES restaurant_tables(id),
    status      text      NOT NULL DEFAULT 'ABIERTA',
    opened_at   timestamp NOT NULL,
    closed_at   timestamp,
    opened_by   text,
    -- Lock SUAVE: no impide nada, permite avisar "Caja 2 está cobrando esta mesa"
    -- en vez de que dos cajas cobren en paralelo sin enterarse.
    claimed_by  text,
    claimed_at  timestamp,
    CONSTRAINT ck_table_sessions_status CHECK (status IN ('ABIERTA', 'COBRANDO', 'CERRADA'))
);

CREATE INDEX IF NOT EXISTS idx_table_sessions_tenant ON table_sessions (tenant_id);
CREATE INDEX IF NOT EXISTS idx_table_sessions_table  ON table_sessions (table_id);

-- UNA sola cuenta viva por mesa. Garantía dura, no cortesía de la aplicación.
CREATE UNIQUE INDEX IF NOT EXISTS ux_table_session_abierta
    ON table_sessions (tenant_id, table_id) WHERE status <> 'CERRADA';

ALTER TABLE table_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE table_sessions FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename='table_sessions' AND policyname='tenant_isolation') THEN
        EXECUTE 'CREATE POLICY tenant_isolation ON table_sessions
                 USING (tenant_id = current_setting(''app.tenant_id'', true))
                 WITH CHECK (tenant_id = current_setting(''app.tenant_id'', true))';
    END IF;
END $$;

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE table_sessions TO app_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_user;

-- La orden se liga a la cuenta de la mesa. NULLABLE: las 3.639 órdenes
-- existentes y todo el flujo de plazoleta siguen sin enterarse.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS table_session_id uuid REFERENCES table_sessions(id);
CREATE INDEX IF NOT EXISTS idx_orders_table_session ON orders (table_session_id);
