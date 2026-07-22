-- =====================================================================
-- F5 optimización (docs/optimizacion/01 §5): índices para las queries
-- calientes de los pollers (cocina, historial, cierres). Solo aceleran.
-- Numeración ramas f5: V11 meseros, V12 caja, V13 pagos, V14 bajas, V15 borrado.
-- =====================================================================

CREATE INDEX IF NOT EXISTS idx_orders_tenant_created
    ON orders (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_tenant_waiter_created
    ON orders (tenant_id, waiter_id, created_at DESC) WHERE waiter_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tracking_tenant_delivered
    ON order_delivery_tracking (tenant_id, delivered);
CREATE INDEX IF NOT EXISTS idx_orders_tenant_method_created
    ON orders (tenant_id, payment_method, created_at);
