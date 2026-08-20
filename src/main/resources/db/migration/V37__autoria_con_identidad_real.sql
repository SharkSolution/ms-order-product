-- =====================================================================
-- V37 — QUIÉN VENDIÓ Y QUIÉN EDITÓ, CON IDENTIDAD REAL.
--
-- ── El hueco ──────────────────────────────────────────────────────────
--
-- La regla 4 de LINEAMIENTOS_DESARROLLO_DATA_FIRST es explícita:
--
--   "Usuario responsable en cada evento. Sin excepción, ni en procesos
--    automáticos — sin atribución no hay detección por cajero ni
--    responsabilidad."
--
-- Y hoy:
--
--   · `orders` NO registra quién vendió. Tiene `waiter_id` (V10:37), pero es
--     NULLABLE y solo lo puebla la app de meseros: una venta hecha en el POS de
--     caja no tiene autor de ningún tipo.
--   · `order_edit_history` NO registra quién editó. Guarda qué cambió y cuándo
--     (V2:20-32) pero no quién — que es la mitad que importa para el antifraude.
--   · Donde sí hay autoría, es `TEXT` con un nombre: `created_by`, `deleted_by`,
--     `user_name`. **No hay una sola FK a `users` en todo el dominio
--     transaccional.** Dos empleados que se llamen igual, o un cambio de nombre,
--     y la trazabilidad se rompe.
--
-- Sin esto no se puede responder "¿qué cajero anula más ventas?", que es
-- literalmente la variable 8 del catálogo de datos objetivo ("Anulaciones —
-- evento tipificado con usuario y motivo — DETECTOR DE ROBO INTERNO").
--
-- ── Qué se añade ──────────────────────────────────────────────────────
--
--   `orders.created_by`              BIGINT → users(id)
--   `order_edit_history.edited_by`   BIGINT → users(id)
--
-- Nullable en esta migración. Se aprietan cuando todos los escritores las
-- pueblen, no antes: expand/contract, igual que V18→V21.
--
-- ── LAS COLUMNAS VIEJAS NO SE TOCAN ───────────────────────────────────
--
-- `created_by TEXT` (V12:14, V14:17, V29:51), `deleted_by TEXT` (V15:10),
-- `user_name` (V2:37) y `registered_by` (V2:74) **se quedan exactamente como
-- están**. Contraer viene después, cuando haya historia poblada con la que
-- comparar; hacerlo ahora dejaría sin autor el histórico entero, que es el único
-- que existe.
--
-- Ojo con el nombre: `orders.created_by` es NUEVO y es un BIGINT. El
-- `created_by TEXT` de `register_expenses`, `food_waste` y
-- `table_session_splits` es otro campo, en otras tablas, y no cambia.
--
-- ── EL ACTOR SISTEMA ──────────────────────────────────────────────────
--
-- La regla 4 dice "sin excepción, ni en procesos automáticos". Los tres
-- schedulers (`SyncOutboxScheduler`, `OrderTrackingSyncScheduler`,
-- `CatalogSyncScheduler`) no dejan autoría de ningún tipo.
--
-- **Decisión: un usuario de sistema POR NEGOCIO, en la propia tabla `users`.**
--
-- Se evaluaron dos alternativas y se descartaron:
--
--   a) `created_by` nullable + una columna `actor_tipo` ('humano' | 'sistema').
--      Rechazada: obliga a mirar dos columnas para saber quién hizo algo, y deja
--      la puerta abierta a filas sin autor "porque es automático". La regla 4
--      dice exactamente lo contrario.
--
--   b) Un único usuario de sistema global, compartido entre negocios.
--      Imposible sin retorcer el esquema: `users.tenant_id` es NOT NULL con FK a
--      `tenants`. Y además rompería el aislamiento — un `created_by` que
--      apuntara a una fila de otro negocio.
--
-- Con un usuario por negocio, `created_by` es SIEMPRE una FK válida y la
-- pregunta "¿quién hizo esto?" siempre tiene respuesta, sin ramas.
--
-- El usuario de sistema:
--   · `role = 'sistema'`, un valor que ningún flujo de login concede;
--   · `status = 'disabled'`, así que `AuthService` lo rechaza aunque alguien
--     acertara la contraseña;
--   · `password_hash = '!'`, que NO es un hash BCrypt válido — BCrypt siempre
--     empieza por `$2`, así que ninguna contraseña puede coincidir con él.
--     No es un secreto y no hay nada que rotar.
--
-- Se crea aquí para los negocios que ya existen, y `UsuarioDeSistema` lo crea
-- bajo demanda para los que se den de alta después.
--
-- ── Impacto ───────────────────────────────────────────────────────────
--
-- Dos columnas nullable y una fila en `users` por cada negocio existente. No
-- toca ninguna fila de negocio ni ninguna columna existente. El código viejo
-- desplegado contra este esquema sigue funcionando.
--
-- ── NUMERACIÓN ────────────────────────────────────────────────────────
--
-- V33 (`fix/rls-tablas-admin`) y V34 (`fix/cierre-caja-integridad`) están
-- escritas y sin fusionar. V35 crea `terminals`, V36 el modelo temporal. Esta es
-- V37 y es INDEPENDIENTE de V35/V36: no comparte tablas con ellas y puede
-- aplicarse en cualquier orden respecto a esas dos.
-- =====================================================================

SET lock_timeout = '3s';

-- ── Las dos columnas nuevas ──────────────────────────────────────────
ALTER TABLE orders             ADD COLUMN IF NOT EXISTS created_by BIGINT REFERENCES users(id);
ALTER TABLE order_edit_history ADD COLUMN IF NOT EXISTS edited_by  BIGINT REFERENCES users(id);

COMMENT ON COLUMN orders.created_by IS
    'Quien registro la venta. FK a users(id), no un nombre suelto: un cambio de '
    'nombre o dos empleados homonimos no pueden romper la trazabilidad. '
    'NULL = venta anterior a V37.';
COMMENT ON COLUMN order_edit_history.edited_by IS
    'Quien edito la orden. FK a users(id). Antes solo se guardaba QUE cambio y '
    'cuando, no quien — que es la mitad que importa para el antifraude.';

-- Las dos preguntas que esto viene a habilitar: "que vendio este cajero" y
-- "quien edita mas ordenes". Parciales: el historico no tiene autor y no debe
-- ocupar indice.
CREATE INDEX IF NOT EXISTS idx_orders_created_by
    ON orders (tenant_id, created_by, created_at DESC)
    WHERE created_by IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_order_edit_history_edited_by
    ON order_edit_history (tenant_id, edited_by)
    WHERE edited_by IS NOT NULL;

-- ── El usuario de sistema de cada negocio ────────────────────────────
--
-- Uno por negocio existente. El email lleva el dominio reservado `.invalid`
-- (RFC 2606): es sintacticamente valido, nunca resuelve y nadie puede
-- registrarlo, asi que no colisiona con el correo de una persona real.
INSERT INTO users (email, password_hash, tenant_id, role, status)
SELECT 'sistema@' || t.id || '.invalid',
       '!',            -- imposible como BCrypt: siempre empieza por $2
       t.id,
       'sistema',
       'disabled'
FROM   tenants t
ON CONFLICT (email) DO NOTHING;

-- ---------------------------------------------------------------------
-- Verificacion: cada negocio tiene su actor sistema, y ninguno puede entrar.
-- ---------------------------------------------------------------------
DO $verificar$
DECLARE
    sin_actor INT;
    habilitados INT;
BEGIN
    SELECT count(*) INTO sin_actor
    FROM tenants t
    WHERE NOT EXISTS (SELECT 1 FROM users u
                      WHERE u.tenant_id = t.id AND u.role = 'sistema');
    IF sin_actor > 0 THEN
        RAISE EXCEPTION 'Quedaron % negocios sin usuario de sistema', sin_actor;
    END IF;

    SELECT count(*) INTO habilitados
    FROM users WHERE role = 'sistema' AND status <> 'disabled';
    IF habilitados > 0 THEN
        RAISE EXCEPTION '% usuarios de sistema quedaron habilitados para entrar', habilitados;
    END IF;
END $verificar$;


-- =====================================================================
-- DOWN — rollback explicito.
--
-- Aditiva: quitar las columnas devuelve el esquema anterior. Se pierde la
-- autoria de lo registrado desde que se aplico, que no se puede recuperar.
--
-- Los usuarios de sistema se borran DESPUES de las columnas, o la FK lo impide.
--
-- ⚠️ Aplicar el DOWN con el codigo nuevo desplegado rompe la creacion de ordenes
-- (Hibernate fallaria al mapear Order). Primero se revierte el codigo, luego el
-- esquema. Misma leccion de V21:15-16.
--
-- DROP INDEX IF EXISTS idx_order_edit_history_edited_by;
-- DROP INDEX IF EXISTS idx_orders_created_by;
-- ALTER TABLE order_edit_history DROP COLUMN IF EXISTS edited_by;
-- ALTER TABLE orders             DROP COLUMN IF EXISTS created_by;
-- DELETE FROM users WHERE role = 'sistema' AND password_hash = '!';
-- =====================================================================
