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
 * - POST /auth/login    → credenciales de usuario (email+clave); deriva el tenant.
 * - POST /auth/register → alta self-service de un negocio (crea tenant + admin); rate-limited.
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

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        try {
            return ResponseEntity.ok(auth.login(req.email(), req.password()));
        } catch (AuthException e) {
            return error(e);
        }
    }

    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req, HttpServletRequest http) {
        try {
            rateLimiter.check(clientIp(http));
            return ResponseEntity.ok(auth.register(
                    req.businessName(), req.email(), req.password(), req.registrationKey()));
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

    private ResponseEntity<?> error(AuthException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }

    public record LoginRequest(String email, String password) {}

    public record RegisterRequest(String businessName, String email, String password,
                                  String registrationKey) {}
}
