package com.suresell.orders.domain.model;

/**
 * De dónde salió el valor de QR que quedó registrado en un cierre de caja.
 *
 * <p><b>Enum CERRADO</b> (regla 9 y 10 de LINEAMIENTOS_DESARROLLO_DATA_FIRST):
 * no hay valor "otro", "varios" ni "ajuste". Si algún día aparece un origen que
 * no encaje en estos tres, se agrega un valor con su migración — no se mete en
 * un cajón de sastre, porque ahí es justamente donde se esconde la señal.
 *
 * <p><b>Por qué existe.</b> Hasta ahora el cierre guardaba el monto de QR sin
 * decir de dónde venía. Conciliado contra el registro del administrador, tecleado
 * por el cajero, o puesto por defecto tras un fallo de integración: los tres
 * quedaban indistinguibles en la misma columna. Eso permitió que entre el
 * 2026-07-30 y el 2026-08-20 los cierres se cuadraran con el valor manual
 * mientras la conciliación devolvía 401, sin que nadie pudiera notarlo mirando
 * los datos.
 */
public enum FuenteQr {

    /**
     * `ms-core-app` respondió y el monto salió de su registro de pagos QR.
     * Es el único caso en que el dato está conciliado contra otra fuente.
     */
    conciliado_core,

    /**
     * El monto salió de la SUMA DE VENTAS del POS con `payment_method = 'QR'`.
     *
     * <p>Es el único de los tres orígenes que existe siempre, porque sale de las
     * ventas mismas y no de un registro que alguien tenga que mantener.
     */
    pos,

    /**
     * El monto lo tecleó el cajero y no había nada contra qué conciliarlo: la
     * consulta funcionó y respondió que no hay pago QR registrado para ese día
     * (404). No es un fallo — es la respuesta legítima "no hay nada".
     */
    manual_cajero,

    /**
     * `ms-core-app` respondió correctamente pero **con cero o sin registro**, y
     * el cajero SÍ contó dinero por QR.
     *
     * <p>Este valor existe por lo que los datos revelaron: `qr_payments` tiene
     * <b>tres filas en toda su historia</b>. El administrador prácticamente no
     * ha usado ese registro, así que "el externo dice cero" es la respuesta
     * normal, no una anomalía.
     *
     * <p>Sin este valor, la primera versión de V34 habría tomado ese cero como
     * conciliación buena y **cada cierre habría reportado cero esperado en QR
     * cuando el negocio recibe del orden de $460.000 diarios** por ese medio.
     *
     * <p>Confianza 0 y el total sigue usando el manual. La regla la sostiene la
     * base: `ck_daily_closures_qr_cero_externo`.
     */
    sin_registro_externo,

    /**
     * La conciliación NO se pudo hacer: 401, timeout, 5xx, respuesta ilegible,
     * lo que sea. El cierre se completó igual con el valor del cajero, pero el
     * dato NO está conciliado y el motivo técnico real queda en `qr_detalle`.
     *
     * <p>La diferencia con {@link #manual_cajero} es la que importa: allí
     * sabemos que no había nada; aquí no sabemos nada.
     */
    fallo_integracion
}
