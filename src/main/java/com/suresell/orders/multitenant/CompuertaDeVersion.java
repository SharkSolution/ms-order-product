package com.suresell.orders.multitenant;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Decide si una app móvil es demasiado vieja para seguir operando.
 *
 * <h3>Por qué existe</h3>
 *
 * Las apps de mesero y cocina solo se actualizan yendo comercio por comercio.
 * Sin una forma de exigir una versión mínima, <b>cada cambio futuro en esas apps
 * obliga a otra ronda presencial</b> — y mientras tanto conviven en campo
 * versiones que mandan contratos distintos. No es hipotético: `NEQUI` se retiró
 * en N2/6.6 y en los últimos 90 días llegaron 38 órdenes con ese medio de pago,
 * desde APKs que nadie ha podido actualizar.
 *
 * <h3>🔴 ESTA COMPROBACIÓN FALLA ABIERTA. NO LA "ARREGLES".</h3>
 *
 * Todo el resto de este servicio falla cerrado: sin negocio en sesión no se ve
 * ninguna fila, sin medio de pago no se registra la orden, sin token no se entra.
 * <b>Aquí es al revés, y es deliberado.</b>
 *
 * <p>Si la variable no está definida, si la versión llega en un formato que no se
 * entiende, si la comparación lanza — <b>la app trabaja</b>. Una compuerta de
 * versión defectuosa que falle cerrada deja a todos los meseros sin poder tomar
 * pedidos a media hora del almuerzo, y arreglarlo exige exactamente la ronda
 * presencial que esta clase viene a eliminar. El fallo sería peor que el
 * problema.
 *
 * <p>Fallar cerrado protege el dato. <b>Aquí lo que hay que proteger es la
 * operación.</b> Un dispositivo desactualizado de más es un inconveniente; un
 * local entero sin poder vender es una pérdida.
 *
 * <p>Por la misma razón el mínimo sale de una <b>variable de entorno</b> y no de
 * una tabla: leerlo de la base metería una consulta nueva en el camino del login,
 * y con ella una forma nueva de que el login falle. Una {@code @Value} ausente no
 * falla: devuelve vacío, y vacío significa "no bloquear".
 *
 * <h3>Dónde se configura</h3>
 *
 * <pre>
 *   APP_VERSION_MINIMA_MESERO=1.1.0     (app.version-minima.mesero)
 *   APP_VERSION_MINIMA_COCINA=1.1.0     (app.version-minima.cocina)
 * </pre>
 *
 * Sin valor, la compuerta de esa app está apagada. Cambiarlas es cambiar una
 * variable del servicio: no hay que compilar ni publicar nada.
 */
@Component
public class CompuertaDeVersion {

    /** Apps que pueden pedir permiso. Enum CERRADO, sin "otros" (regla 10). */
    public enum App {
        mesero,
        cocina
    }

    /**
     * @param bloquear si la app debe mostrar la pantalla de actualización
     * @param minima   la versión exigida, o null si no hay compuerta activa
     * @param motivo   por qué se dejó pasar o se bloqueó. Para el log y para los
     *                 tests; NO viaja al cliente
     */
    public record Veredicto(boolean bloquear, String minima, String motivo) {}

    private static final Veredicto PASA_SIN_COMPUERTA =
            new Veredicto(false, null, "sin version minima configurada");

    @Value("${app.version-minima.mesero:}")
    private String minimaMesero;

    @Value("${app.version-minima.cocina:}")
    private String minimaCocina;

    /** Para los tests, que no pasan por la inyección de {@code @Value}. */
    void fijarMinimas(String mesero, String cocina) {
        this.minimaMesero = mesero;
        this.minimaCocina = cocina;
    }

    /**
     * @param appId   identificador de la app tal como llega del cliente. Puede ser
     *                null (cliente viejo que no manda nada)
     * @param version versión del cliente. Puede ser null, vacía o basura
     */
    public Veredicto evaluar(String appId, String version) {
        try {
            App app = appDe(appId);
            if (app == null) {
                // Cliente viejo, o un appId que no reconocemos. No es asunto de
                // esta clase decidir quién puede hablar: eso lo hace el login.
                return new Veredicto(false, null, "app no reconocida: " + appId);
            }
            String minima = minimaDe(app);
            if (esVacio(minima)) {
                return PASA_SIN_COMPUERTA;
            }
            if (esVacio(version)) {
                // Un cliente que no declara versión es, por definición, uno
                // anterior a que esto existiera. Bloquearlo dejaría fuera a
                // TODOS los dispositivos de campo el día que se active la
                // compuerta — que es el escenario exacto que hay que evitar.
                return new Veredicto(false, minima, "el cliente no declara version");
            }
            int[] v = parsear(version);
            int[] m = parsear(minima);
            if (v == null || m == null) {
                return new Veredicto(false, minima, "version no interpretable: " + version);
            }
            boolean vieja = comparar(v, m) < 0;
            return new Veredicto(vieja, minima,
                    vieja ? "version por debajo del minimo" : "version suficiente");
        } catch (RuntimeException e) {
            // La red de seguridad. Cualquier cosa que no se haya previsto acaba
            // aquí y deja pasar. Si algún día este catch se quita "porque no
            // debería hacer falta", léase la cabecera de la clase otra vez.
            return new Veredicto(false, null, "error evaluando: " + e.getClass().getSimpleName());
        }
    }

    private static App appDe(String appId) {
        if (esVacio(appId)) {
            return null;
        }
        try {
            return App.valueOf(appId.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String minimaDe(App app) {
        return app == App.mesero ? minimaMesero : minimaCocina;
    }

    /**
     * "1.2.3" → {1,2,3}. Devuelve null si no se entiende, y null significa pasar.
     *
     * <p>Acepta menos de tres partes ("1.2" → {1,2,0}) y descarta cualquier
     * sufijo tras un guion o un más ("1.2.3+45" → {1,2,3}), que es como Flutter
     * escribe la versión en `pubspec.yaml`.
     */
    static int[] parsear(String version) {
        if (esVacio(version)) {
            return null;
        }
        String limpia = version.trim().split("[-+]", 2)[0];
        String[] partes = limpia.split("\\.");
        if (partes.length == 0 || partes.length > 3) {
            return null;
        }
        int[] n = new int[] {0, 0, 0};
        for (int i = 0; i < partes.length; i++) {
            try {
                n[i] = Integer.parseInt(partes[i].trim());
            } catch (NumberFormatException e) {
                return null;
            }
            if (n[i] < 0) {
                return null;
            }
        }
        return n;
    }

    private static int comparar(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return 0;
    }

    private static boolean esVacio(String s) {
        return s == null || s.trim().isEmpty();
    }
}
