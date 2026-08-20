package com.suresell.orders.multitenant;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints de autenticación (perfil `cloud`). Exentos del {@link TenantContextFilter}
 * (si no, haría falta un token para pedir un token). Ver docs/110-plan-auth-real.md.
 *
 * - POST /auth/login           → credenciales de usuario (email+clave); deriva el tenant. Rate-limited por fallos.
 * - POST /auth/register        → alta self-service de un negocio (crea tenant + admin); rate-limited.
 * - POST /auth/forgot-password → envía el enlace de restablecimiento; rate-limited.
 * - POST /auth/reset-password  → consume el token de un solo uso.
 *
 * La lógica vive en {@link AuthService}; aquí solo se mapea HTTP y errores. El login
 * legacy por clave compartida (/auth/token) se eliminó tras migrar el front a /auth/login.
 */
@RestController
@Profile("cloud")
public class AuthController {

    private final AuthService auth;
    private final RegisterRateLimiter rateLimiter;

    public AuthController(AuthService auth, RegisterRateLimiter rateLimiter) {
        this.auth = auth;
        this.rateLimiter = rateLimiter;
    }

    /**
     * El cupo se comprueba ANTES de intentar y solo se consume si las
     * credenciales fallan; un login correcto además borra los fallos previos de
     * esa IP. Así un local con varias cajas detrás de la misma IP pública nunca
     * se queda fuera por escribir bien, y la fuerza bruta —que es solo
     * fallos— choca contra el muro enseguida. Ver {@link RegisterRateLimiter}.
     */
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletRequest http) {
        String ip = clientIp(http);
        try {
            rateLimiter.verificarCupo(RegisterRateLimiter.Bucket.LOGIN, ip);
            var resultado = auth.login(req.email(), req.password());
            rateLimiter.limpiar(RegisterRateLimiter.Bucket.LOGIN, ip);
            return ResponseEntity.ok(resultado);
        } catch (AuthException e) {
            // El propio 429 no gasta cupo: si no, un cliente que reintenta contra
            // el muro extendería su bloqueo indefinidamente.
            if (e.status() != 429) {
                rateLimiter.anotarIntento(RegisterRateLimiter.Bucket.LOGIN, ip);
            }
            return error(e);
        }
    }

    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req, HttpServletRequest http) {
        try {
            rateLimiter.check(clientIp(http));
            return ResponseEntity.ok(auth.register(
                    req.businessName(), req.email(), req.password(), req.registrationKey(),
                    req.nit(), req.address(), req.phone()));
        } catch (AuthException e) {
            return error(e);
        }
    }

    /** IP del cliente respetando el proxy de Railway (X-Forwarded-For, primer salto). */
    private String clientIp(HttpServletRequest http) {
        String fwd = http.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }

    /**
     * Aquí se cuentan TODOS los intentos, no solo los fallidos: este endpoint
     * responde lo mismo exista o no el email (para no filtrar quién tiene
     * cuenta), así que "fallo" no significa nada. Lo que hay que frenar es el
     * volumen — mandar cien correos de recuperación al buzón de alguien, o
     * medir tiempos de respuesta para enumerar cuentas.
     */
    @PostMapping("/auth/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotRequest req, HttpServletRequest http) {
        try {
            String ip = clientIp(http);
            rateLimiter.verificarCupo(RegisterRateLimiter.Bucket.RECUPERACION, ip);
            rateLimiter.anotarIntento(RegisterRateLimiter.Bucket.RECUPERACION, ip);
            var r = auth.forgotPassword(req.email());
            // link SOLO viene en staging (expose-link); en prod es null.
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("message", "Si el email existe, enviamos un enlace para restablecer la contraseña.");
            if (r.link() != null) {
                body.put("resetLink", r.link());
            }
            return ResponseEntity.ok(body);
        } catch (AuthException e) {
            return error(e);
        }
    }

    /**
     * Comparte el cupo de {@link RegisterRateLimiter.Bucket#RECUPERACION} con
     * {@code /auth/forgot-password}: los dos son pasos del mismo flujo, así que
     * un cupo común es lo que de verdad acota el abuso del flujo entero.
     *
     * <p>El token es de un solo uso, con expiración y guardado como hash
     * SHA-256 ({@code V9:11-18}), así que adivinarlo ya era difícil. Pero sin
     * cupo se podían probar tokens indefinidamente, y eso no tenía por qué ser
     * gratis.
     *
     * <p>Se cuentan TODOS los intentos, no solo los fallidos: un reset correcto
     * ocurre una vez, no diez. A diferencia del login, aquí el volumen legítimo
     * es tan bajo que no hay riesgo de dejar fuera a nadie.
     */
    @PostMapping("/auth/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetRequest req, HttpServletRequest http) {
        try {
            String ip = clientIp(http);
            rateLimiter.verificarCupo(RegisterRateLimiter.Bucket.RECUPERACION, ip);
            rateLimiter.anotarIntento(RegisterRateLimiter.Bucket.RECUPERACION, ip);
            auth.resetPassword(req.token(), req.newPassword());
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (AuthException e) {
            return error(e);
        }
    }

    private ResponseEntity<?> error(AuthException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }

    public record LoginRequest(String email, String password) {}

    public record ForgotRequest(String email) {}

    public record ResetRequest(String token, String newPassword) {}

    public record RegisterRequest(String businessName, String email, String password,
                                  String registrationKey,
                                  String nit, String address, String phone) {}
}
