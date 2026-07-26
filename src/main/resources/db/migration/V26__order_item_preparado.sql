-- V26 — Distinguir en cocina lo YA PREPARADO de lo recién agregado (N3/#1).
--
-- Con las rondas acumuladas en la misma orden (N3/#8), la cocina recibe la
-- orden COMPLETA cada vez que la mesa pide algo más. Sin una marca, el cocinero
-- no puede saber qué es nuevo y qué ya despachó — y o repite platos o se salta
-- los nuevos.
--
-- Se marca por ÍTEM y no por orden: dentro de una misma mesa conviven platos
-- entregados y platos pendientes.
--
-- Aditiva y reversible. Rollback en docs/migraciones/V23-V25-modo-restaurante.md.

ALTER TABLE order_item ADD COLUMN IF NOT EXISTS created_at  timestamp;
ALTER TABLE order_item ADD COLUMN IF NOT EXISTS prepared_at timestamp;

-- Los ítems existentes heredan la fecha de su orden (mejor que now(): conserva
-- el orden real de llegada) y se dan por preparados: son historia.
UPDATE order_item i
SET    created_at  = COALESCE(i.created_at, o.created_at),
       prepared_at = COALESCE(i.prepared_at, o.created_at)
FROM   orders o
WHERE  o.id_order = i.order_id AND (i.created_at IS NULL OR i.prepared_at IS NULL);

CREATE INDEX IF NOT EXISTS idx_order_item_pendientes
    ON order_item (order_id) WHERE prepared_at IS NULL;
