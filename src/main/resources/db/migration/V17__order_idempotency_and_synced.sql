-- V17 — N2/D1 + N2/D2: dedupe de órdenes por idempotencia y estado de sync real.
--
-- D1: el POS creaba DOS órdenes por venta (folios distintos, 0,4–2 s de diferencia).
--     El outbox del cliente se drenaba desde dos lados a la vez (checkout +
--     SyncScheduler) y el servidor no podía deduplicar porque no había ni campo ni
--     índice. El fix de aplicación (dedupe en OrderHandler.createOrUpdateOrder) es
--     un check-then-act: sin este índice único dos POST simultáneos aún podrían
--     colarse. El índice es la garantía dura.
--
-- D2: `orders.synced` se quedaba en false para siempre en el perfil `cloud`, donde
--     este backend ES la nube y el SyncOutboxScheduler ni se instancia
--     (@ConditionalOnProperty sync.cloud.enabled=true). El historial mostraba
--     "No sincronizada" en órdenes que sí estaban en la BD.
--
-- Aditiva y reversible. Rollback en docs/migraciones/V17-idempotencia-y-synced.md.

-- 1) Unicidad por tenant sobre claves no nulas. Parcial: las órdenes viejas y las
--    del panel admin no traen clave y no deben chocar entre sí.
CREATE UNIQUE INDEX IF NOT EXISTS ux_orders_tenant_idempotency
    ON orders (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- 2) Normalización del histórico: en esta base toda fila ES la copia de la nube,
--    así que `synced=false` es un residuo del modelo local-first, no un estado real.
UPDATE orders SET synced = TRUE WHERE synced IS NOT TRUE;
