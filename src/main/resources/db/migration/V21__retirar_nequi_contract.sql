-- V21 — N2/6.6: retirar Nequi (paso CONTRACT, **destructivo**).
--
-- Elimina las columnas de Nequi de daily_closures. Es el paso final del
-- expand-contract iniciado en V18, que ya migró todos los datos a QR:
--   · orders.payment_method = 'NEQUI'  → 'QR'   (9 órdenes, $309.000)
--   · order_payments.method = 'NEQUI'  → 'QR'
--   · daily_closures: los montos de Nequi se sumaron a los de QR y las
--     columnas quedaron en 0.
--
-- Verificado en prod ANTES de aplicar esto:
--   ordenes_nequi = 0 · residuo de nequi en cierres = 0.00 · flyway = 20
--
-- El código ya NO referencia estas columnas: se limpiaron la entidad
-- DailyClosure, ClosureResponse, ClosurePreviewResponse y el export a Excel.
-- Aplicar esto con el código viejo desplegado ROMPERÍA el cierre de caja
-- (Hibernate fallaría al mapear la entidad).
--
-- Snapshot previo: esquema backup_20260725 (creado 2026-07-25).
-- Rollback completo en docs/migraciones/V18-V21-retirar-nequi.md.
--
-- Ojo: V19 y V20 — V19 quedó SIN USAR a propósito (V20 ya estaba aplicada
-- cuando se planificó este paso, y un V19 posterior lo rechazaría Flyway por
-- out-of-order). Por eso el contract es V21.

ALTER TABLE daily_closures DROP COLUMN IF EXISTS total_expected_nequi;
ALTER TABLE daily_closures DROP COLUMN IF EXISTS total_counted_nequi;
ALTER TABLE daily_closures DROP COLUMN IF EXISTS difference_nequi;
