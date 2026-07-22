-- =====================================================================
-- F5 cluster meseros (corrida nocturna 2026-07-22, PLAN.md A7/A8).
-- Base de caja POR DEFECTO por mesero: el admin la asigna y la app la sugiere
-- al abrir turno (el turno sigue guardando su propia base en waiter_sessions).
-- Nota de numeración: V12=caja (gastos), V13=pagos, V14=bajas (otras ramas f5).
-- =====================================================================

ALTER TABLE waiters ADD COLUMN default_cash_base NUMERIC(15,2);
