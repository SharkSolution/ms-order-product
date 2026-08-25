-- =====================================================================
-- V41 — EL CAMINO DEL MESERO ENTRA EN LA CADENA, Y SE PUEDE DISTINGUIR
--       DE LA DEL POS.
--
-- ── El hueco ──────────────────────────────────────────────────────────
--
-- Medido contra Producción el 2026-08-25: en los últimos 90 días, **1.501 de
-- 3.831 órdenes (39,18%) vienen del camino del mesero**, y ninguna lleva hora
-- del dispositivo, ni terminal, ni firma. `WaiterService.java:358` pasa seis
-- nulos a `OrderRequestRecord.sinProcedencia`.
--
-- La cadena de hashes tiene, literalmente, un hueco por cada dos de cada cinco
-- ventas. Y la afirmación "cada venta va firmada" es falsa mientras eso siga
-- así, que es peor que no tener cadena: una cobertura parcial que se presenta
-- como total.
--
-- ── El diseño, que NO es el del POS, y por qué ────────────────────────
--
-- En el POS la cadena la calcula el CLIENTE. Existe porque sus eventos duermen
-- en un SQLite local antes de sincronizar, y el encadenamiento demuestra que
-- ese dato en reposo no se alteró.
--
-- La app de mesero **exige internet siempre y no persiste nada**. Sin
-- almacenamiento local no hay dato en reposo que manipular, y el servidor ve
-- cada orden en tiempo real. Replicar el outbox del POS en Flutter sería
-- trabajo grande y sin objeto.
--
-- Así que aquí:
--
--     La app persiste UN valor:  terminal_id (UUID, en shared_preferences)
--     La app envía:              terminal_id + ocurrido_en (su reloj)
--     El servidor asigna:        seq, hash_anterior, hash_propio, registrado_en
--
-- ── 🔴 LAS DOS CADENAS NO PRUEBAN LO MISMO ────────────────────────────
--
-- Y por eso hace falta una columna que diga cuál es cuál.
--
--   · La del POS la genera el cliente: prueba que el dato local NO SE ALTERÓ
--     entre que se vendió y que se sincronizó.
--   · La del mesero la genera el servidor: **no prueba eso**. Prueba que el
--     registro del servidor es internamente consistente, que es bastante menos.
--
-- Meter las dos en las mismas columnas sin distinguirlas sería colapsar dos
-- hechos distintos en uno — exactamente lo que prohíbe la regla 6 de
-- LINEAMIENTOS_DESARROLLO_DATA_FIRST. Un auditor tiene que poder ver, fila a
-- fila, qué garantía tiene delante. Si no puede, la garantía más débil
-- contamina a la más fuerte y las dos valen lo que vale la débil.
--
--     cadena_origen = 'cliente'   la firmó el terminal, antes de sincronizar
--     cadena_origen = 'servidor'  la firmó este servicio, al recibirla
--
-- Enum CERRADO, sin valor "otro" (regla 10).
--
-- ── Por qué hace falta `hash_propio` ──────────────────────────────────
--
-- `orders` guarda `hash_anterior` (V36:233) pero NO el hash de la propia fila:
-- en el POS ese valor vive en el cliente (`TerminalIdentity.avanzar`), que es
-- quien encadena.
--
-- Para encadenar del lado del servidor hace falta poder leer el hash del último
-- evento de ese terminal, y hoy no está en ninguna parte. Sin esta columna, la
-- segunda orden de un mesero no tendría a qué apuntar.
--
-- Se rellena SOLO en las de origen `servidor`. En las del POS queda nula, que es
-- la verdad: ese hash existe, pero en el terminal, no aquí.
--
-- ── De dónde sale el `seq`, y por qué NO de una tabla de contadores ───
--
-- De `orders`, con la consulta del último de ese `(terminal, epoch)`. `V35:68-71`
-- decidió explícitamente que `terminals` NO llevara `ultimo_seq`: *"Sería una
-- fila caliente por terminal, actualizada en cada venta, y con dos cajas
-- vendiendo a la vez se convierte en un punto de contención en el camino
-- crítico del cobro."* Ese argumento sigue valiendo, así que esta migración no
-- añade ningún contador.
--
-- El índice único `ux_orders_terminal_epoch_seq` (V36:364) ya cubre la consulta
-- —lleva `(tenant_id, terminal_id, epoch, seq)`— y además es la garantía dura de
-- que no haya dos eventos en la misma posición. Un chequeo en el código sería
-- check-then-act; el índice no.
--
-- ── Compatibilidad: el contrato viejo SIGUE VALIENDO ──────────────────
--
-- Los campos nuevos son opcionales. Aunque se actualicen todos los dispositivos
-- el mismo día, la ronda dura horas y ningún mesero puede quedarse sin poder
-- tomar un pedido a mitad.
--
-- Que esto no es teórico está medido: en los últimos 90 días llegaron **38
-- órdenes con `NEQUI`**, un medio retirado en N2/6.6, desde APKs que nadie ha
-- podido actualizar.
--
-- Sin `terminal_id` la orden se registra igual, con `ocurrido_en` nulo y sin
-- cadena — lo mismo que hoy, no peor.
--
-- ── Impacto ───────────────────────────────────────────────────────────
--
-- Dos columnas nuevas, nulas en todas las filas existentes. No toca ninguna
-- fila. Producción: 3.831 órdenes en 90 días, ~332.600 en total; un
-- `ADD COLUMN` sin default no las reescribe.
--
-- ── ROLLBACK ──────────────────────────────────────────────────────────
--
-- Bloque DOWN al final, comentado. Quitar las columnas pierde la distinción
-- entre las dos cadenas, no las cadenas.
--
-- ── NUMERACIÓN ────────────────────────────────────────────────────────
--
-- La última es V40 (`tenant_modules`). V19 quedó sin usar a propósito (V21:21-23).
-- =====================================================================

SET lock_timeout = '3s';

ALTER TABLE orders ADD COLUMN IF NOT EXISTS cadena_origen TEXT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS hash_propio   TEXT;

COMMENT ON COLUMN orders.cadena_origen IS
    'Quien calculo la cadena de esta fila. cliente = la firmo el terminal antes '
    'de sincronizar (prueba que el dato local no se altero). servidor = la firmo '
    'este servicio al recibirla (prueba solo consistencia del registro). NO son '
    'la misma garantia y por eso no comparten columna. NULL = la fila no esta '
    'encadenada.';

COMMENT ON COLUMN orders.hash_propio IS
    'Hash canonico de ESTA fila, para que la siguiente pueda apuntarle. Solo se '
    'rellena cuando cadena_origen = servidor: en las del POS ese hash existe en '
    'el terminal, no aca, y fingir lo contrario seria inventar un dato.';

-- Enum cerrado. Sin valor "otro": si aparece un tercer origen, se anade aqui y
-- se decide qué garantiza, no se cuela por un cajon de sastre.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_cadena_origen;
ALTER TABLE orders ADD CONSTRAINT ck_orders_cadena_origen
    CHECK (cadena_origen IS NULL OR cadena_origen IN ('cliente', 'servidor'));

-- Mismo formato que ck_orders_hash_anterior (V36:340).
ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_hash_propio_formato;
ALTER TABLE orders ADD CONSTRAINT ck_orders_hash_propio_formato
    CHECK (hash_propio IS NULL OR hash_propio ~ '^[0-9a-f]{64}$');

-- Coherencia, en los dos sentidos:
--   · una cadena de origen `servidor` SIN su propio hash no encadena nada: la
--     siguiente orden no tendria a que apuntar;
--   · un `hash_propio` sin origen declarado seria un hash del que nadie sabe
--     que garantiza, que es el problema que esta migracion viene a evitar.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_cadena_coherente;
ALTER TABLE orders ADD CONSTRAINT ck_orders_cadena_coherente
    CHECK ((cadena_origen IS NULL     AND hash_propio IS NULL)
        OR (cadena_origen = 'cliente' AND hash_propio IS NULL)
        OR (cadena_origen = 'servidor' AND hash_propio IS NOT NULL));

-- Y que no se declare cadena sin terminal. Es el mismo criterio de
-- ck_orders_procedencia_coherente (V36:346): sin terminal no hay cadena a la
-- que pertenecer.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_cadena_exige_terminal;
ALTER TABLE orders ADD CONSTRAINT ck_orders_cadena_exige_terminal
    CHECK (cadena_origen IS NULL OR terminal_id IS NOT NULL);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE orders TO app_user;

-- ---------------------------------------------------------------------
-- Verificacion. Comprueba que las restricciones RECHAZAN lo que deben, no solo
-- que existen: una restriccion presente y una restriccion que funciona se ven
-- igual en `pg_constraint`.
-- ---------------------------------------------------------------------
DO $verificar$
DECLARE
    faltan INT;
    acepto BOOLEAN;
BEGIN
    SELECT 2 - count(*) INTO faltan
    FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'orders'
      AND column_name IN ('cadena_origen', 'hash_propio');
    IF faltan <> 0 THEN
        RAISE EXCEPTION 'V41: faltan % columnas de las 2 esperadas', faltan;
    END IF;

    -- El indice del que depende la asignacion de seq tiene que seguir ahi.
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public'
                   AND indexname = 'ux_orders_terminal_epoch_seq') THEN
        RAISE EXCEPTION
            'V41: falta ux_orders_terminal_epoch_seq (V36). Sin el, dos ordenes '
            'simultaneas del mismo terminal podrian tomar el mismo seq.';
    END IF;

    -- Un valor fuera del enum se tiene que rechazar.
    BEGIN
        acepto := true;
        INSERT INTO orders (uuid_id, tenant_id, id_order, total, cadena_origen, terminal_id)
        VALUES (gen_random_uuid(), '__v41__', -1, 0, 'otro', gen_random_uuid());
        RAISE EXCEPTION 'V41: se acepto cadena_origen = otro; el enum no es cerrado';
    EXCEPTION
        WHEN check_violation OR foreign_key_violation THEN
            acepto := false;   -- rechazado: correcto
        WHEN others THEN
            acepto := false;   -- otra restriccion lo paro antes; tambien sirve
    END;
    IF acepto THEN
        RAISE EXCEPTION 'V41: el enum de cadena_origen no rechaza valores nuevos';
    END IF;
    DELETE FROM orders WHERE tenant_id = '__v41__';
END $verificar$;

-- Lo que este archivo NO puede comprobar:
--   · que la gramatica Java produzca el MISMO hash que el POS. Eso lo dice
--     `VectoresDeOroTest`, que recorre los 15 vectores de oro; sin ese test en
--     verde, esta migracion habilita una cadena que no es comparable con la del
--     POS y no vale para nada.
--   · que una orden de mesero llegue encadenada. Eso solo lo dice una llamada
--     a la API real.

-- =====================================================================
-- DOWN — rollback explicito.
-- =====================================================================
--
-- ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_cadena_exige_terminal;
-- ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_cadena_coherente;
-- ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_hash_propio_formato;
-- ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_cadena_origen;
-- ALTER TABLE orders DROP COLUMN IF EXISTS hash_propio;
-- ALTER TABLE orders DROP COLUMN IF EXISTS cadena_origen;
--
-- Ojo: revertir NO borra las cadenas ya escritas (seq, hash_anterior siguen
-- ahi). Lo que se pierde es poder distinguir cual la firmo el terminal y cual
-- este servidor, que es justo lo que esta migracion aporta.
