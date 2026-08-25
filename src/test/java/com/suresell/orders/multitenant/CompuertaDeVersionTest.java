package com.suresell.orders.multitenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * La compuerta de versión.
 *
 * <p>La mitad de estos casos comprueban que <b>NO</b> bloquea. No es relleno: la
 * regla de esta clase es la contraria a la del resto del servicio —falla
 * abierta— y la única forma de que esa regla sobreviva a un refactor es que
 * quitarla ponga tests en rojo. Ver la cabecera de {@link CompuertaDeVersion}.
 */
class CompuertaDeVersionTest {

    private CompuertaDeVersion conMinima(String mesero, String cocina) {
        CompuertaDeVersion c = new CompuertaDeVersion();
        c.fijarMinimas(mesero, cocina);
        return c;
    }

    // =====================================================================
    @Nested
    @DisplayName("Cuando SÍ debe bloquear")
    class Bloquea {

        @Test
        @DisplayName("versión por debajo del mínimo")
        void porDebajoBloquea() {
            CompuertaDeVersion c = conMinima("1.2.0", "1.2.0");
            assertTrue(c.evaluar("mesero", "1.1.9").bloquear());
            assertTrue(c.evaluar("mesero", "1.0.0").bloquear());
            assertTrue(c.evaluar("mesero", "0.9.9").bloquear());
            assertTrue(c.evaluar("cocina", "1.1.0").bloquear());
        }

        @Test
        @DisplayName("compara por componente, no como texto")
        void comparaNumerico() {
            // "1.10.0" es MAYOR que "1.9.0", aunque como cadena sea menor. Este
            // es el error clásico y llegaría justo en la décima versión.
            CompuertaDeVersion c = conMinima("1.9.0", "1.9.0");
            assertFalse(c.evaluar("mesero", "1.10.0").bloquear(), "1.10.0 >= 1.9.0");
            assertTrue(c.evaluar("mesero", "1.8.9").bloquear());
        }

        @Test
        @DisplayName("el sufijo de build de Flutter no cuenta")
        void ignoraElSufijoDeBuild() {
            // pubspec.yaml escribe "1.0.0+1"; package_info devuelve "1.0.0".
            CompuertaDeVersion c = conMinima("1.2.0", "1.2.0");
            assertTrue(c.evaluar("mesero", "1.1.0+47").bloquear());
            assertFalse(c.evaluar("mesero", "1.2.0+47").bloquear());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("🔴 Cuando debe DEJAR PASAR — la regla que no se puede perder")
    class FallaAbierta {

        @Test
        @DisplayName("versión igual al mínimo: pasa")
        void igualPasa() {
            assertFalse(conMinima("1.2.0", "1.2.0").evaluar("mesero", "1.2.0").bloquear());
        }

        @Test
        @DisplayName("versión por encima: pasa")
        void porEncimaPasa() {
            assertFalse(conMinima("1.2.0", "1.2.0").evaluar("mesero", "2.0.0").bloquear());
        }

        @Test
        @DisplayName("🔴 la variable NO está definida: pasa")
        void sinVariablePasa() {
            for (String vacia : new String[] {null, "", "   "}) {
                CompuertaDeVersion c = conMinima(vacia, vacia);
                assertFalse(c.evaluar("mesero", "0.0.1").bloquear(),
                        "sin minima configurada NO se puede bloquear a nadie");
                assertFalse(c.evaluar("cocina", "0.0.1").bloquear());
            }
        }

        @Test
        @DisplayName("🔴 la versión llega con formato raro: pasa")
        void formatoRaroPasa() {
            CompuertaDeVersion c = conMinima("1.2.0", "1.2.0");
            for (String basura : new String[] {
                    "abc", "1.2.x", "", "   ", "1.2.3.4", "-1.0.0", "v1.2.0", "??" }) {
                assertFalse(c.evaluar("mesero", basura).bloquear(),
                        "una versión que no se entiende NO puede bloquear: " + basura);
            }
        }

        @Test
        @DisplayName("🔴 el cliente no declara versión ni app: pasa")
        void clienteViejoPasa() {
            CompuertaDeVersion c = conMinima("9.9.9", "9.9.9");
            assertFalse(c.evaluar(null, null).bloquear(),
                    "un APK anterior a la compuerta no manda cabeceras; bloquearlo "
                            + "dejaría fuera a TODO el campo el día que se active");
            assertFalse(c.evaluar("mesero", null).bloquear());
            assertFalse(c.evaluar(null, "1.0.0").bloquear());
        }

        @Test
        @DisplayName("🔴 el mínimo configurado es basura: pasa")
        void minimaInvalidaPasa() {
            // Un dedazo escribiendo la variable en Railway no puede dejar sin
            // vender a nadie.
            CompuertaDeVersion c = conMinima("no-es-una-version", "1.2.x");
            assertFalse(c.evaluar("mesero", "1.0.0").bloquear());
            assertFalse(c.evaluar("cocina", "1.0.0").bloquear());
        }

        @Test
        @DisplayName("un appId desconocido pasa: esta clase no decide quién puede hablar")
        void appDesconocidaPasa() {
            CompuertaDeVersion c = conMinima("9.9.9", "9.9.9");
            assertFalse(c.evaluar("pos", "1.0.0").bloquear());
            assertFalse(c.evaluar("MESERO-RARO", "1.0.0").bloquear());
        }

        @Test
        @DisplayName("cada app tiene su mínimo: apagar una no apaga la otra")
        void losMinimosSonIndependientes() {
            CompuertaDeVersion c = conMinima("2.0.0", "");
            assertTrue(c.evaluar("mesero", "1.0.0").bloquear());
            assertFalse(c.evaluar("cocina", "1.0.0").bloquear(),
                    "cocina no tiene mínimo configurado y no debe bloquearse");
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("El login")
    class EnElLogin {

        private HttpServletRequest peticion(String app, String version) {
            HttpServletRequest http = mock(HttpServletRequest.class);
            when(http.getHeader(AuthController.CABECERA_APP)).thenReturn(app);
            when(http.getHeader(AuthController.CABECERA_VERSION)).thenReturn(version);
            when(http.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1");
            return http;
        }

        private AuthController controlador(CompuertaDeVersion c, AuthService auth) {
            return new AuthController(auth, new RegisterRateLimiter(), c);
        }

        @Test
        @DisplayName("🔴 una app vieja recibe 426 y NO se comprueban sus credenciales")
        void bloqueaAntesDeMirarLaClave() {
            AuthService auth = mock(AuthService.class);
            ResponseEntity<?> res = controlador(conMinima("2.0.0", "2.0.0"), auth)
                    .login(new AuthController.LoginRequest("a@b.co", "clave"),
                            peticion("mesero", "1.0.0"));

            assertEquals(426, res.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> cuerpo = (Map<String, Object>) res.getBody();
            assertEquals("ACTUALIZACION_REQUERIDA", cuerpo.get("error"));
            assertEquals("2.0.0", cuerpo.get("minima"));
            // Lo que de verdad prueba que la compuerta va PRIMERO: el servicio
            // de autenticación ni se llama.
            verify(auth, never()).login(anyString(), anyString());
        }

        @Test
        @DisplayName("🔴 sin variable configurada, el login sigue su curso normal")
        void sinVariableElLoginNoSeToca() {
            AuthService auth = mock(AuthService.class);
            when(auth.login(anyString(), anyString())).thenThrow(
                    new AuthException(401, "Credenciales inválidas"));

            ResponseEntity<?> res = controlador(conMinima("", ""), auth)
                    .login(new AuthController.LoginRequest("a@b.co", "mala"),
                            peticion("mesero", "0.0.1"));

            // 401, no 426: la compuerta no se metió. Si respondiera 426 aquí,
            // apagar la compuerta no la estaría apagando.
            assertEquals(401, res.getStatusCode().value());
            verify(auth).login(anyString(), anyString());
        }

        @Test
        @DisplayName("un cliente que no manda cabeceras entra igual")
        void clienteViejoEntraIgual() {
            AuthService auth = mock(AuthService.class);
            when(auth.login(anyString(), anyString())).thenThrow(
                    new AuthException(401, "Credenciales inválidas"));

            ResponseEntity<?> res = controlador(conMinima("9.9.9", "9.9.9"), auth)
                    .login(new AuthController.LoginRequest("a@b.co", "mala"),
                            peticion(null, null));

            assertEquals(401, res.getStatusCode().value());
        }

        @Test
        @DisplayName("el endpoint de arranque nunca devuelve error")
        void elEndpointDeArranqueNuncaFalla() {
            AuthController c = controlador(conMinima("2.0.0", ""), mock(AuthService.class));

            for (String[] caso : new String[][] {
                    {"mesero", "1.0.0"}, {"mesero", "3.0.0"}, {"cocina", "0.0.1"},
                    {null, null}, {"basura", "basura"}, {"mesero", null}}) {
                ResponseEntity<?> res = c.versionMinima(caso[0], caso[1]);
                assertEquals(200, res.getStatusCode().value(),
                        "el endpoint de arranque respondió algo distinto de 200 para "
                                + caso[0] + "/" + caso[1]);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> bloqueado =
                    (Map<String, Object>) c.versionMinima("mesero", "1.0.0").getBody();
            assertEquals(Boolean.TRUE, bloqueado.get("bloquear"));
            @SuppressWarnings("unchecked")
            Map<String, Object> libre =
                    (Map<String, Object>) c.versionMinima("cocina", "1.0.0").getBody();
            assertEquals(Boolean.FALSE, libre.get("bloquear"));
        }
    }
}
