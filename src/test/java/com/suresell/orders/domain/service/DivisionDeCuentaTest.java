package com.suresell.orders.domain.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * DIVISIÓN DE CUENTA — la red que impide que el negocio pierda plata sin darse
 * cuenta, o que le cobre de más a un cliente.
 *
 * <p>El test que importa es {@link #invarianteFiscal}: para TODO total y TODO
 * N entre 2 y 10, {@code suma(pagos) + ajuste == total}. Cero excepciones.
 */
class DivisionDeCuentaTest {

    private static BigDecimal pesos(String v) {
        return new BigDecimal(v);
    }

    // ------------------------------------------------------------------
    // El invariante. Si algo de este archivo falla, que sea esto.
    // ------------------------------------------------------------------

    /**
     * INVARIANTE FISCAL, barrido exhaustivo.
     *
     * <p>Para cada N ∈ [2,10] se recorren 2.000 totales consecutivos: se cubren
     * TODOS los restos posibles de esa división, no una muestra con suerte.
     */
    @ParameterizedTest(name = "dividido entre {0} personas siempre cuadra")
    @ValueSource(ints = {2, 3, 4, 5, 6, 7, 8, 9, 10})
    @DisplayName("suma(pagos) + ajuste_redondeo_negocio == total, para todo T y todo N")
    void invarianteFiscal(int personas) {
        for (int t = 0; t < 2_000; t++) {
            BigDecimal total = BigDecimal.valueOf(t);
            DivisionDeCuenta.Reparto r = DivisionDeCuenta.repartir(total, personas);

            assertEquals(0, r.cobrado().add(r.residuo()).compareTo(total),
                    () -> "No cuadra con total=" + total + " y N=" + personas);

            // El residuo siempre cabe dentro de una vuelta: 0 <= residuo < N.
            assertTrue(r.residuo().compareTo(BigDecimal.ZERO) >= 0,
                    "El residuo nunca puede ser negativo (sería cobrar de más)");
            assertTrue(r.residuo().compareTo(BigDecimal.valueOf(personas)) < 0,
                    "Un residuo >= N significa que se repartió de menos");
        }
    }

    /** El mismo invariante con totales grandes y con centavos. */
    @Test
    @DisplayName("el invariante también aguanta totales enormes y con centavos")
    void invarianteConCentavosYTotalesGrandes() {
        List<BigDecimal> totales = List.of(
                pesos("10000.50"), pesos("0.01"), pesos("999999.99"),
                pesos("1234567.89"), pesos("7"), pesos("0"));
        for (BigDecimal total : totales) {
            for (int n = 2; n <= DivisionDeCuenta.MAX_PERSONAS; n++) {
                DivisionDeCuenta.Reparto r = DivisionDeCuenta.repartir(total, n);
                assertEquals(0, r.cobrado().add(r.residuo()).compareTo(total),
                        "No cuadra con total=" + total + " y N=" + n);
                assertTrue(r.residuo().compareTo(BigDecimal.ZERO) >= 0);
            }
        }
    }

    // ------------------------------------------------------------------
    // Nunca hacia arriba: el comensal jamás paga de más.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("$10.000 entre 3: cada uno paga $3.333 y el negocio asume $1")
    void elCasoQueTrabaLaDecision() {
        DivisionDeCuenta.Reparto r = DivisionDeCuenta.repartir(pesos("10000"), 3);

        assertEquals(0, pesos("3333").compareTo(r.base()));
        assertEquals(0, pesos("9999").compareTo(r.cobrado()));
        assertEquals(0, pesos("1").compareTo(r.residuo()));
    }

    @Test
    @DisplayName("nunca se redondea hacia arriba: base <= total/N, siempre")
    void nuncaCobraDeMas() {
        for (int n = 2; n <= DivisionDeCuenta.MAX_PERSONAS; n++) {
            for (int t = 1; t < 500; t++) {
                DivisionDeCuenta.Reparto r = DivisionDeCuenta.repartir(BigDecimal.valueOf(t), n);
                assertTrue(
                        r.base().multiply(BigDecimal.valueOf(n)).compareTo(BigDecimal.valueOf(t)) <= 0,
                        "Con total=" + t + " y N=" + n + " se estaría cobrando de más");
            }
        }
    }

    @Test
    @DisplayName("cuando la división es exacta no hay ajuste que asumir")
    void divisionExactaNoDejaResiduo() {
        DivisionDeCuenta.Reparto r = DivisionDeCuenta.repartir(pesos("12000"), 4);
        assertEquals(0, pesos("3000").compareTo(r.base()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.residuo()));
    }

    // ------------------------------------------------------------------
    // Entradas inválidas: se rechazan, no se "arreglan".
    // ------------------------------------------------------------------

    @Test
    void dividirEntreMenosDeDosNoEsDividir() {
        assertThrows(IllegalArgumentException.class,
                () -> DivisionDeCuenta.repartir(pesos("1000"), 1));
        assertThrows(IllegalArgumentException.class,
                () -> DivisionDeCuenta.repartir(pesos("1000"), 0));
    }

    @Test
    @DisplayName("el tope de negocio son 10 comensales: 11 se rechaza")
    void masDelTopeEsUnErrorDeDigitacion() {
        assertEquals(10, DivisionDeCuenta.MAX_PERSONAS,
                "Si el tope cambia, tiene que ser una decisión consciente y no un descuido");
        assertDoesNotThrow(() -> DivisionDeCuenta.repartir(pesos("1000"), 10));
        assertThrows(IllegalArgumentException.class,
                () -> DivisionDeCuenta.repartir(pesos("1000"), 11));
    }

    @Test
    void noSePuedeDividirUnTotalNegativo() {
        assertThrows(IllegalArgumentException.class,
                () -> DivisionDeCuenta.repartir(pesos("-1"), 2));
    }

    // ------------------------------------------------------------------
    // Agrupación por medio de pago.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("dos pagan en efectivo y uno con tarjeta: se agrupa por medio")
    void agrupaPorMedioDePago() {
        DivisionDeCuenta.Reparto r = DivisionDeCuenta.repartir(pesos("10000"), 3);
        Map<String, BigDecimal> porMetodo =
                DivisionDeCuenta.agruparPorMetodo(r, List.of("CASH", "CASH", "CARD"));

        assertEquals(0, pesos("6666").compareTo(porMetodo.get("CASH")));
        assertEquals(0, pesos("3333").compareTo(porMetodo.get("CARD")));

        BigDecimal sumaPagos = porMetodo.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, sumaPagos.add(r.residuo()).compareTo(r.total()),
                "El invariante tiene que sobrevivir a la agrupación por medio");
    }

    @Test
    void faltarUnMedioDePagoEsUnError() {
        DivisionDeCuenta.Reparto r = DivisionDeCuenta.repartir(pesos("10000"), 3);
        assertThrows(IllegalArgumentException.class,
                () -> DivisionDeCuenta.agruparPorMetodo(r, List.of("CASH", "CARD")));
        assertThrows(IllegalArgumentException.class,
                () -> DivisionDeCuenta.agruparPorMetodo(r, null));
    }

    // ------------------------------------------------------------------
    // Reparto entre las rondas de la mesa.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("el reparto entre órdenes suma EXACTO el monto de entrada")
    void repartoEntreOrdenesEsExacto() {
        // Tres rondas de tamaños feos: el caso donde el redondeo ingenuo falla.
        List<BigDecimal> pesosOrdenes = List.of(pesos("3333"), pesos("3333"), pesos("3334"));
        for (int monto = 0; monto < 500; monto++) {
            List<BigDecimal> partes =
                    DivisionDeCuenta.repartirProporcional(BigDecimal.valueOf(monto), pesosOrdenes);
            BigDecimal suma = partes.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, suma.compareTo(BigDecimal.valueOf(monto)),
                    "El reparto entre órdenes perdió o inventó pesos con monto=" + monto);
            partes.forEach(p -> assertTrue(p.compareTo(BigDecimal.ZERO) >= 0,
                    "Ninguna parte puede ser negativa"));
        }
    }

    @Test
    @DisplayName("una mesa con una sola orden recibe todo el monto")
    void unaSolaOrdenSeLlevaTodo() {
        List<BigDecimal> partes =
                DivisionDeCuenta.repartirProporcional(pesos("9999"), List.of(pesos("10000")));
        assertEquals(1, partes.size());
        assertEquals(0, pesos("9999").compareTo(partes.get(0)));
    }

    @Test
    @DisplayName("si las órdenes suman cero, el monto no se pierde")
    void ordenesEnCeroNoPierdenElMonto() {
        List<BigDecimal> partes = DivisionDeCuenta.repartirProporcional(
                pesos("500"), List.of(BigDecimal.ZERO, BigDecimal.ZERO));
        BigDecimal suma = partes.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, pesos("500").compareTo(suma));
    }

    @Test
    @DisplayName("sin órdenes no hay reparto, y no revienta")
    void sinOrdenesDevuelveVacio() {
        assertTrue(DivisionDeCuenta.repartirProporcional(pesos("500"), List.of()).isEmpty());
    }

    // ------------------------------------------------------------------
    // El invariante COMPLETO, extremo a extremo: reparto -> agrupación ->
    // distribución entre órdenes. Es la cadena real que corre en producción.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("de punta a punta: lo que se guarda por orden + el ajuste == el total")
    void invarianteDePuntaAPunta() {
        List<BigDecimal> ordenes = List.of(pesos("12500"), pesos("8300"), pesos("4200"));
        BigDecimal total = ordenes.stream().reduce(BigDecimal.ZERO, BigDecimal::add); // 25.000

        for (int n = 2; n <= DivisionDeCuenta.MAX_PERSONAS; n++) {
            DivisionDeCuenta.Reparto r = DivisionDeCuenta.repartir(total, n);
            // Se alternan los medios para que la agrupación no sea trivial.
            List<String> metodos = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                metodos.add(switch (i % 3) {
                    case 0 -> "CASH";
                    case 1 -> "CARD";
                    default -> "QR";
                });
            }
            Map<String, BigDecimal> porMetodo = DivisionDeCuenta.agruparPorMetodo(r, metodos);

            BigDecimal guardado = BigDecimal.ZERO;
            for (BigDecimal monto : porMetodo.values()) {
                for (BigDecimal parte : DivisionDeCuenta.repartirProporcional(monto, ordenes)) {
                    guardado = guardado.add(parte);
                }
            }
            assertEquals(0, guardado.add(r.residuo()).compareTo(total),
                    "Descuadre extremo a extremo con N=" + n);
        }
    }
}
