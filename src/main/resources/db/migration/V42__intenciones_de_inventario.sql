-- =====================================================================
-- V42 -- La bandeja de intenciones de inventario.
--
-- QUE RESUELVE
--
-- Vender tiene que descontar insumo, y el inventario vive en OTRO servicio
-- (ms-smart-inventory). Hay tres formas de conectarlos y solo una es
-- aceptable:
--
--   1. Llamada HTTP sincrona dentro de la venta. DESCARTADA: o se cae la venta,
--      o se traga el error. Es exactamente lo que paso con
--      ExecuteDailyClosureUseCase llamando a /qr-payments sin JWT: 401 durante
--      TRES SEMANAS y los cierres cuadrando con el valor manual, sin que
--      quedara rastro en ninguna parte.
--   2. Que el otro servicio escriba en nuestras tablas. DESCARTADA: devuelve el
--      acoplamiento que se quito al separarlos.
--   3. ESTA: la venta escribe el HECHO y la INTENCION en la misma transaccion.
--      El inventario la aplica despues, de forma idempotente.
--
-- POR QUE ESTO NO ES "UNA LLAMADA QUE SE CAE EN SILENCIO"
--
-- Un fallo aqui deja una fila PENDIENTE envejeciendo, que se puede consultar y
-- alarmar. El 401 del cierre fue invisible porque no dejaba rastro. La
-- diferencia no es la latencia: es que el fallo tenga cuerpo.
--
-- POR QUE NO SE REUTILIZA `sync_outbox`
--
-- Medido el 2026-09-01 en produccion: tiene RLS ACTIVA con CERO POLITICAS
-- -- deniega todo a `app_user` -- ni una fila, y ni siquiera columna
-- `tenant_id`. Es de otro proposito (sincronizacion a la nube, hoy apagada) y
-- arreglarlo despertaria un camino dormido. Esta bandeja nace bien en vez de
-- heredar eso.
--
-- LA SEDE NO SE COPIA AQUI
--
-- Sale de `orders.site_id`, y el consumidor la resuelve por `orden_id`.
-- Duplicarla seria un segundo sitio donde el mismo hecho puede discrepar.
-- =====================================================================

CREATE TABLE IF NOT EXISTS public.inventario_intenciones (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       TEXT        NOT NULL
                    DEFAULT NULLIF(current_setting('app.tenant_id', true), ''),

    orden_id        BIGINT      NOT NULL,
    orden_uuid      UUID        NULL,

    -- Cuando ocurrio la VENTA (puede venir de un dispositivo sin cobertura) y
    -- cuando se registro la intencion. No son lo mismo, y el movimiento que
    -- salga de aqui tiene que nacer con la fecha del hecho, no con la del
    -- procesamiento -- si no, un pedido tomado offline descontaria inventario
    -- en el dia equivocado.
    ocurrido_en     TIMESTAMPTZ NOT NULL,
    registrado_en   TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Regla 4: quien vendio. Puede ser nulo si no se pudo resolver -- la venta
    -- no se pierde por no poder firmarla -- pero entonces el movimiento
    -- heredara esa incertidumbre en vez de inventar un responsable.
    usuario_id      TEXT        NULL,
    terminal_id     TEXT        NULL,

    -- Las lineas vendidas: [{"producto_id": "...", "cantidad": 2}, ...].
    -- Es el CRUDO (regla 7). El desglose en insumos lo hace el consumidor con
    -- la lista de materiales VIGENTE en `ocurrido_en`, no con la de hoy.
    lineas          JSONB       NOT NULL,

    estado          TEXT        NOT NULL DEFAULT 'PENDIENTE',
    intentos        INTEGER     NOT NULL DEFAULT 0,
    ultimo_error    TEXT        NULL,
    aplicada_en     TIMESTAMPTZ NULL,

    -- Regla 12: reprocesar no duplica.
    idempotency_key TEXT        NOT NULL,

    CONSTRAINT pk_inventario_intenciones PRIMARY KEY (id),

    CONSTRAINT ck_int_estado CHECK (estado IN (
        'PENDIENTE',   -- esperando al consumidor
        'APLICADA',    -- ya produjo sus movimientos
        'FALLIDA',     -- agoto reintentos; alguien tiene que mirarla
        'DESCARTADA')),-- decidido a mano que no debe aplicarse

    -- Una intencion aplicada sin fecha de aplicacion, o al reves, es una fila
    -- que miente sobre su propio estado.
    CONSTRAINT ck_int_aplicada CHECK (
        (estado = 'APLICADA') = (aplicada_en IS NOT NULL)),

    -- Un fallo sin explicacion no sirve para diagnosticar nada, que es
    -- justamente lo que paso con "posible falta de internet" en el cierre.
    CONSTRAINT ck_int_error CHECK (
        estado <> 'FALLIDA' OR (ultimo_error IS NOT NULL
                                AND length(btrim(ultimo_error)) > 0)),

    CONSTRAINT ck_int_reloj CHECK (ocurrido_en <= registrado_en),
    CONSTRAINT ck_int_intentos CHECK (intentos >= 0),
    CONSTRAINT ck_int_lineas CHECK (jsonb_typeof(lineas) = 'array')
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_int_idempotencia
    ON public.inventario_intenciones (tenant_id, idempotency_key);

-- El indice del consumidor: lo pendiente, lo mas viejo primero. Y es tambien
-- el de la alarma que importa -- "¿hay algo PENDIENTE de hace mas de N
-- minutos?" --, que es lo que convierte un fallo silencioso en uno visible.
CREATE INDEX IF NOT EXISTS ix_int_pendientes
    ON public.inventario_intenciones (tenant_id, registrado_en)
    WHERE estado = 'PENDIENTE';

CREATE INDEX IF NOT EXISTS ix_int_orden
    ON public.inventario_intenciones (tenant_id, orden_id);

COMMENT ON TABLE public.inventario_intenciones IS
    'Bandeja de salida hacia ms-smart-inventory. La venta la escribe en su '
    'misma transaccion; el inventario la aplica de forma idempotente.';


-- =====================================================================
-- Aislamiento
-- =====================================================================
ALTER TABLE public.inventario_intenciones ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inventario_intenciones FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_inventario_intenciones ON public.inventario_intenciones;
CREATE POLICY tenant_isolation_inventario_intenciones ON public.inventario_intenciones
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));

DO $permisos$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        -- UPDATE si: el consumidor marca APLICADA y cuenta intentos. DELETE no:
        -- una intencion borrada es una venta que descontaria dos veces si
        -- alguien reprocesa, y nadie sabria que existio.
        GRANT SELECT, INSERT, UPDATE ON public.inventario_intenciones TO app_user;
    END IF;
END
$permisos$;


-- =====================================================================
-- La comprobacion que hace fallar la migracion
-- =====================================================================
DO $cierre$
DECLARE abiertas TEXT;
BEGIN
    SELECT string_agg(policyname, ', ') INTO abiertas
    FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'inventario_intenciones'
      AND permissive = 'PERMISSIVE' AND (qual = 'true' OR qual IS NULL);
    IF abiertas IS NOT NULL THEN
        RAISE EXCEPTION 'V42: politica abierta en inventario_intenciones: %', abiertas;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user')
       AND has_table_privilege('app_user', 'public.inventario_intenciones', 'DELETE') THEN
        RAISE EXCEPTION 'V42: app_user puede borrar intenciones; no debe';
    END IF;

    RAISE NOTICE 'V42: bandeja de intenciones con aislamiento verificado.';
END $cierre$;


-- =====================================================================
-- DOWN
-- =====================================================================
-- DROP TABLE IF EXISTS public.inventario_intenciones;
