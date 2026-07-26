-- V18 — N2/6.6: retirar Nequi (paso EXPAND, no destructivo).
--
-- Nequi deja de existir como medio de pago; quedan Efectivo, Tarjeta, QR y Mixto.
-- Este paso SOLO migra datos y NO elimina nada: las columnas
-- total_expected_nequi / total_counted_nequi / difference_nequi siguen ahí para
-- que un rollback del código no rompa. El DROP va en V19 (paso CONTRACT).
--
-- Criterio: Nequi y QR son ambos transferencia digital, así que los montos de
-- Nequi se PLIEGAN dentro de QR en vez de perderse — el cuadre histórico se
-- mantiene al centavo.
--
-- Rollback en docs/migraciones/V18-V21-retirar-nequi.md.

-- 1) Órdenes: NEQUI -> QR. (En prod eran 9 órdenes por $309.000.)
UPDATE orders
SET    payment_method = 'QR'
WHERE  payment_method = 'NEQUI';

-- 2) Splits de multipago: NEQUI -> QR.
UPDATE order_payments
SET    method = 'QR'
WHERE  method = 'NEQUI';

-- 3) Cierres: los montos de Nequi se suman a los de QR y se dejan las columnas
--    de Nequi en 0. Así los totales por cierre no cambian ni un peso.
UPDATE daily_closures
SET    total_expected_qr    = COALESCE(total_expected_qr, 0)    + COALESCE(total_expected_nequi, 0),
       total_counted_qr     = COALESCE(total_counted_qr, 0)     + COALESCE(total_counted_nequi, 0),
       difference_qr        = COALESCE(difference_qr, 0)        + COALESCE(difference_nequi, 0),
       total_expected_nequi = 0,
       total_counted_nequi  = 0,
       difference_nequi     = 0
WHERE  COALESCE(total_expected_nequi, 0) <> 0
   OR  COALESCE(total_counted_nequi, 0)  <> 0
   OR  COALESCE(difference_nequi, 0)     <> 0;
