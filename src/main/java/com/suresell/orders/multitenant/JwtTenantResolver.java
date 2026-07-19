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
        return parse(authorizationHeader).map(c -> c.get("tenant_id", String.class));
    }

    /** Devuelve el subject (email del usuario) si el token es válido; si no, vacío. */
    public Optional<String> resolveSubject(String authorizationHeader) {
        return parse(authorizationHeader).map(Claims::getSubject);
    }

    /** Rol del usuario (claim `role`: admin|cajero…) para gating por rol (F3). */
    public Optional<String> resolveRole(String authorizationHeader) {
        return parse(authorizationHeader).map(c -> c.get("role", String.class));
    }

    /**
     * Módulos del tenant (claim `modules`) para enforcement por módulo (F3). Vacío
     * si el token no lo trae (tokens viejos) — el {@link ModuleAccessFilter} lo trata
     * como "desconocido" y no bloquea, hasta que todos re-loguean.
     */
    @SuppressWarnings("unchecked")
    public java.util.List<String> resolveModules(String authorizationHeader) {
        return parse(authorizationHeader)
                .map(c -> {
                    Object m = c.get("modules");
                    return m instanceof java.util.List ? (java.util.List<String>) m
                            : java.util.Collections.<String>emptyList();
                })
                .orElse(java.util.Collections.emptyList());
    }

    private Optional<Claims> parse(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return Optional.empty();
        }
        String token = authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7)
                : authorizationHeader;
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
