package com.suresell.orders.multitenant;

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
 * - POST /auth/register → alta self-service de un negocio (crea tenant + admin).
 * - POST /auth/token    → LEGACY (clave compartida + tenant tecleado); deprecar.
 *
 * La lógica vive en {@link AuthService}; aquí solo se mapea HTTP y errores.
 */
@RestController
@Profile("cloud")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
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
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        try {
            return ResponseEntity.ok(auth.register(req.businessName(), req.email(), req.password()));
        } catch (AuthException e) {
            return error(e);
        }
    }

    /** LEGACY — mantiene vivo staging mientras el front migra a /auth/login. */
    @PostMapping("/auth/token")
    public ResponseEntity<?> token(@RequestBody TokenRequest req) {
        try {
            var res = auth.legacyToken(req.tenantId(), req.userName(), req.password());
            return ResponseEntity.ok(Map.of("token", res.token(), "tenantId", res.tenantId()));
        } catch (AuthException e) {
            return error(e);
        }
    }

    private ResponseEntity<?> error(AuthException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }

    public record LoginRequest(String email, String password) {}

    public record RegisterRequest(String businessName, String email, String password) {}

    public record TokenRequest(String tenantId, String userName, String password) {}
}
