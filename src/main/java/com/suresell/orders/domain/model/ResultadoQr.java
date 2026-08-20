package com.suresell.orders.domain.model;

import java.math.BigDecimal;

/**
 * El monto de QR que va al cierre, JUNTO CON su procedencia y su fiabilidad.
 *
 * <p>Antes esto era un {@code BigDecimal} pelado. Un número sin origen no se
 * puede auditar: da igual si vino de la conciliación, del teclado del cajero o
 * de un fallo silencioso, porque en la base se ve idéntico. Las reglas 5 y 6 de
 * LINEAMIENTOS_DESARROLLO_DATA_FIRST existen exactamente para esto — todo dato
 * que sale de una fuente viaja con su nivel de confianza y con su fuente
 * explícita.
 *
 * @param monto      el valor que se usa en el cuadre
 * @param fuente     de dónde salió, enum cerrado
 * @param confianza  0–3, ver {@link #CONFIANZA_CONCILIADO} y {@link #CONFIANZA_SIN_CONCILIAR}
 * @param detalle    mensaje TÉCNICO del fallo, o null si no hubo. NO es un campo
 *                   analizable: no se agrega ni se compara, solo se lee cuando
 *                   alguien investiga. Lo analizable es {@code fuente}
 */
public record ResultadoQr(BigDecimal monto, FuenteQr fuente, short confianza, String detalle,
                          BigDecimal qrPos, BigDecimal qrManual, BigDecimal qrConciliado) {

    /**
     * Dato conciliado contra una segunda fuente (el registro del administrador
     * en `ms-core-app`).
     *
     * <p>⚠️ LINEAMIENTOS define la escala como "0–3" (reglas 0 y 5) pero **no
     * incluye la tabla que dice qué significa cada nivel**. Este valor es el que
     * fija el encargo de la tarea, no una interpretación propia. Ver
     * `discovery/BLOQUEOS-FASE-1.md`.
     */
    public static final short CONFIANZA_CONCILIADO = 2;

    /**
     * Dato sin conciliar: lo tecleó una persona, o no se pudo verificar contra
     * nada. Aplica tanto a {@link FuenteQr#manual_cajero} como a
     * {@link FuenteQr#fallo_integracion}.
     */
    public static final short CONFIANZA_SIN_CONCILIAR = 0;

    /**
     * `ms-core-app` respondió con un monto REAL (distinto de cero): hay
     * conciliación de verdad contra una segunda fuente.
     */
    public static ResultadoQr conciliado(BigDecimal montoDeCore, BigDecimal delPos,
                                         BigDecimal delCajero) {
        return new ResultadoQr(montoDeCore, FuenteQr.conciliado_core, CONFIANZA_CONCILIADO, null,
                delPos, delCajero, montoDeCore);
    }

    /**
     * `ms-core-app` respondió correctamente pero con CERO o sin registro, y el
     * cajero sí contó dinero.
     *
     * <p><b>El total usa el del cajero, nunca el cero.</b> Es el escenario que
     * los datos revelaron —`qr_payments` con tres filas en toda su historia— y
     * el que la primera versión de V34 habría convertido en trece cierres con
     * cero esperado en QR.
     */
    public static ResultadoQr sinRegistroExterno(BigDecimal delCajero, BigDecimal delPos,
                                                 BigDecimal loQueDijoCore) {
        return new ResultadoQr(normalizar(delCajero), FuenteQr.sin_registro_externo,
                CONFIANZA_SIN_CONCILIAR, null, delPos, delCajero, loQueDijoCore);
    }

    /**
     * El monto salió de la suma de ventas por QR del POS.
     *
     * <p>Es el único origen que existe siempre: sale de las ventas mismas y no
     * de un registro que alguien tenga que mantener al día.
     */
    public static ResultadoQr delPos(BigDecimal delPos, BigDecimal delCajero) {
        return new ResultadoQr(normalizar(delPos), FuenteQr.pos,
                CONFIANZA_SIN_CONCILIAR, null, delPos, delCajero, null);
    }

    /**
     * La consulta funcionó y no hay pago QR registrado para ese día. Se usa el
     * valor del cajero.
     *
     * <p>No se registra como {@code conciliado_core} con monto cero: un 404
     * significa "no hay registro", no "el registro dice cero". Afirmar una
     * conciliación que no ocurrió es justo lo que este cambio viene a impedir.
     */
    public static ResultadoQr manual(BigDecimal montoDelCajero, BigDecimal delPos) {
        return new ResultadoQr(normalizar(montoDelCajero), FuenteQr.manual_cajero,
                CONFIANZA_SIN_CONCILIAR, null, delPos, montoDelCajero, null);
    }

    /**
     * No se pudo conciliar. El cierre sigue adelante con el valor del cajero,
     * pero queda marcado y con el motivo técnico REAL.
     *
     * @param detalleTecnico el mensaje del fallo tal cual. Nunca una explicación
     *                       inventada: el log decía "posible falta de internet"
     *                       cuando lo que pasaba era un 401, y esa frase mandó a
     *                       todo el mundo a mirar donde no era
     */
    public static ResultadoQr fallo(BigDecimal montoDelCajero, BigDecimal delPos,
                                    String detalleTecnico) {
        return new ResultadoQr(normalizar(montoDelCajero), FuenteQr.fallo_integracion,
                CONFIANZA_SIN_CONCILIAR, detalleTecnico, delPos, montoDelCajero, null);
    }

    private static BigDecimal normalizar(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}
