package com.suresell.orders.application.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.suresell.orders.domain.model.ResultadoQr;
import com.suresell.orders.infrastructure.web.TokenDeLaPeticion;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Resuelve el monto de QR de un día contra `ms-core-app`, diciendo SIEMPRE de
 * dónde salió el número.
 *
 * <h3>El incidente que originó esta clase</h3>
 *
 * El cierre consultaba `/qr-payments/by-date` con
 * {@code restTemplate.getForEntity(url, JsonNode.class)} — sin cabeceras. El
 * 2026-07-30 se añadió {@code JwtTenantFilter} a `ms-core-app` y esa ruta pasó a
 * exigir un JWT de negocio, así que empezó a devolver 401. El llamador atrapaba
 * toda excepción, registraba un {@code log.warn} que además culpaba a "posible
 * falta de internet", y **cuadraba el cierre con el valor manual del cajero**.
 *
 * Tres semanas de cierres cuadrados con otro número, y en la base de datos no
 * quedaba ni rastro de que hubiera pasado algo. Ese es el defecto real: no el
 * 401, sino que el dato resultante fuera indistinguible de uno conciliado.
 *
 * <h3>Qué hace distinto</h3>
 *
 * <ol>
 *   <li><b>Propaga el JWT</b> de la petición en curso ({@link TokenDeLaPeticion}).</li>
 *   <li><b>Tiene timeouts.</b> Sin ellos, un `ms-core-app` lento cuelga el cierre
 *       de caja indefinidamente: el cajero se queda con la pantalla bloqueada y
 *       el local sin poder cerrar.</li>
 *   <li><b>Nunca devuelve un número pelado.</b> Devuelve {@link ResultadoQr}, que
 *       lleva fuente y nivel de confianza (reglas 5 y 6 de
 *       LINEAMIENTOS_DESARROLLO_DATA_FIRST).</li>
 *   <li><b>Distingue "no hay nada" de "no pude saberlo".</b> Ver abajo.</li>
 * </ol>
 *
 * <h3>404 no es un fallo</h3>
 *
 * `ms-core-app` responde 404 cuando no hay pago QR registrado para esa fecha
 * (`QrPaymentController.java:35-37`, un {@code orElse(notFound())}). Eso NO es un
 * error de integración: es la respuesta correcta a "¿hay algo?" cuando no hay
 * nada. Se registra como {@code manual_cajero}, porque el número que acaba en el
 * cierre es el del cajero.
 *
 * No se registra como {@code conciliado_core} con monto cero: un 404 significa
 * "no hay registro", no "el registro dice cero". Afirmar una conciliación que no
 * ocurrió es exactamente lo que esta clase viene a impedir.
 *
 * <p>Hasta ahora los dos casos —404 y 401— caían en el mismo {@code catch} y
 * producían el mismo resultado. Separarlos es lo que hace el problema detectable.
 */
@Log4j2
@Component
public class ConciliadorDeQr {

    /**
     * Timeouts deliberadamente cortos. Esto corre dentro de la transacción del
     * cierre, con el cajero esperando: es preferible cerrar marcando
     * `fallo_integracion` que dejar el local sin poder cerrar la caja. El peor
     * caso posible es la suma de los dos, ~8 s.
     */
    static final Duration TIMEOUT_CONEXION = Duration.ofSeconds(3);
    static final Duration TIMEOUT_LECTURA = Duration.ofSeconds(5);

    private final RestTemplate restTemplate;
    private final TokenDeLaPeticion token;

    @Value("${sync.cloud.core-url:http://localhost:8083/api/core}")
    private String coreApiUrl;

    /**
     * El {@code @Autowired} es obligatorio: hay dos constructores y Spring solo
     * elige solo cuando hay uno. Sin esto el contexto no levanta.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public ConciliadorDeQr(TokenDeLaPeticion token) {
        this(token, conTimeouts());
    }

    /** Para los tests: permite inyectar un RestTemplate con MockRestServiceServer. */
    ConciliadorDeQr(TokenDeLaPeticion token, RestTemplate restTemplate) {
        this.token = token;
        this.restTemplate = restTemplate;
    }

    /** Para los tests, que no pasan por la inyección de {@code @Value}. */
    void fijarUrlDeCore(String url) {
        this.coreApiUrl = url;
    }

    private static RestTemplate conTimeouts() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(TIMEOUT_CONEXION);
        fabrica.setReadTimeout(TIMEOUT_LECTURA);
        return new RestTemplate(fabrica);
    }

    /**
     * @param fecha           día que se está cerrando
     * @param valorDelCajero  lo que tecleó el cajero; es el respaldo cuando no
     *                        hay conciliación posible
     */
    public ResultadoQr resolver(LocalDate fecha, BigDecimal valorDelCajero) {
        String url = coreApiUrl + "/qr-payments/by-date?date=" + fecha;
        try {
            ResponseEntity<JsonNode> respuesta = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(cabeceras()), JsonNode.class);

            if (!respuesta.getStatusCode().is2xxSuccessful() || respuesta.getBody() == null) {
                return ResultadoQr.fallo(valorDelCajero,
                        "Respuesta inesperada de ms-core-app: HTTP " + respuesta.getStatusCode().value()
                                + (respuesta.getBody() == null ? " con cuerpo vacio" : ""));
            }

            JsonNode monto = respuesta.getBody().get("amount");
            if (monto == null || monto.isNull()) {
                return ResultadoQr.fallo(valorDelCajero,
                        "ms-core-app respondio 200 sin el campo 'amount'");
            }

            ResultadoQr conciliado = ResultadoQr.conciliado(new BigDecimal(monto.asText()));
            log.info("Cierre: QR conciliado contra ms-core-app = {}", conciliado.monto());
            return conciliado;

        } catch (HttpClientErrorException.NotFound e) {
            // Caso legítimo: no hay pago QR registrado ese día. No es un fallo.
            log.info("Cierre: ms-core-app no tiene pago QR para {}; se usa el valor del cajero", fecha);
            return ResultadoQr.manual(valorDelCajero);

        } catch (Exception e) {
            String detalle = describir(e);
            // WARN y no ERROR: el cierre se completa. Lo que hace este problema
            // detectable no es el log —que ya existía y no sirvió de nada— sino
            // la columna qr_fuente que queda en la fila.
            log.warn("Cierre: no se pudo conciliar el QR contra ms-core-app ({}). "
                    + "Se usa el valor del cajero y el cierre queda marcado como fallo_integracion.", detalle);
            return ResultadoQr.fallo(valorDelCajero, detalle);
        }
    }

    private HttpHeaders cabeceras() {
        HttpHeaders cabeceras = new HttpHeaders();
        // Sin token no se inventa nada: la llamada sale sin credencial, recibe
        // 401 y el cierre queda marcado como fallo_integracion, que es la verdad.
        token.cabeceraAuthorization()
                .ifPresent(valor -> cabeceras.set(HttpHeaders.AUTHORIZATION, valor));
        return cabeceras;
    }

    /**
     * Mensaje técnico REAL del fallo, con el código HTTP cuando lo hay. Nunca una
     * explicación inventada: el mensaje anterior decía "posible falta de
     * internet" y mandó a buscar el problema donde no estaba durante tres
     * semanas.
     *
     * <p>Se recorta a 500 caracteres: la columna es para diagnosticar, no para
     * volcar trazas.
     */
    private String describir(Exception e) {
        String texto;
        if (e instanceof HttpClientErrorException http) {
            HttpStatus estado = HttpStatus.resolve(http.getStatusCode().value());
            texto = "HTTP " + http.getStatusCode().value()
                    + (estado != null ? " " + estado.getReasonPhrase() : "")
                    + " de ms-core-app";
            if (http.getStatusCode().value() == 401 || http.getStatusCode().value() == 403) {
                texto += " (el JWT no llego o no es valido)";
            }
        } else {
            texto = e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage());
        }
        return texto.length() > 500 ? texto.substring(0, 500) : texto;
    }
}
