package com.suresell.orders.multitenant;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica la extracción del tenant_id desde el JWT (firma HS256). */
class JwtTenantResolverTest {

    private static final String SECRET = "clave-de-prueba-suficientemente-larga-256bits!";
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private final JwtTenantResolver resolver = new JwtTenantResolver(SECRET);

    @Test
    void extraeTenantDeTokenFirmadoValido() {
        String token = Jwts.builder().claim("tenant_id", "shark-burger").signWith(key).compact();
        assertEquals(Optional.of("shark-burger"), resolver.resolveTenant("Bearer " + token));
    }

    @Test
    void firmaInvalidaNoResuelve() {
        SecretKey otra = Keys.hmacShaKeyFor(
                "otra-clave-distinta-pero-igual-de-larga-256!".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder().claim("tenant_id", "x").signWith(otra).compact();
        assertTrue(resolver.resolveTenant("Bearer " + token).isEmpty());
    }

    @Test
    void sinHeaderResuelveVacio() {
        assertTrue(resolver.resolveTenant(null).isEmpty());
    }
}
