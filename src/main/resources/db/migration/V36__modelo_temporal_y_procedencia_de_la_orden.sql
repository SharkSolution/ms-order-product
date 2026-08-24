-- =====================================================================
-- V36 — LA ORDEN DEJA DE TENER UN SOLO RELOJ.
--
-- ⚠️ SOLO EXPAND. **NO SE TOCA `created_at`.** Cinco servicios lo leen y de él
--    dependen los cierres, la analítica y la disponibilidad de rastreadores.
--    Reinterpretarlo rompería producción en silencio. Se añaden columnas al
--    lado; apretar y contraer viene después, como en V18→V21.
--
-- ── El defecto ────────────────────────────────────────────────────────
--
-- Hoy `orders` tiene UN timestamp con dos significados distintos según cómo se
-- desplegó el backend:
--
--   · perfil `cloud` (el desplegado): `OrderHandler.java:180` estampa
--     `LocalDateTime.now(BOGOTA_ZONE)` cuando la orden LLEGA al servidor;
--   · perfil local-first: `PostgresOrderCloudSyncAdapter.java:160` conserva la
--     fecha del DISPOSITIVO al subir.
--
-- Y no hay ninguna columna que diga cuál de los dos es.
--
-- El POS **sí manda** la fecha del dispositivo —
-- `offline-order.service.ts:90` la pone y `offline-order.repository.ts:25` la
-- mete en el payload del evento— pero el DTO del servidor
-- (`OrderRequestRecord.java`) tiene nueve campos y ninguno es una fecha, así que
-- Jackson la descarta sin un solo error.
--
-- **Consecuencia concreta:** una venta hecha a las 13:00 en un local sin
-- internet, que sincroniza a las 19:00, queda registrada como venta de las
-- 19:00. Doce meses de esa serie no sirven para estacionalidad, franja horaria
-- ni detección de fraude, que son análisis por hora.
--
-- ── Por qué esta migración no puede esperar ───────────────────────────
--
-- Todo lo demás se puede retrofitear. La historia no. Una venta registrada hoy
-- con la hora equivocada no se puede corregir mañana: el dato correcto nunca
-- existió.
--
-- ── Las dos fechas ────────────────────────────────────────────────────
--
--   `ocurrido_en`    cuándo pasó, según el reloj del DISPOSITIVO, tal como lo
--                    mandó. No se corrige ni se ajusta.
--   `registrado_en`  cuándo lo supo el SERVIDOR. Siempre reloj del servidor,
--                    NUNCA del cliente.
--
-- La diferencia entre las dos ES el dato: `registrado_en - ocurrido_en` responde
-- "¿cuánto tiempo estuvo este local sin conexión?", que hoy no se puede
-- responder de ninguna manera.
--
-- **`ocurrido_en` es NULLABLE y se queda nulo si el cliente no la manda.** No se
-- rellena con la del servidor: un nulo honesto vale más que un dato inventado
-- que después nadie puede distinguir de uno real. Es la misma regla que aplicó
-- V34 al negarse a registrar un 404 como "conciliado con monto cero".
--
-- Las dos son `TIMESTAMPTZ` y no `TIMESTAMP`, a diferencia del resto del
-- esquema: la regla 3 de LINEAMIENTOS pide "UTC + zona horaria", y una fecha de
-- dispositivo sin zona es inutilizable en cuanto haya un cliente fuera de
-- Colombia. `created_at` se queda como está.
--
-- ── La procedencia ────────────────────────────────────────────────────
--
--   `terminal_id`   qué caja la hizo (FK a `terminals`, V35)
--   `epoch`         vida del terminal; sube cuando pierde su estado local
--   `seq`           secuencia monotónica dentro de ese epoch
--   `hash_anterior` encadenamiento con el evento previo del terminal
--
-- ── LA CADENA VA SOBRE EVENTOS DEL OUTBOX, NO SOBRE ÓRDENES ───────────
--
-- Es la decisión de diseño de esta migración y la que evita rehacer todo en dos
-- meses.
--
-- Hoy el outbox del POS solo maneja un tipo de evento, `order_created`
-- (`offline-order.repository.ts:23`). Pero en cuanto lleguen más —edición,
-- anulación, movimiento de inventario— todos tienen que entrar en la MISMA
-- secuencia, porque lo que se quiere demostrar es "este terminal produjo estos
-- hechos en este orden y no falta ninguno". Una cadena atada a órdenes no puede
-- absorber un evento que no es una orden.
--
-- Así que `seq` y `hash_anterior` son propiedades del EVENTO DEL OUTBOX que
-- produjo esta orden, no de la orden. Cuando en la Fase 3 exista la tabla de
-- eventos canónica, la cadena continúa sin cortarse: los mismos campos, la misma
-- secuencia, otro contenedor.
--
-- ── DEFINICIÓN DEL HASH — no cambiar sin migrar la cadena entera ──────
--
-- `hash_anterior` guarda el hash del evento ANTERIOR de ese terminal en ese
-- epoch. El primer evento de un epoch lleva NULL.
--
-- El hash de un evento se define como:
--
--     SHA-256( terminal_id || '|' || epoch || '|' || seq || '|'
--              || tipo_de_evento || '|' || idempotency_key || '|'
--              || ocurrido_en_en_ISO_8601 || '|' || (hash_anterior ?? '') )
--
-- en hexadecimal minúsculas. Los separadores `|` no son adorno: sin ellos,
-- `seq=1, tipo="2x"` y `seq=12, tipo="x"` producirían la misma entrada.
--
-- Se eligen SOLO campos que ya son inmutables. **El total de la orden NO entra**,
-- y es deliberado: una orden se puede editar (`OrderHandler.java:405`) y un hash
-- sobre un campo mutable se invalidaría solo con cada edición legítima, con lo
-- que la cadena dejaría de distinguir manipulación de operación normal.
--
-- Lo que la cadena demuestra es la INTEGRIDAD DE LA SECUENCIA —que no se
-- borró ni se insertó un evento a posteriori—, no la inmutabilidad del
-- contenido. Para lo segundo está la auditoría de ediciones.
--
-- **Esta migración escribe y puebla la columna. NO construye la herramienta de
-- verificación**: el dato es lo que no se puede retrofitear, la verificación se
-- puede escribir en cualquier momento a partir de esta definición.
--
-- ── Impacto ───────────────────────────────────────────────────────────
--
-- Siete columnas nuevas, TODAS nullable y sin default. No toca ninguna fila ni
-- columna existente. Las órdenes históricas quedan con las seis en NULL, que
-- significa exactamente "de antes de que esto se registrara" y no se confunde
-- con ningún valor real. **No se rellenan hacia atrás: no hay de dónde sacar el
-- dato.**
--
-- El código viejo desplegado contra este esquema sigue funcionando sin cambios.
--
-- ── Cuándo se aprietan ────────────────────────────────────────────────
--
-- `registrado_en` y `terminal_id` pueden pasar a NOT NULL cuando TODOS los
-- escritores las pueblen y no queden filas nuevas en NULL. `ocurrido_en` NO se
-- aprieta nunca: un cliente viejo legítimamente no la manda.
--
-- Consulta para decidirlo, en `docs/CONSULTAS-VIGILANCIA.md`.
--
-- ── NUMERACIÓN ────────────────────────────────────────────────────────
--
-- V33 (rama `fix/rls-tablas-admin`) y V34 (rama `fix/cierre-caja-integridad`)
-- están escritas y sin fusionar. V35 crea `terminals`.
--
-- ⚠️ **Esta migración DEPENDE de V35**: la clave foránea `terminal_id`
-- referencia `terminals`. Aplicar V36 sin V35 falla.
-- =====================================================================

SET lock_timeout = '3s';

-- ── Las dos fechas ───────────────────────────────────────────────────
ALTER TABLE orders ADD COLUMN IF NOT EXISTS ocurrido_en   TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS registrado_en TIMESTAMPTZ;

COMMENT ON COLUMN orders.ocurrido_en IS
    'Cuando ocurrio la venta segun el reloj del DISPOSITIVO, tal como lo mando. '
    'NULL = el cliente no la envio (version vieja). No se rellena con la del '
    'servidor: un nulo honesto vale mas que un dato inventado.';
COMMENT ON COLUMN orders.registrado_en IS
    'Cuando lo supo el SERVIDOR. Siempre reloj del servidor, nunca del cliente.';

-- ── La procedencia ───────────────────────────────────────────────────
ALTER TABLE orders ADD COLUMN IF NOT EXISTS terminal_id   UUID REFERENCES terminals(id);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS epoch         INT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS seq           BIGINT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS hash_anterior TEXT;

COMMENT ON COLUMN orders.terminal_id IS
    'Que caja produjo la venta. FK a terminals (V35).';
COMMENT ON COLUMN orders.epoch IS
    'Vida del terminal. Sube cuando pierde su estado local y reinicia seq. '
    'Un salto aqui explica un reinicio de secuencia; sin el seria '
    'indistinguible de un ataque de repeticion.';
COMMENT ON COLUMN orders.seq IS
    'Secuencia monotonica del evento del outbox que produjo esta orden, dentro '
    'de (terminal_id, epoch). Es del EVENTO, no de la orden.';
-- ── La calidad de la fecha del dispositivo ───────────────────────────
--
-- El POS corre en el equipo del local. Un equipo con la pila de la BIOS agotada
-- tiene el reloj mal, y eso es ordinario en el retail. El servidor NUNCA rechaza
-- una venta por su fecha —seria dejar de facturar por un problema de hardware
-- que el negocio no sabe que tiene— pero si deja constancia de si era creible.
--
-- Enum CERRADO, sin valor "otro" (reglas 9 y 10 de LINEAMIENTOS):
--
--   sin_fecha     el cliente no la mando. Un cliente viejo, no un problema.
--   creible       incluye el atraso NORMAL de una venta que espero en la cola
--                 sin internet. Ese caso NO se marca: marcarlo seria declarar
--                 sospechosa la operacion que este modelo existe para registrar.
--   adelantado    posterior a registrado_en. Fisicamente imposible: nada ocurre
--                 despues de que el servidor lo supo.
--   muy_atrasado  mas atras de lo que cualquier cola justifica (7 dias por
--                 defecto, configurable).
--
-- Marcar y seguir es lo que permite, mas adelante, separar las series limpias de
-- las sucias sin haber perdido ninguna venta por el camino.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS reloj_veredicto TEXT;

COMMENT ON COLUMN orders.reloj_veredicto IS
    'Calidad de ocurrido_en: sin_fecha | creible | adelantado | muy_atrasado. '
    'NUNCA se rechaza una venta por esto; solo se deja constancia.';

ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_reloj_veredicto;
ALTER TABLE orders ADD CONSTRAINT ck_orders_reloj_veredicto
    CHECK (reloj_veredicto IS NULL
           OR reloj_veredicto IN ('sin_fecha', 'creible', 'adelantado', 'muy_atrasado'));

-- Coherencia: si no hay fecha, el veredicto solo puede ser sin_fecha; y si hay
-- fecha, no puede ser sin_fecha. Sostiene en la base que las dos columnas
-- cuenten la misma historia.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_reloj_coherente;
ALTER TABLE orders ADD CONSTRAINT ck_orders_reloj_coherente
    CHECK (reloj_veredicto IS NULL
           OR (ocurrido_en IS NULL     AND reloj_veredicto = 'sin_fecha')
           OR (ocurrido_en IS NOT NULL AND reloj_veredicto <> 'sin_fecha'));

COMMENT ON COLUMN orders.hash_anterior IS
    'SHA-256 hex del evento anterior de ese terminal en ese epoch. NULL en el '
    'primero de cada epoch. Definicion del hash en la cabecera de V36: no '
    'cambiarla sin migrar la cadena entera.';

-- ── Rangos ───────────────────────────────────────────────────────────
-- Epoch y seq empiezan en 1 y solo suben. Un valor fuera de rango es un error
-- de programacion, no un dato.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_epoch;
ALTER TABLE orders ADD CONSTRAINT ck_orders_epoch
    CHECK (epoch IS NULL OR epoch >= 1);

ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_seq;
ALTER TABLE orders ADD CONSTRAINT ck_orders_seq
    CHECK (seq IS NULL OR seq >= 1);

-- El hash es SHA-256 en hexadecimal: 64 caracteres, minusculas. Se valida el
-- formato para que una cadena mal construida se detecte al escribir y no meses
-- despues, cuando alguien intente verificar la cadena.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_hash_anterior;
ALTER TABLE orders ADD CONSTRAINT ck_orders_hash_anterior
    CHECK (hash_anterior IS NULL OR hash_anterior ~ '^[0-9a-f]{64}$');

-- Coherencia: seq, epoch y hash solo tienen sentido con un terminal detras.
-- Sin esto se podrian escribir secuencias huerfanas que no pertenecen a nadie.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_procedencia_coherente;
ALTER TABLE orders ADD CONSTRAINT ck_orders_procedencia_coherente
    CHECK (terminal_id IS NOT NULL
           OR (epoch IS NULL AND seq IS NULL AND hash_anterior IS NULL));

-- ── La garantia dura de la secuencia ─────────────────────────────────
--
-- Un terminal no puede producir dos eventos con el mismo (epoch, seq). Es lo
-- que impide que un reintento mal hecho, o un cliente con el estado a medio
-- restaurar, inserte dos hechos distintos en la misma posicion de la cadena.
--
-- Parcial: las ordenes historicas y las de clientes viejos no traen seq y no
-- deben chocar entre si. Mismo recurso que ux_orders_tenant_idempotency
-- (V17:19-21), y por la misma razon que argumenta V17:5-8 — un chequeo en el
-- codigo seria check-then-act y dos POST simultaneos lo atravesarian. El indice
-- es la garantia dura.
--
-- Lleva tenant_id por delante para que sirva ademas de indice de consulta bajo
-- RLS, que siempre filtra por negocio.
CREATE UNIQUE INDEX IF NOT EXISTS ux_orders_terminal_epoch_seq
    ON orders (tenant_id, terminal_id, epoch, seq)
    WHERE terminal_id IS NOT NULL AND seq IS NOT NULL;

-- Las dos consultas que este modelo viene a habilitar:
--   · ventas por franja horaria REAL (por ocurrido_en, no por created_at)
--   · cuanto tardo en llegar cada venta (registrado_en - ocurrido_en)
CREATE INDEX IF NOT EXISTS idx_orders_ocurrido_en
    ON orders (tenant_id, ocurrido_en)
    WHERE ocurrido_en IS NOT NULL;

-- Regla dura desde V22: permiso explicito del rol de aplicacion.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE orders TO app_user;

-- ---------------------------------------------------------------------
-- Verificacion: las seis columnas existen y `created_at` sigue intacta.
-- Lo segundo importa mas que lo primero: esta migracion se define por lo que
-- NO toca.
-- ---------------------------------------------------------------------
DO $verificar$
DECLARE
    faltan INT;
    tipo_created TEXT;
BEGIN
    SELECT 7 - count(*) INTO faltan
    FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'orders'
      AND column_name IN ('ocurrido_en','registrado_en','terminal_id',
                          'epoch','seq','hash_anterior','reloj_veredicto');
    IF faltan <> 0 THEN
        RAISE EXCEPTION 'Faltan % columnas del modelo temporal en orders', faltan;
    END IF;

    SELECT data_type INTO tipo_created
    FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'orders' AND column_name = 'created_at';
    IF tipo_created IS DISTINCT FROM 'timestamp without time zone' THEN
        RAISE EXCEPTION 'created_at cambio de tipo (%). Esta migracion NO debe tocarla', tipo_created;
    END IF;
END $verificar$;


-- =====================================================================
-- DOWN — rollback explicito.
--
-- Aditiva pura: quitar las columnas devuelve el esquema exacto de antes y NO
-- toca `created_at`, asi que el cuadre historico y la analitica siguen igual.
--
-- Se pierden las fechas de dispositivo y la procedencia de las ordenes
-- registradas desde que se aplico. Eso NO se puede recuperar: son datos que solo
-- existian aqui.
--
-- ⚠️ Aplicar el DOWN con el codigo nuevo desplegado ROMPE la creacion de ordenes
-- (Hibernate fallaria al mapear Order). Misma leccion de V21:15-16: primero se
-- revierte el codigo, luego el esquema.
--
-- DROP INDEX IF EXISTS idx_orders_ocurrido_en;
-- DROP INDEX IF EXISTS ux_orders_terminal_epoch_seq;
-- ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_reloj_coherente;
-- ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_reloj_veredicto;
-- ALTER TABLE orders DROP COLUMN IF EXISTS reloj_veredicto;
-- ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_procedencia_coherente;
-- ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_hash_anterior;
-- ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_seq;
-- ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_epoch;
-- ALTER TABLE orders DROP COLUMN IF EXISTS hash_anterior;
-- ALTER TABLE orders DROP COLUMN IF EXISTS seq;
-- ALTER TABLE orders DROP COLUMN IF EXISTS epoch;
-- ALTER TABLE orders DROP COLUMN IF EXISTS terminal_id;
-- ALTER TABLE orders DROP COLUMN IF EXISTS registrado_en;
-- ALTER TABLE orders DROP COLUMN IF EXISTS ocurrido_en;
-- =====================================================================
