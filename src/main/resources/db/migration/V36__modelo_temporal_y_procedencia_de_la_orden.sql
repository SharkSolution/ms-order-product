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
-- ⚠️ **EL HASH CUBRE EL CONTENIDO DEL EVENTO, NO SOLO SU IDENTIDAD.**
--
-- La primera versión de esta migración hasheaba solo el sobre —terminal, epoch,
-- seq, tipo, referencia y fecha— y dejaba fuera los importes, con el argumento
-- de que "una orden se puede editar y un hash sobre un campo mutable se
-- invalidaría con cada edición legítima".
--
-- **Ese argumento se cae en cuanto la cadena es sobre EVENTOS.** Un evento del
-- outbox es inmutable por construcción: editar una orden no muta el evento
-- anterior, emite uno nuevo. El hash del evento original sigue cubriendo el
-- importe original, que es exactamente lo que se quiere — dos hechos
-- encadenados, el importe original y el editado, ambos verificables.
--
-- La distinción que sostiene el diseño: **la orden es mutable, el evento no.**
--
-- Con el hash solo sobre el sobre, la cadena demostraba que un terminal emitió
-- N eventos en cierto orden pero NO qué decían: alguien que alterara un importe
-- en el almacenamiento local del POS antes de sincronizar no rompía nada. Para
-- que la serie histórica sea verificable, la integridad del CONTENIDO es el
-- punto entero.
--
-- ── LA FORMA CANÓNICA ─────────────────────────────────────────────────
--
-- La implementación de referencia está en
-- `front_pos_electron/src/app/core/offline/hash-del-evento.ts`, en UNA sola
-- función. Cualquier verificador (Java, SQL) debe seguir esta gramática al pie
-- de la letra, o dará falsos positivos sobre datos correctos — que es peor que
-- no verificar, porque hace desconfiar de lo que está bien.
--
-- ⚠️ NO IMPLEMENTES A PARTIR DE ESTE COMENTARIO SOLO. Hay 15 vectores de oro
-- en `front_pos_electron/src/app/core/offline/hash-vectores-oro.json`, con el
-- evento de entrada, la CADENA CANÓNICA INTERMEDIA y el hash de cada caso.
-- Reprodúcelos todos antes de tocar un dato real. Dos de las reglas de abajo
-- —redondeo y Unicode— se descubrieron precisamente al generarlos, así que un
-- verificador escrito solo de leer prosa tiene alta probabilidad de fallar en
-- las mismas dos cosas.
--
--   canon := "v2" LF
--            "terminal:" <texto>   LF   "epoch:"    <entero>  LF
--            "seq:"      <entero>  LF   "tipo:"     <texto>   LF
--            "ref:"      <texto>   LF   "ocurrido:" <fecha>   LF
--            "medio:"    <texto>   LF
--            "subtotal:" <decimal> LF   "descuento:"<decimal> LF
--            "total:"    <decimal> LF
--            "lineas:"   <entero>  LF
--            { "  " <i> ":" <producto> "|" <cantidad> "|" <unitario> "|" <total> LF }
--            "pagos:"    <entero>  LF
--            { "  " <i> ":" <metodo> "|" <monto> LF }
--            "anterior:" <texto>   LF
--
--   hash := SHA-256(UTF-8(canon)) en hexadecimal MINÚSCULAS
--
-- REGLAS, todas obligatorias:
--
--   · UTF-8. Separador de línea LF (0x0A), nunca CRLF.
--   · <decimal>: punto, SIEMPRE 2 decimales, sin separador de miles, sin
--     notación exponencial, REDONDEO HALF_UP sobre el valor decimal.
--         25000 -> "25000.00"    0 -> "0.00"    1.005 -> "1.01"
--     En Java es exactamente `setScale(2, RoundingMode.HALF_UP)`. Se eligió
--     HALF_UP porque es lo que hace Postgres al guardar en NUMERIC(15,2): así
--     el hash cubre el importe TAL COMO QUEDA ALMACENADO en esta misma tabla.
--     ⚠️ En JavaScript NO vale `toFixed(2)`, que redondea sobre la
--     representación binaria: da "1.00" para 1.005 y "2.67" para 2.675.
--     Un negativo que redondee a cero se normaliza a "0.00": (-0.004) da
--     "-0.00" en JavaScript y `BigDecimal` no tiene cero negativo.
--   · <entero>: sin decimales ni separadores.
--   · <fecha>: ISO-8601 en UTC con TRES decimales de milisegundo,
--     `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`. En Java NO vale `Instant.toString()`,
--     que omite los ceros finales.
--   · AUSENTE y CERO son distintos: un nulo serializa como cadena VACÍA tras
--     los dos puntos. `descuento:` y `descuento:0.00` son hechos diferentes.
--   · Las listas van EN EL ORDEN EN QUE VIAJAN; no se reordenan. El orden en
--     que el cajero marcó los productos es parte del hecho.
--   · Todo texto se NORMALIZA A NFC y DESPUÉS se escapa. Las dos cosas, en ese
--     orden.
--       - NFC (`Normalizer.normalize(s, Form.NFC)` en Java): "Café" llega
--         descompuesto desde iOS (e + U+0301) y compuesto desde Windows
--         (U+00E9). Se ven idénticos y son el MISMO HECHO; sin normalizar dan
--         hashes distintos y el verificador diría "manipulado" sobre una venta
--         correcta cuyo único pecado es haberse tecleado en otro teclado.
--       - Escape —`\`→`\\`, LF→`\n`, CR→`\r`, `|`→`\p`, `:`→`\c`— para que el
--         contenido no pueda fabricar estructura. Sin esto, un nombre de
--         producto con un salto de línea podría simular líneas adicionales.
--
-- QUÉ NO ENTRA, y por qué:
--
--   · `tenant_id`      implícito: un terminal pertenece a un solo negocio (FK +
--                      RLS). Incluirlo ataría el hecho a un valor que el
--                      servidor descarta, porque el negocio lo decide el JWT.
--   · `pager_color`,   enrutamiento operativo; un rastreador se reasigna sin
--     `pager_number`   que el hecho económico cambie.
--   · `table_session_id`, `preparado_en_comanda`  operativos, no económicos.
--   · `status`, `synced`, `id_order`  estado del servidor: no existen todavía
--                      cuando el evento se emite.
--   · `discount_code`  el IMPORTE del descuento es el hecho; el código es cómo
--                      se llegó a él y lo re-resuelve el servidor.
--
-- **Esta migración escribe y puebla la columna. NO construye la herramienta de
-- verificación**: el dato es lo que no se puede retrofitear, y la verificación
-- se puede escribir en cualquier momento a partir de esta definición.
--
-- ── Impacto ───────────────────────────────────────────────────────────
--
-- Ocho columnas nuevas, TODAS nullable y sin default. No toca ninguna fila ni
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
-- ── La discrepancia de importes ──────────────────────────────────────
--
-- El servidor DESCARTA los importes que manda el cliente y usa siempre los
-- suyos (OrderHandler.java:198-204). Es lo correcto: aceptarlos dejaria a un POS
-- manipulado fijar el importe de su propia venta.
--
-- Pero descartar y no comparar desperdicia una senal que ya esta llegando
-- gratis. El cliente manda `total` en el payload; compararlo contra el calculo
-- del servidor cuesta una columna y detecta dos cosas distintas:
--
--   · un POS con el codigo alterado para inflar o desinflar totales;
--   · un desfase de catalogo entre el terminal y el servidor —el POS vendio con
--     un precio viejo— que es un problema real de operacion y hoy es invisible.
--
-- LA DISCREPANCIA ES SENAL, NO AUTORIDAD. El total de la orden lo sigue
-- calculando el servidor; esta columna no participa en ningun calculo.
--
-- Se guarda la DIFERENCIA y no el importe del cliente porque cero es la
-- respuesta esperada y una columna que casi siempre vale cero es barata de
-- indexar y de consultar. El importe del cliente se reconstruye sumando.
--
-- NULL = no habia con que comparar (el cliente no mando total). Distinto de 0,
-- que significa "comparado y coincide". Misma regla de AUSENTE vs CERO que
-- gobierna la forma canonica del hash (hoy v2).
ALTER TABLE orders ADD COLUMN IF NOT EXISTS total_discrepancia NUMERIC(15,2);

COMMENT ON COLUMN orders.total_discrepancia IS
    'total del cliente menos total del servidor. 0 = coinciden. NULL = el cliente '
    'no mando total. El servidor SIEMPRE usa su propio calculo: esto es senal, no '
    'autoridad.';

-- Indice parcial: lo que se busca son las discrepancias, que deberian ser pocas.
CREATE INDEX IF NOT EXISTS idx_orders_discrepancia
    ON orders (tenant_id, created_at DESC)
    WHERE total_discrepancia IS NOT NULL AND total_discrepancia <> 0;

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
    SELECT 8 - count(*) INTO faltan
    FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'orders'
      AND column_name IN ('ocurrido_en','registrado_en','terminal_id',
                          'epoch','seq','hash_anterior','reloj_veredicto',
                          'total_discrepancia');
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
-- DROP INDEX IF EXISTS idx_orders_discrepancia;
-- ALTER TABLE orders DROP COLUMN IF EXISTS total_discrepancia;
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
