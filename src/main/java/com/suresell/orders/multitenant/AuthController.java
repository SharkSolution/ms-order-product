package com.suresell.orders.multitenant;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Emite el JWT de tenant que el resto de endpoints exige (lo valida
 * {@link JwtTenantResolver}). SOLO en el perfil `cloud`. Está exento del
 * {@link TenantContextFilter} (si no, haría falta un token para pedir un token).
 *
 * Auth mínima para F1/staging: se valida una clave compartida (`auth.login.password`,
 * por env) y se emite un token firmado con `tenant_id`. NO es gestión de usuarios
 * real — eso llega con el panel SaaS (ver docs/50). Si `auth.login.password` está
 * vacío, no se exige clave (modo demo local).
 */
@RestController
@Profile("cloud")
public class AuthController {

    private final SecretKey key;
    private final String loginPassword;
    private final long ttlSeconds;

    public AuthController(
            @Value("${security.jwt.secret:cambia-esta-clave-en-produccion-min-32-bytes!}") String secret,
            @Value("${auth.login.password:}") String loginPassword,
            @Value("${auth.token.ttl-seconds:43200}") long ttlSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.loginPassword = loginPassword;
        this.ttlSeconds = ttlSeconds;
    }

    @PostMapping("/auth/token")
    public ResponseEntity<?> token(@RequestBody TokenRequest req) {
        if (loginPassword != null && !loginPassword.isBlank()
                && !loginPassword.equals(req.password())) {
            return ResponseEntity.status(401).body(Map.of("error", "Clave de acceso inválida"));
        }
        if (req.tenantId() == null || req.tenantId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "tenantId es requerido"));
        }

        Instant now = Instant.now();
        String jwt = Jwts.builder()
                .claim("tenant_id", req.tenantId())
                .subject(req.userName() == null ? "" : req.userName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();

        return ResponseEntity.ok(Map.of("token", jwt, "tenantId", req.tenantId()));
    }

    public record TokenRequest(String tenantId, String userName, String password) {}
}
