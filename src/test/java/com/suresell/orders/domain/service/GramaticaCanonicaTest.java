package com.suresell.orders.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comprobaciones de la gramática que los vectores de oro NO cubren.
 *
 * <p>Los 15 vectores son el contrato entre el POS y este servidor, y lo cubren
 * casi todo. Lo que queda aquí son dos cosas: diferencias de plataforma que solo
 * se pueden afirmar midiéndolas en Java, y casos que la implementación defiende
 * pero ningún vector ejerce — que es distinto de "casos cubiertos".
 */
class GramaticaCanonicaTest {

    @Test
    @DisplayName("el cero negativo no existe en BigDecimal: la guarda no se dispara")
    void elCeroNegativoNoExisteEnBigDecimal() {
        // El vector `negativo-que-redondea-a-cero` existe por JavaScript, donde
        // (-0.001).toFixed(2) da "-0.00". En Java el problema no se plantea, y
        // por eso romper esa línea NO pone ningún vector en rojo — comprobado.
        // Se deja escrito para que nadie la "cubra" con un test que en realidad
        // no la ejerce.
        for (String v : new String[] {"-0", "-0.0", "-0.001", "-0.004"}) {
            assertEquals("0.00", GramaticaCanonica.decimal(new BigDecimal(v)),
                    "BigDecimal produjo un cero con signo para " + v);
        }
        // Y donde sí hay magnitud, el signo se conserva.
        assertEquals("-0.01", GramaticaCanonica.decimal(new BigDecimal("-0.005")));
        assertEquals("-0.50", GramaticaCanonica.decimal(new BigDecimal("-0.5")));
    }

    @Test
    @DisplayName("HALF_UP, no HALF_EVEN: es lo que hace Postgres con NUMERIC(15,2)")
    void redondeoHalfUp() {
        // Con HALF_EVEN, 1.005 daría "1.00" y 2.675 daría "2.68": el primero
        // discreparía del POS y del valor que queda almacenado.
        assertEquals("1.01", GramaticaCanonica.decimal(new BigDecimal("1.005")));
        assertEquals("2.68", GramaticaCanonica.decimal(new BigDecimal("2.675")));
        assertEquals("0.02", GramaticaCanonica.decimal(new BigDecimal("0.015")));
    }

    @Test
    @DisplayName("AUSENTE y CERO son hechos distintos")
    void ausenteNoEsCero() {
        assertEquals("", GramaticaCanonica.decimal(null));
        assertEquals("0.00", GramaticaCanonica.decimal(BigDecimal.ZERO));
        assertNotEquals(GramaticaCanonica.decimal(null),
                GramaticaCanonica.decimal(BigDecimal.ZERO));
        assertEquals("", GramaticaCanonica.texto(null));
        assertEquals("", GramaticaCanonica.entero(null));
        assertEquals("0", GramaticaCanonica.entero(0));
    }

    @Test
    @DisplayName("los milisegundos van siempre, aunque sean cero")
    void milisegundosSiempre() {
        // Instant.toString() da "2026-08-20T21:00:00Z" —sin milisegundos— donde
        // JavaScript da "...T21:00:00.000Z". Es la trampa que documenta el
        // vector `milisegundos-en-cero`.
        Instant enPunto = Instant.parse("2026-08-20T21:00:00Z");
        assertEquals("2026-08-20T21:00:00.000Z", GramaticaCanonica.fecha(enPunto));
        assertNotEquals(enPunto.toString(), GramaticaCanonica.fecha(enPunto),
                "si estas dos coincidieran, se estaría usando toString() y el "
                        + "hash divergiría del POS en toda hora exacta");
    }

    @Test
    @DisplayName("la barra invertida se escapa PRIMERO")
    void ordenDelEscapado() {
        // Al revés, "\\n" (barra + n literales) y un salto de línea real
        // acabarían produciendo la misma cadena, y dos hechos distintos tendrían
        // el mismo hash.
        assertEquals("\\\\n", GramaticaCanonica.texto("\\n"));
        assertEquals("\\n", GramaticaCanonica.texto("\n"));
        assertNotEquals(GramaticaCanonica.texto("\\n"), GramaticaCanonica.texto("\n"));
        assertEquals("a\\pb", GramaticaCanonica.texto("a|b"));
        assertEquals("a\\cb", GramaticaCanonica.texto("a:b"));
    }

    @Test
    @DisplayName("el entero trunca hacia cero, como Math.trunc")
    void enteroTrunca() {
        assertEquals("3", GramaticaCanonica.entero(3.9));
        assertEquals("-3", GramaticaCanonica.entero(-3.9));
    }

    @Test
    @DisplayName("una fecha con desfase se normaliza a UTC")
    void desfaseANormalizado() {
        // Dos terminales en husos distintos que registran el MISMO instante
        // deben producir el mismo hash.
        assertEquals(
                GramaticaCanonica.fecha(GramaticaCanonica.instanteDe("2026-08-20T14:32:05.123Z")),
                GramaticaCanonica.fecha(
                        GramaticaCanonica.instanteDe("2026-08-20T09:32:05.123-05:00")));
    }
}
