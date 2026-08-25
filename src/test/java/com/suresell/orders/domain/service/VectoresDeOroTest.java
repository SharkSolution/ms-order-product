package com.suresell.orders.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;

/**
 * Los 15 vectores de oro contra la implementación Java.
 *
 * <h3>Para qué existe este test</h3>
 *
 * {@code hash-vectores-oro.json} lo genera el POS (TypeScript) y define el
 * formato. Esta es la <b>primera reimplementación real</b> de esa gramática, y
 * los vectores existen exactamente para este momento: para que las dos
 * implementaciones no diverjan en silencio.
 *
 * <p>Una divergencia no se vería como un error. Se vería como una cadena de
 * hashes que "no verifica" — el verificador diría "manipulado" sobre ventas
 * correctas cuyo único pecado es haberse hasheado en el otro lenguaje. Ruido que
 * hace desconfiar de datos buenos, que es el peor resultado posible.
 *
 * <h3>Qué se compara, y por qué las DOS cosas</h3>
 *
 * Se compara la <b>cadena canónica</b> y el <b>hash</b>. Comparar solo el hash
 * diría "no coincide" sin decir dónde; comparar solo la canónica dejaría fuera un
 * error de codificación UTF-8 o del propio SHA-256. Con las dos, un fallo señala
 * el carácter exacto.
 *
 * <h3>Cómo se leen los números</h3>
 *
 * Con {@code USE_BIG_DECIMAL_FOR_FLOATS}. No es un detalle: leyendo
 * {@code 1.005} como {@code double}, {@code new BigDecimal(double)} da
 * {@code 1.00499999999999989...} y redondea a {@code 1.00} en vez de a
 * {@code 1.01}. El vector {@code importe-con-mas-de-dos-decimales} existe para
 * atrapar justo eso, y solo lo atrapa si el JSON se lee así.
 */
class VectoresDeOroTest {

    private static JsonNode raiz;

    @BeforeAll
    static void cargar() throws Exception {
        ObjectMapper om = new ObjectMapper()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        try (InputStream in = VectoresDeOroTest.class
                .getResourceAsStream("/hash-vectores-oro.json")) {
            assertNotNull(in, "no se encontró hash-vectores-oro.json en el classpath de tests");
            raiz = om.readTree(in);
        }
    }

    @Test
    @DisplayName("son 15 vectores y la versión canónica coincide con la del POS")
    void elContratoEsElQueSeEspera() {
        // Sin esta comprobación, el @TestFactory de abajo pasaría en verde con
        // un fichero vacío: cero vectores son cero vectores que fallan. Es el
        // modo de fallo que ya se coló una vez en V39.
        assertEquals(15, raiz.get("vectores").size(),
                "cambió el número de vectores: eso no es actualizar un fichero, "
                        + "es partir la cadena histórica en dos formatos");
        assertEquals(GramaticaCanonica.VERSION_CANONICA, raiz.get("version").asText(),
                "la versión canónica del Java y la del JSON no coinciden");
        assertTrue(raiz.get("algoritmo").asText().startsWith("SHA-256"));
    }

    @TestFactory
    @DisplayName("cada vector reproduce EXACTAMENTE su canónica y su hash")
    List<DynamicTest> losQuinceVectores() {
        List<DynamicTest> casos = new ArrayList<>();
        for (JsonNode v : raiz.get("vectores")) {
            String nombre = v.get("nombre").asText();
            casos.add(DynamicTest.dynamicTest(nombre, () -> {
                JsonNode entrada = v.get("entrada");
                GramaticaCanonica.Identidad id = identidadDe(entrada.get("identidad"));
                GramaticaCanonica.Hechos hechos = hechosDe(entrada.get("orden"));

                String canonicaEsperada = v.get("canonica").asText();
                String canonicaObtenida = GramaticaCanonica.formaCanonica(id, hechos);

                assertEquals(canonicaEsperada, canonicaObtenida,
                        "vector '" + nombre + "': la forma canónica difiere.\n"
                                + "  porQue del vector: " + v.get("porQue").asText() + "\n"
                                + primeraDiferencia(canonicaEsperada, canonicaObtenida));

                assertEquals(v.get("hash").asText(), GramaticaCanonica.hash(id, hechos),
                        "vector '" + nombre + "': la canónica coincide pero el hash no. "
                                + "Eso apunta a la codificación UTF-8 o al propio SHA-256, "
                                + "no a la gramática.");
            }));
        }
        return casos;
    }

    // =====================================================================

    private static GramaticaCanonica.Identidad identidadDe(JsonNode n) {
        return new GramaticaCanonica.Identidad(
                texto(n, "terminalId"),
                n.hasNonNull("epoch") ? n.get("epoch").asInt() : null,
                n.hasNonNull("seq") ? n.get("seq").asLong() : null,
                texto(n, "tipo"),
                texto(n, "ref"),
                texto(n, "hashAnterior"));
    }

    private static GramaticaCanonica.Hechos hechosDe(JsonNode n) {
        List<GramaticaCanonica.Linea> lineas = new ArrayList<>();
        if (n.hasNonNull("items")) {
            for (JsonNode it : n.get("items")) {
                lineas.add(new GramaticaCanonica.Linea(
                        texto(it, "productId"),
                        it.hasNonNull("quantity") ? it.get("quantity").asInt() : null,
                        decimal(it, "unitPrice"),
                        decimal(it, "totalPrice")));
            }
        }
        List<GramaticaCanonica.Pago> pagos = new ArrayList<>();
        if (n.hasNonNull("payments")) {
            for (JsonNode p : n.get("payments")) {
                pagos.add(new GramaticaCanonica.Pago(texto(p, "method"), decimal(p, "amount")));
            }
        }
        return new GramaticaCanonica.Hechos(
                GramaticaCanonica.instanteDe(texto(n, "createdAt")),
                texto(n, "paymentMethod"),
                decimal(n, "subtotal"),
                decimal(n, "discountAmount"),
                decimal(n, "total"),
                lineas, pagos);
    }

    /** Distingue "campo ausente o null" de "cadena vacía": son hechos distintos. */
    private static String texto(JsonNode n, String campo) {
        return n.hasNonNull(campo) ? n.get(campo).asText() : null;
    }

    private static BigDecimal decimal(JsonNode n, String campo) {
        if (!n.hasNonNull(campo)) {
            return null;
        }
        // decimalValue() respeta USE_BIG_DECIMAL_FOR_FLOATS: el literal del JSON
        // tal cual se escribió, sin pasar por double.
        return n.get(campo).decimalValue();
    }

    /** Señala el primer carácter que difiere, para no tener que leer 20 líneas. */
    private static String primeraDiferencia(String esperada, String obtenida) {
        int n = Math.min(esperada.length(), obtenida.length());
        for (int i = 0; i < n; i++) {
            if (esperada.charAt(i) != obtenida.charAt(i)) {
                return "  primera diferencia en el carácter " + i + ":\n"
                        + "    esperado: ..." + visible(esperada, i) + "\n"
                        + "    obtenido: ..." + visible(obtenida, i);
            }
        }
        return "  una es prefijo de la otra: esperada " + esperada.length()
                + " caracteres, obtenida " + obtenida.length();
    }

    private static String visible(String s, int i) {
        int desde = Math.max(0, i - 20);
        int hasta = Math.min(s.length(), i + 20);
        return s.substring(desde, hasta)
                .replace("\n", "⏎")
                .replace("\r", "␍");
    }
}
