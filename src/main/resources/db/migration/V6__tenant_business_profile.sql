-- =====================================================================
-- Perfil del negocio en `tenants`: datos que se imprimen en el ticket.
--
-- Antes estos valores estaban HARDCODEADOS en el frontend (printer.service:
-- "SHARK BURGER", NIT, dirección, teléfono, pie), así que CUALQUIER negocio
-- imprimía los datos de Shark Burger. Ahora salen del tenant. Ver docs/120.
--
-- La numeración/resolución DIAN fiscal NO va aquí (es F2, ver 30-offline-y-sync.md).
-- =====================================================================

ALTER TABLE tenants ADD COLUMN nit           TEXT;
ALTER TABLE tenants ADD COLUMN address       TEXT;
ALTER TABLE tenants ADD COLUMN phone         TEXT;
ALTER TABLE tenants ADD COLUMN ticket_footer TEXT;

-- Semilla del tenant demo, para que el ticket de staging siga coherente.
UPDATE tenants
SET nit = '902.012.746-1',
    address = 'Calle 26 # 53-43',
    phone = '3202672388',
    ticket_footer = '¡Gracias por su compra!'
WHERE id = 'shark-burger';
