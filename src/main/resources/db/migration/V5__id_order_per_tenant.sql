-- =====================================================================
-- id_order correlativo POR NEGOCIO (antes: secuencia global).
--
-- Motivo: con una secuencia global, cada tenant veía números de orden altos y
-- discontinuos, y se filtraba el volumen total entre negocios. Cada negocio debe
-- tener su propia numeración 1,2,3… Ver docs/100 §5 / docs/120.
--
-- Mecanismo: una tabla contador por tenant + el trigger BEFORE INSERT de `orders`
-- (que ya asignaba id_order desde la secuencia global) pasa a tomar el siguiente
-- valor del contador del tenant, con un UPSERT atómico (la fila del contador se
-- bloquea durante el UPDATE → sin colisiones bajo concurrencia).
-- =====================================================================

CREATE TABLE tenant_order_counters (
    tenant_id  TEXT   PRIMARY KEY,
    last_id    BIGINT NOT NULL DEFAULT 0
);

GRANT SELECT, INSERT, UPDATE ON tenant_order_counters TO app_user;

-- Igual que tenants/users: tabla de infraestructura, no aislada por-tenant. RLS
-- activo (Supabase lo fuerza en public) con política abierta solo para app_user;
-- además la función del trigger es SECURITY DEFINER, así que escribe como owner.
ALTER TABLE tenant_order_counters ENABLE ROW LEVEL SECURITY;
CREATE POLICY app_rw_order_counters ON tenant_order_counters
    FOR ALL TO app_user USING (true) WITH CHECK (true);

-- Continuidad: sembrar el contador de cada tenant existente con su id_order máximo
-- actual, para que las órdenes nuevas continúen desde ahí (no se renumeran las
-- viejas). En una DB fresca (orders vacío) no inserta nada.
INSERT INTO tenant_order_counters (tenant_id, last_id)
SELECT tenant_id, MAX(id_order)
FROM orders
WHERE id_order IS NOT NULL
GROUP BY tenant_id
ON CONFLICT (tenant_id) DO UPDATE SET last_id = GREATEST(tenant_order_counters.last_id, EXCLUDED.last_id);

-- El número de orden es único DENTRO de cada negocio (globalmente puede repetirse;
-- las consultas siempre corren bajo RLS con app.tenant_id, así que nunca se cruzan).
ALTER TABLE orders ADD CONSTRAINT uq_orders_tenant_id_order UNIQUE (tenant_id, id_order);

-- Redefine la función del trigger: en vez de nextval() de la secuencia global,
-- incrementa atómicamente el contador del tenant de la fila.
CREATE OR REPLACE FUNCTION set_order_id_order() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    next_id BIGINT;
BEGIN
    IF NEW.id_order IS NULL THEN
        INSERT INTO tenant_order_counters (tenant_id, last_id)
        VALUES (NEW.tenant_id, 1)
        ON CONFLICT (tenant_id)
        DO UPDATE SET last_id = tenant_order_counters.last_id + 1
        RETURNING last_id INTO next_id;
        NEW.id_order := next_id;
    END IF;
    RETURN NEW;
END;
$$;

-- La secuencia global `orders_id_order_seq` (V1) queda sin uso; se conserva para no
-- romper nada que aún la referencie. Puede eliminarse en una migración futura.
