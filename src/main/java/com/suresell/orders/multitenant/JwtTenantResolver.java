package com.suresell.orders.multitenant;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Extrae el `tenant_id` de un JWT firmado (HS256). El secreto viene de
 * configuración (variable de entorno en producción). Ver docs/40-multitenant.md.
 */
@Component
public class JwtTenantResolver {

    private final SecretKey key;

    public JwtTenantResolver(
            @Value("${security.jwt.secret:cambia-esta-clave-en-produccion-min-32-bytes!}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Devuelve el tenant_id si el token es válido y lo contiene; si no, vacío. */
    public Optional<String> resolveTenant(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return Optional.empty();
        }
        String token = authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7)
                : authorizationHeader;
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.ofNullable(claims.get("tenant_id", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
