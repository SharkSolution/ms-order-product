-- =====================================================================
-- V31 — SACAR DEL CÓDIGO LO QUE ES DE UN NEGOCIO EN PARTICULAR.
--
-- SureSell se vende como SaaS multi-tenant, pero tres cosas estaban modeladas
-- sobre cómo opera un cliente concreto:
--
--   1. El ORDEN de las categorías del menú, escrito a mano en la app de
--      meseros: hamburguesas primero, después carnes al barril, después
--      jugos. Para cualquier otro negocio ese orden no significa nada — y
--      peor: como la consulta no tenía ORDER BY, el resto de las categorías
--      salía en un orden NO DETERMINISTA, que podía cambiar entre llamadas.
--
--   2. El ÍCONO de cada categoría, deducido de su nombre con una lista de
--      palabras ('arepa', 'barril', 'hamburguesa'…). Un negocio con otra
--      carta se queda sin íconos.
--
--   3. Los GRUPOS DE RASTREADORES por defecto (Amarillo y Azul, 16 de cada
--      uno). Eran los de un cliente y se le entregaban a TODO negocio nuevo.
--
-- La regla que se aplica: **el código genérico, la preferencia en datos.**
--
-- ADITIVA. Dos columnas nuevas, las dos NULLABLE y sin default, más un
-- relleno que CONSERVA lo que cada negocio ve hoy.
--
-- Rollback:
--   ALTER TABLE menu_categories DROP COLUMN IF EXISTS display_order;
--   ALTER TABLE menu_categories DROP COLUMN IF EXISTS icon;
--   -- las filas de tenant_pager_groups se dejan: son la config real del negocio
-- =====================================================================

-- 1 y 2 · El orden y el ícono pasan a ser DATO del negocio ---------------
--
-- Nulos = "no configurado". La consulta ordena por `display_order` y deja los
-- nulos al final, por nombre: así el orden es DETERMINISTA desde el primer día
-- aunque nadie configure nada, que es más de lo que había antes.

ALTER TABLE menu_categories ADD COLUMN IF NOT EXISTS display_order INTEGER;
ALTER TABLE menu_categories ADD COLUMN IF NOT EXISTS icon TEXT;

COMMENT ON COLUMN menu_categories.display_order IS
    'Orden en que el negocio quiere ver sus categorías. NULL = al final, por nombre.';
COMMENT ON COLUMN menu_categories.icon IS
    'Emoji de la categoría, elegido por el negocio. NULL = ícono neutro.';

CREATE INDEX IF NOT EXISTS idx_menu_categories_orden
    ON menu_categories (tenant_id, display_order);

-- 3 · Los rastreadores dejan de tener un default escrito en el código ----
--
-- Antes, un negocio SIN filas recibía "Amarillo/Azul, 16 de cada uno" desde el
-- servicio. Eso se va a quitar del código, así que primero hay que GUARDAR esa
-- configuración como dato para los negocios que hoy dependen de ella: si no,
-- mañana se quedarían sin rastreadores y el POS no podría entregar un pedido.
--
-- Solo toca a los que NO tienen nada configurado. Al que ya eligió los suyos no
-- se le cambia nada.
--
-- El bucle fija `app.tenant_id` en cada vuelta porque la tabla tiene RLS
-- forzada: sin eso el INSERT se rechaza aunque lo corra el dueño.

DO $$
DECLARE
    t TEXT;
BEGIN
    FOR t IN SELECT id FROM tenants LOOP
        PERFORM set_config('app.tenant_id', t, true);

        IF NOT EXISTS (SELECT 1 FROM tenant_pager_groups WHERE tenant_id = t) THEN
            INSERT INTO tenant_pager_groups (tenant_id, code, label, color, quantity, sort_order)
            VALUES (t, 'AMARILLO', 'Amarillo', '#eab308', 16, 0),
                   (t, 'AZUL',     'Azul',     '#3b82f6', 16, 1);
        END IF;
    END LOOP;
END $$;

-- Regla dura del proyecto: permiso explícito del rol de aplicación.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE menu_categories TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE tenant_pager_groups TO app_user;
