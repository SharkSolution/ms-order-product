package com.suresell.orders.multitenant;

import com.suresell.orders.multitenant.RegisterRateLimiter.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * El cupo de intentos de {@code /auth/login} y {@code /auth/forgot-password}
 * dispara de verdad.
 *
 * <p>Hasta ahora el único endpoint de autenticación con cupo era
 * {@code /auth/register}: la clave del administrador de un negocio se podía
 * probar sin límite, mientras que el PIN de cuatro dígitos de un mesero sí
 * tenía bloqueo por intentos fallidos.
 *
 * <p>Se prueba contra el controlador y no solo contra el limitador porque lo
 * que se rompió antes no fue el limitador —que ya funcionaba— sino que nadie lo
 * llamara. El test que importa es el que falla si alguien quita la llamada.
 */
class RateLimitDeAutenticacionTest {

    private static final String IP = "200.1.2.3";

    private HttpServletRequest peticionDesde(String ip) {
        HttpServletRequest http = mock(HttpServletRequest.class);
        when(http.getHeader("X-Forwarded-For")).thenReturn(ip);
        when(http.getRemoteAddr()).thenReturn(ip);
        return http;
    }

    /** Una respuesta de login válida cualquiera; su contenido no importa aquí. */
    private AuthService.AuthResponse respuestaOk() {
        return new AuthService.AuthResponse(
                "token", "negocio-demo", "Negocio Demo", "pro", "Admin", "admin",
                null, null, null, null, java.util.List.of("ventas"));
    }

    /** Extrae el "error" del cuerpo de la respuesta, que siempre es un Map. */
    @SuppressWarnings("unchecked")
    private String errorDe(org.springframework.http.ResponseEntity<?> res) {
        Object body = res.getBody();
        assertNotNull(body, "la respuesta de error debe traer cuerpo");
        return String.valueOf(((java.util.Map<String, Object>) body).get("error"));
    }

    // =====================================================================
    @Nested
    @DisplayName("POST /auth/login")
    class Login {

        @Test
        @DisplayName("tras agotar el cupo de fallos responde 429 y deja de consultar credenciales")
        void bloqueaTrasElCupoDeFallos() {
            AuthService auth = mock(AuthService.class);
            when(auth.login(anyString(), anyString()))
                    .thenThrow(new AuthException(401, "Credenciales inválidas"));
            AuthController controller = new AuthController(auth, new RegisterRateLimiter());
            var req = new AuthController.LoginRequest("admin@negocio.co", "clave-mala");
            var http = peticionDesde(IP);

            // Los primeros intentos fallan por credenciales, no por cupo.
            for (int i = 0; i < RegisterRateLimiter.MAX_LOGINS_FALLIDOS; i++) {
                assertEquals(401, controller.login(req, http).getStatusCode().value(),
                        "el intento " + (i + 1) + " debía fallar por credenciales, no por cupo");
            }

            var bloqueado = controller.login(req, http);
            assertEquals(429, bloqueado.getStatusCode().value());
            assertTrue(errorDe(bloqueado).toLowerCase().contains("demasiados"));

            // La clave es esta: una vez bloqueada la IP, el servicio de
            // autenticación ya ni se consulta. Si el 429 se devolviera DESPUÉS de
            // verificar la contraseña, el limitador no serviría para nada: el
            // atacante seguiría midiendo el coste del BCrypt.
            verify(auth, times(RegisterRateLimiter.MAX_LOGINS_FALLIDOS))
                    .login(anyString(), anyString());
        }

        @Test
        @DisplayName("un login correcto no consume cupo")
        void elExitoNoGastaCupo() {
            AuthService auth = mock(AuthService.class);
            when(auth.login(anyString(), anyString())).thenReturn(respuestaOk());
            AuthController controller = new AuthController(auth, new RegisterRateLimiter());
            var req = new AuthController.LoginRequest("admin@negocio.co", "clave-buena");
            var http = peticionDesde(IP);

            // Muchos más que el cupo: un local con varias cajas detrás de la
            // misma IP pública no puede quedarse fuera por trabajar.
            for (int i = 0; i < RegisterRateLimiter.MAX_LOGINS_FALLIDOS * 3; i++) {
                assertEquals(200, controller.login(req, http).getStatusCode().value());
            }
        }

        @Test
        @DisplayName("acertar la clave borra los fallos acumulados")
        void elExitoLimpiaLosFallosPrevios() {
            AuthService auth = mock(AuthService.class);
            AuthController controller = new AuthController(auth, new RegisterRateLimiter());
            var http = peticionDesde(IP);
            var mala = new AuthController.LoginRequest("admin@negocio.co", "mala");
            var buena = new AuthController.LoginRequest("admin@negocio.co", "buena");

            when(auth.login(anyString(), eq("mala")))
                    .thenThrow(new AuthException(401, "Credenciales inválidas"));
            when(auth.login(anyString(), eq("buena"))).thenReturn(respuestaOk());

            // El cajero se equivoca casi hasta el tope...
            for (int i = 0; i < RegisterRateLimiter.MAX_LOGINS_FALLIDOS - 1; i++) {
                controller.login(mala, http);
            }
            // ...y acierta.
            assertEquals(200, controller.login(buena, http).getStatusCode().value());

            // No debe arrastrar los fallos anteriores: vuelve a tener el cupo entero.
            for (int i = 0; i < RegisterRateLimiter.MAX_LOGINS_FALLIDOS; i++) {
                assertEquals(401, controller.login(mala, http).getStatusCode().value(),
                        "el cupo no se reinició tras el login correcto");
            }
            assertEquals(429, controller.login(mala, http).getStatusCode().value());
        }

        @Test
        @DisplayName("el cupo es por IP: bloquear una no bloquea a otra")
        void elCupoEsPorIp() {
            AuthService auth = mock(AuthService.class);
            when(auth.login(anyString(), anyString()))
                    .thenThrow(new AuthException(401, "Credenciales inválidas"));
            AuthController controller = new AuthController(auth, new RegisterRateLimiter());
            var req = new AuthController.LoginRequest("admin@negocio.co", "mala");

            var atacante = peticionDesde("6.6.6.6");
            for (int i = 0; i < RegisterRateLimiter.MAX_LOGINS_FALLIDOS; i++) {
                controller.login(req, atacante);
            }
            assertEquals(429, controller.login(req, atacante).getStatusCode().value());

            // El negocio de al lado sigue pudiendo trabajar.
            assertEquals(401, controller.login(req, peticionDesde("190.5.5.5"))
                    .getStatusCode().value());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("POST /auth/forgot-password")
    class Recuperacion {

        @Test
        @DisplayName("tras agotar el cupo responde 429 y deja de enviar correos")
        void bloqueaTrasElCupo() {
            AuthService auth = mock(AuthService.class);
            when(auth.forgotPassword(anyString()))
                    .thenReturn(new AuthService.ForgotResponse(true, null));
            AuthController controller = new AuthController(auth, new RegisterRateLimiter());
            var req = new AuthController.ForgotRequest("victima@negocio.co");
            var http = peticionDesde(IP);

            for (int i = 0; i < RegisterRateLimiter.MAX_RECUPERACIONES; i++) {
                assertEquals(200, controller.forgotPassword(req, http).getStatusCode().value());
            }

            var bloqueado = controller.forgotPassword(req, http);
            assertEquals(429, bloqueado.getStatusCode().value());

            // No basta con devolver 429: el correo no puede haberse mandado.
            // Sin esto, el endpoint seguiría sirviendo para bombardear un buzón.
            verify(auth, times(RegisterRateLimiter.MAX_RECUPERACIONES))
                    .forgotPassword(anyString());
        }

        @Test
        @DisplayName("cuenta todos los intentos, no solo los de emails que existen")
        void cuentaTodosLosIntentos() {
            AuthService auth = mock(AuthService.class);
            when(auth.forgotPassword(anyString()))
                    .thenReturn(new AuthService.ForgotResponse(true, null));
            AuthController controller = new AuthController(auth, new RegisterRateLimiter());
            var http = peticionDesde(IP);

            // Emails distintos en cada intento: así es como se enumera. El cupo
            // va por IP, así que rotar el email no lo esquiva.
            for (int i = 0; i < RegisterRateLimiter.MAX_RECUPERACIONES; i++) {
                controller.forgotPassword(
                        new AuthController.ForgotRequest("probando" + i + "@negocio.co"), http);
            }
            assertEquals(429, controller.forgotPassword(
                    new AuthController.ForgotRequest("otro@negocio.co"), http)
                    .getStatusCode().value());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("POST /auth/reset-password")
    class Reset {

        @Test
        @DisplayName("tras agotar el cupo responde 429 y deja de probar tokens")
        void bloqueaTrasElCupo() {
            AuthService auth = mock(AuthService.class);
            AuthController controller = new AuthController(auth, new RegisterRateLimiter());
            var req = new AuthController.ResetRequest("token-a-probar", "ClaveNueva123");
            var http = peticionDesde(IP);

            for (int i = 0; i < RegisterRateLimiter.MAX_RECUPERACIONES; i++) {
                assertEquals(200, controller.resetPassword(req, http).getStatusCode().value());
            }

            assertEquals(429, controller.resetPassword(req, http).getStatusCode().value());

            // El token no se puede seguir probando una vez agotado el cupo.
            verify(auth, times(RegisterRateLimiter.MAX_RECUPERACIONES))
                    .resetPassword(anyString(), anyString());
        }

        @Test
        @DisplayName("comparte cupo con forgot-password: son el mismo flujo")
        void comparteCupoConForgot() {
            AuthService auth = mock(AuthService.class);
            when(auth.forgotPassword(anyString()))
                    .thenReturn(new AuthService.ForgotResponse(true, null));
            AuthController controller = new AuthController(auth, new RegisterRateLimiter());
            var http = peticionDesde(IP);

            // Agotar el cupo pidiendo enlaces...
            for (int i = 0; i < RegisterRateLimiter.MAX_RECUPERACIONES; i++) {
                controller.forgotPassword(new AuthController.ForgotRequest("a@b.co"), http);
            }

            // ...deja también sin cupo el consumo de tokens. Si fueran cupos
            // separados, el atacante tendría el doble de intentos sobre el
            // mismo flujo.
            assertEquals(429, controller.resetPassword(
                    new AuthController.ResetRequest("t", "ClaveNueva123"), http)
                    .getStatusCode().value());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("Los cupos no se pisan entre sí")
    class CuposIndependientes {

        @Test
        @DisplayName("agotar el de recuperación no bloquea el login de esa IP")
        void sonIndependientes() {
            RegisterRateLimiter limiter = new RegisterRateLimiter();

            for (int i = 0; i < RegisterRateLimiter.MAX_RECUPERACIONES; i++) {
                limiter.anotarIntento(Bucket.RECUPERACION, IP);
            }
            assertThrows(AuthException.class,
                    () -> limiter.verificarCupo(Bucket.RECUPERACION, IP));

            // Quedarse sin cupo para recuperar la clave no puede impedir entrar
            // a quien sí la recuerda.
            assertDoesNotThrow(() -> limiter.verificarCupo(Bucket.LOGIN, IP));
            assertDoesNotThrow(() -> limiter.verificarCupo(Bucket.REGISTRO, IP));
        }

        @Test
        @DisplayName("verificarCupo no consume: se puede llamar sin gastar intentos")
        void verificarNoConsume() {
            RegisterRateLimiter limiter = new RegisterRateLimiter();
            for (int i = 0; i < RegisterRateLimiter.MAX_LOGINS_FALLIDOS * 5; i++) {
                assertDoesNotThrow(() -> limiter.verificarCupo(Bucket.LOGIN, IP));
            }
        }
    }
}
