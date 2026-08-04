package com.suresell.orders.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DIVISIÓN DE CUENTA entre N comensales (N3/Inc. 4 — lo último del bloque 3).
 *
 * <p>Repartir $10.000 entre 3 no da un número entero de pesos. Alguien tiene
 * que absorber la diferencia, y la decisión de negocio está tomada:
 * <strong>la absorbe el negocio, nunca el comensal</strong>.
 *
 * <p>Las tres reglas que hacen que esto no rompa la caja:
 *
 * <ol>
 *   <li><b>El total de la mesa es la fuente de verdad y nunca se modifica.</b>
 *       Esta clase no toca órdenes: solo calcula.
 *   <li><b>Nunca se redondea hacia arriba.</b> Cobrar de más a un comensal es
 *       inaceptable comercial y fiscalmente. Por eso {@code FLOOR} y no
 *       {@code HALF_UP}: con {@code HALF_UP}, repartir $10.000 entre 3 cobraría
 *       $3.333,33 → $3.333 a dos y $3.334 al tercero. Ese peso de más es el que
 *       no se cobra.
 *   <li><b>El residuo no desaparece: queda registrado.</b> Un descuadre
 *       silencioso destruye la promesa de un cierre auditable al peso. Por eso
 *       {@link Reparto#residuo()} sale del cálculo y se persiste como
 *       {@code ajuste_redondeo_negocio}.
 * </ol>
 *
 * <p>El invariante, verificado por test para todo T y todo N ∈ [2,20]:
 * <pre>suma(pagos) + ajuste_redondeo_negocio == total</pre>
 *
 * <p>Clase pura, sin Spring y sin base de datos: es la parte que tiene que ser
 * imposible de romper.
 */
public final class DivisionDeCuenta {

    /** El peso colombiano es la unidad mínima de cobro: no hay centavos en caja. */
    private static final int ESCALA_PESO = 0;

    /** Tope defensivo. Una mesa de más de 50 comensales es un error de digitación. */
    public static final int MAX_PERSONAS = 50;

    private DivisionDeCuenta() {
    }

    /**
     * Resultado del reparto.
     *
     * @param total    total de la mesa, intacto
     * @param personas entre cuántos se divide
     * @param base     lo que paga CADA comensal (pesos enteros)
     * @param residuo  lo que NO se le cobra a nadie y absorbe el negocio
     */
    public record Reparto(BigDecimal total, int personas, BigDecimal base, BigDecimal residuo) {

        /** Lo efectivamente cobrado: {@code base × personas}. */
        public BigDecimal cobrado() {
            return base.multiply(BigDecimal.valueOf(personas));
        }
    }

    /**
     * Reparte un total entre N comensales sin cobrarle de más a ninguno.
     *
     * <pre>
     * base    = floor(total / N)      // pesos enteros
     * residuo = total - (base * N)    // 0 &lt;= residuo &lt; N
     * </pre>
     *
     * <p>Restar {@code base*N} del total —en vez de calcular el módulo— hace que
     * los centavos que pueda traer el total también caigan dentro del residuo.
     * Así el invariante se cumple exacto aunque el total no sea un entero.
     */
    public static Reparto repartir(BigDecimal total, int personas) {
        if (total == null || total.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El total a dividir no puede ser negativo");
        }
        if (personas < 2) {
            throw new IllegalArgumentException("La división de cuenta necesita al menos 2 personas");
        }
        if (personas > MAX_PERSONAS) {
            throw new IllegalArgumentException(
                    "No se puede dividir entre más de " + MAX_PERSONAS + " personas");
        }
        BigDecimal base = total.divide(BigDecimal.valueOf(personas), ESCALA_PESO, RoundingMode.FLOOR);
        BigDecimal residuo = total.subtract(base.multiply(BigDecimal.valueOf(personas)));
        return new Reparto(total, personas, base, residuo);
    }

    /**
     * Agrupa por medio de pago lo que paga cada comensal.
     *
     * <p>Entra la lista de medios elegidos, uno por comensal
     * ({@code ["CASH","CARD","CASH"]}); sale {@code {CASH: 2·base, CARD: base}}.
     * El cliente elige QUIÉN paga con QUÉ; los montos los pone el servidor, así
     * ninguna caja puede mandar cifras que no cuadren.
     *
     * <p>Se conserva el orden de aparición para que el mensaje al cajero sea
     * estable entre llamadas.
     */
    public static Map<String, BigDecimal> agruparPorMetodo(Reparto reparto, List<String> metodosPorPersona) {
        if (metodosPorPersona == null || metodosPorPersona.size() != reparto.personas()) {
            throw new IllegalArgumentException(String.format(
                    "Se esperaba un medio de pago por cada una de las %d personas", reparto.personas()));
        }
        Map<String, BigDecimal> porMetodo = new LinkedHashMap<>();
        for (String metodo : metodosPorPersona) {
            porMetodo.merge(metodo, reparto.base(), BigDecimal::add);
        }
        return porMetodo;
    }

    /**
     * Reparte un monto entre varias órdenes de forma PROPORCIONAL y EXACTA.
     *
     * <p>Una cuenta de mesa suele tener varias órdenes (las rondas). El cierre
     * de caja lee los pagos por orden, así que hay que decidir cuánto de cada
     * medio le toca a cada ronda. Repartir proporcionalmente y redondear cada
     * parte por separado descuadraría el total — es exactamente el descuadre
     * que esta clase existe para evitar.
     *
     * <p>Por eso se usa el <b>método del resto mayor</b>: se asigna la parte
     * entera a cada orden y los pesos sobrantes se entregan de a uno a las
     * órdenes con mayor parte fraccionaria. La suma de las partes es
     * <b>idéntica</b> al monto de entrada, siempre.
     *
     * <p>Se reparte proporcional al total de cada orden —y no todo a la
     * primera— para que el efectivo se siga atribuyendo al mesero que tomó cada
     * ronda, igual que hacía el cobro de mesa con un solo medio.
     *
     * @param monto  monto a repartir (pesos enteros)
     * @param pesos  peso de cada orden, en el mismo orden que la salida
     * @return una parte por orden; {@code suma(partes) == monto} exacto
     */
    public static List<BigDecimal> repartirProporcional(BigDecimal monto, List<BigDecimal> pesos) {
        int n = pesos.size();
        List<BigDecimal> partes = new ArrayList<>(n);
        if (n == 0) {
            return partes;
        }
        BigDecimal sumaPesos = pesos.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        // Sin pesos (órdenes en cero) no hay proporción posible: todo a la primera.
        if (sumaPesos.compareTo(BigDecimal.ZERO) <= 0) {
            partes.add(monto.setScale(ESCALA_PESO, RoundingMode.FLOOR));
            for (int i = 1; i < n; i++) {
                partes.add(BigDecimal.ZERO.setScale(ESCALA_PESO));
            }
            return partes;
        }

        List<BigDecimal> restos = new ArrayList<>(n);
        BigDecimal asignado = BigDecimal.ZERO;
        for (BigDecimal peso : pesos) {
            BigDecimal exacto = monto.multiply(peso).divide(sumaPesos, 10, RoundingMode.HALF_UP);
            BigDecimal entero = exacto.setScale(ESCALA_PESO, RoundingMode.FLOOR);
            partes.add(entero);
            restos.add(exacto.subtract(entero));
            asignado = asignado.add(entero);
        }

        // Los pesos que quedaron sueltos van, de a uno, a los restos más grandes.
        BigDecimal sobrante = monto.setScale(ESCALA_PESO, RoundingMode.FLOOR).subtract(asignado);
        List<Integer> orden = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            orden.add(i);
        }
        orden.sort(Comparator.<Integer, BigDecimal>comparing(restos::get).reversed());

        // Cada parte fraccionaria es < 1, así que el sobrante siempre es < n y
        // una sola vuelta alcanza. El recorrido cíclico es blindaje, no diseño.
        for (int i = 0; sobrante.compareTo(BigDecimal.ZERO) > 0; i++) {
            int idx = orden.get(i % orden.size());
            partes.set(idx, partes.get(idx).add(BigDecimal.ONE));
            sobrante = sobrante.subtract(BigDecimal.ONE);
        }
        return partes;
    }
}
