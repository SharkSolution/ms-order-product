package com.suresell.orders.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * SERIALIZACIÓN CANÓNICA Y HASH DE UN EVENTO, EN JAVA.
 *
 * <p>Reimplementación exacta de
 * {@code front_pos_electron/src/app/core/offline/hash-del-evento.ts}. Ese
 * archivo es la definición del formato; este es un segundo hablante del mismo
 * idioma.
 *
 * <h3>⚠️ Esto define un formato de datos, no una utilidad</h3>
 *
 * Cambiar cualquier regla invalida todos los hashes ya escritos. Si hay que
 * cambiarla se sube {@link #VERSION_CANONICA} <b>en las dos implementaciones a
 * la vez</b> y se regeneran los vectores; no se edita en el sitio.
 *
 * <h3>El contrato es `hash-vectores-oro.json`, y no es decorativo</h3>
 *
 * Los 15 vectores de oro existen para que dos implementaciones en lenguajes
 * distintos no diverjan en silencio. {@code VectoresDeOroTest} los recorre todos
 * y compara la cadena canónica <b>y</b> el hash. Mientras ese test esté verde,
 * un hash escrito por el POS y uno escrito por este servidor son comparables.
 *
 * <p>Los casos que más costaron y que hay que respetar aquí al pie de la letra:
 *
 * <ul>
 *   <li><b>HALF_UP contra {@code toFixed}.</b> {@code (1.005).toFixed(2)} da
 *       {@code "1.00"} en JavaScript y {@code setScale(2, HALF_UP)} da
 *       {@code 1.01} en Java. El TypeScript se corrigió para hacer HALF_UP; aquí
 *       basta con no caer en {@code new BigDecimal(double)}, que reconstruye el
 *       binario y vuelve a dar 1.00. Ver {@link #decimal(BigDecimal)}.</li>
 *   <li><b>El cero negativo.</b> {@code BigDecimal} no tiene cero negativo, pero
 *       un {@code -0.001} redondeado sí produciría {@code "-0.00"}. Se normaliza
 *       a {@code "0.00"}.</li>
 *   <li><b>Los milisegundos.</b> {@code Instant.toString()} los OMITE cuando son
 *       cero: da {@code "2026-08-20T21:00:00Z"} donde JavaScript da
 *       {@code "...T21:00:00.000Z"}. Por eso el formato es explícito y no se usa
 *       {@code toString()}.</li>
 *   <li><b>NFC contra NFD.</b> "Café" desde iOS llega descompuesto y desde
 *       Windows compuesto. Sin {@code Normalizer.normalize(..., NFC)} el mismo
 *       hecho da dos hashes y el verificador diría "manipulado" sobre una venta
 *       correcta.</li>
 * </ul>
 *
 * <h3>Por qué {@code BigDecimal} y no {@code double} en toda la clase</h3>
 *
 * Un {@code double} ya perdió la intención decimal antes de llegar aquí:
 * {@code new BigDecimal(1.005d)} vale {@code 1.00499999999999989...} y redondea
 * a {@code 1.00}. La clase solo acepta {@code BigDecimal} para que ese error no
 * pueda entrar por la puerta.
 */
public final class GramaticaCanonica {

    private GramaticaCanonica() {}

    /** Debe coincidir con {@code VERSION_CANONICA} de {@code hash-del-evento.ts}. */
    public static final String VERSION_CANONICA = "v2";

    /**
     * ISO-8601 en UTC con milisegundos SIEMPRE, aunque sean ceros.
     * {@code Instant.toString()} no sirve: los omite cuando son cero.
     */
    private static final DateTimeFormatter ISO_MILIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    /** Identidad del evento en la cadena del terminal. */
    public record Identidad(String terminalId, Integer epoch, Long seq, String tipo,
                            String ref, String hashAnterior) {}

    /** Una línea del pedido. */
    public record Linea(String productId, Integer quantity,
                        BigDecimal unitPrice, BigDecimal totalPrice) {}

    /** Una porción de multipago. */
    public record Pago(String method, BigDecimal amount) {}

    /** Los hechos económicos del evento. {@code ocurrido} es el instante del dispositivo. */
    public record Hechos(Instant ocurrido, String medioDePago, BigDecimal subtotal,
                         BigDecimal descuento, BigDecimal total,
                         List<Linea> lineas, List<Pago> pagos) {}

    /**
     * Construye la forma canónica. Ver la gramática completa en
     * {@code hash-del-evento.ts}; aquí se reproduce campo a campo y en el mismo
     * orden, terminando con un salto de línea.
     */
    public static String formaCanonica(Identidad identidad, Hechos hechos) {
        StringBuilder sb = new StringBuilder();
        sb.append(VERSION_CANONICA).append('\n');
        sb.append("terminal:").append(texto(identidad.terminalId())).append('\n');
        sb.append("epoch:").append(entero(identidad.epoch())).append('\n');
        sb.append("seq:").append(entero(identidad.seq())).append('\n');
        sb.append("tipo:").append(texto(identidad.tipo())).append('\n');
        sb.append("ref:").append(texto(identidad.ref())).append('\n');
        sb.append("ocurrido:").append(fecha(hechos.ocurrido())).append('\n');
        sb.append("medio:").append(texto(hechos.medioDePago())).append('\n');
        sb.append("subtotal:").append(decimal(hechos.subtotal())).append('\n');
        sb.append("descuento:").append(decimal(hechos.descuento())).append('\n');
        sb.append("total:").append(decimal(hechos.total())).append('\n');

        List<Linea> lineas = hechos.lineas() == null ? List.of() : hechos.lineas();
        sb.append("lineas:").append(lineas.size()).append('\n');
        for (int i = 0; i < lineas.size(); i++) {
            Linea l = lineas.get(i);
            sb.append("  ").append(i).append(':').append(texto(l.productId()))
              .append('|').append(entero(l.quantity()))
              .append('|').append(decimal(l.unitPrice()))
              .append('|').append(decimal(l.totalPrice())).append('\n');
        }

        List<Pago> pagos = hechos.pagos() == null ? List.of() : hechos.pagos();
        sb.append("pagos:").append(pagos.size()).append('\n');
        for (int i = 0; i < pagos.size(); i++) {
            Pago p = pagos.get(i);
            sb.append("  ").append(i).append(':').append(texto(p.method()))
              .append('|').append(decimal(p.amount())).append('\n');
        }

        sb.append("anterior:").append(texto(identidad.hashAnterior())).append('\n');
        return sb.toString();
    }

    /** SHA-256 de la forma canónica en UTF-8, hexadecimal en minúsculas. */
    public static String hash(Identidad identidad, Hechos hechos) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(formaCanonica(identidad, hechos).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : d) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    // =====================================================================
    // Los cinco tipos de la gramática
    // =====================================================================

    /**
     * Escapa para que un texto no pueda inventar estructura.
     *
     * <p>La barra invertida PRIMERO, o se re-escaparían las secuencias recién
     * creadas. Mismo orden que el TypeScript.
     */
    static String escapar(String valor) {
        return valor
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("|", "\\p")
                .replace(":", "\\c");
    }

    /** Texto canónico: normalizado a NFC y luego escapado. Nulo → cadena vacía. */
    static String texto(String valor) {
        if (valor == null) {
            return "";
        }
        return escapar(Normalizer.normalize(valor, Normalizer.Form.NFC));
    }

    /**
     * Decimal canónico: dos decimales, punto, HALF_UP, sin exponente.
     *
     * <p><b>El parámetro es {@code BigDecimal} a propósito.</b> Con un
     * {@code double} la intención decimal ya se perdió y {@code 1.005} redondearía
     * a {@code 1.00}, discrepando del POS.
     *
     * <p>Nulo → cadena vacía. AUSENTE y CERO son hechos distintos: {@code
     * descuento:} no es lo mismo que {@code descuento:0.00}.
     */
    static String decimal(BigDecimal valor) {
        if (valor == null) {
            return "";
        }
        String s = valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
        // Guarda que en Java NO se dispara, y conviene que quede escrito por qué
        // está igualmente: en JavaScript `(-0.001).toFixed(2)` da "-0.00" y el
        // TypeScript necesita normalizarlo. `BigDecimal` no tiene cero negativo,
        // así que aquí `-0`, `-0.0` y `-0.001` ya dan "0.00" solos — medido en
        // `GramaticaCanonicaTest.elCeroNegativoNoExisteEnBigDecimal`.
        //
        // Se deja porque cuesta nada y porque la simetría con el TypeScript es
        // parte del contrato; pero NO cuenta como cubierta por los vectores:
        // quitarla no pone ninguno en rojo, y eso se comprobó.
        return "-0.00".equals(s) ? "0.00" : s;
    }

    /** Entero canónico. Trunca hacia cero, como {@code Math.trunc}. Nulo → vacío. */
    static String entero(Number valor) {
        return valor == null ? "" : String.valueOf(valor.longValue());
    }

    /**
     * Fecha canónica: ISO-8601 en UTC con milisegundos, siempre tres decimales.
     *
     * <p>Se normaliza a UTC a propósito: dos terminales en husos distintos que
     * registran el MISMO instante deben producir el mismo hash. Guardar el
     * desfase original es cosa de {@code ocurrido_en} en la base, no del hash.
     */
    static String fecha(Instant instante) {
        return instante == null ? "" : ISO_MILIS.format(instante);
    }

    /** Acepta lo que mande un cliente: con Z, con desfase, o nulo. */
    public static Instant instanteDe(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(iso.trim()).toInstant();
    }
}
