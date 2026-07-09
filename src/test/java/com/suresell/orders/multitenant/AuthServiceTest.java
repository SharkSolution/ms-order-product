package com.suresell.orders.multitenant;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Auth real: verifica login/registro/aislamiento del emisor de token, y (crítico)
 * que el hash BCrypt sembrado en V4__auth.sql valide con Spring — si no, el login
 * demo de staging quedaría roto. Sin DB: se mockea {@link AuthRepository}.
 */
class AuthServiceTest {

    private static final String SECRET = "clave-de-pruebas-suficientemente-larga-32bytes!";
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private AuthService newService(AuthRepository repo) {
        return new AuthService(repo, SECRET, 3600, "");
    }

    private String tenantOf(String jwt) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(jwt).getPayload();
        return c.get("tenant_id", String.class);
    }

    @Test
    void seededDemoHashValidates() {
        // El hash exacto sembrado en V4__auth.sql para la clave 'shark2026'.
        String seeded = "$2a$10$lM1WJngu0T/FrD9PaW15QeR/PbGuZPXn7mRqrmChmQCupcfHw7jP.";
        assertTrue(encoder.matches("shark2026", seeded),
                "El hash sembrado debe validar con Spring BCrypt");
        assertFalse(encoder.matches("otra-clave", seeded));
    }

    @Test
    void loginHappyPathReturnsTenantScopedToken() {
        AuthRepository repo = mock(AuthRepository.class);
        String hash = encoder.encode("s3cret");
        when(repo.findUserByEmail("ana@shark.co")).thenReturn(Optional.of(
                new AuthRepository.UserRow(1, "ana@shark.co", hash, "shark-burger", "admin", "active")));
        when(repo.findTenant("shark-burger")).thenReturn(Optional.of(
                new AuthRepository.TenantRow("shark-burger", "Shark Burger", "pro", "active")));

        AuthService.AuthResponse res = newService(repo).login("ana@shark.co", "s3cret");

        assertEquals("shark-burger", res.tenantId());
        assertEquals("Shark Burger", res.tenantName());
        assertEquals("pro", res.plan());
        assertEquals("shark-burger", tenantOf(res.token()));
    }

    @Test
    void loginWrongPasswordIs401AndGeneric() {
        AuthRepository repo = mock(AuthRepository.class);
        when(repo.findUserByEmail(anyString())).thenReturn(Optional.of(
                new AuthRepository.UserRow(1, "ana@shark.co", encoder.encode("right"),
                        "shark-burger", "admin", "active")));

        AuthException ex = assertThrows(AuthException.class,
                () -> newService(repo).login("ana@shark.co", "wrong"));
        assertEquals(401, ex.status());
        assertEquals("Credenciales inválidas", ex.getMessage());
    }

    @Test
    void loginUnknownEmailIs401SameMessage() {
        AuthRepository repo = mock(AuthRepository.class);
        when(repo.findUserByEmail(anyString())).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class,
                () -> newService(repo).login("nope@x.co", "whatever"));
        assertEquals(401, ex.status());
        assertEquals("Credenciales inválidas", ex.getMessage(),
                "No debe revelar si el email existe");
    }

    @Test
    void loginSuspendedTenantIs403() {
        AuthRepository repo = mock(AuthRepository.class);
        when(repo.findUserByEmail(anyString())).thenReturn(Optional.of(
                new AuthRepository.UserRow(1, "ana@shark.co", encoder.encode("s3cret"),
                        "shark-burger", "admin", "active")));
        when(repo.findTenant("shark-burger")).thenReturn(Optional.of(
                new AuthRepository.TenantRow("shark-burger", "Shark Burger", "pro", "suspended")));

        AuthException ex = assertThrows(AuthException.class,
                () -> newService(repo).login("ana@shark.co", "s3cret"));
        assertEquals(403, ex.status());
    }

    @Test
    void registerCreatesTenantAndAdminAndDerivesSlug() {
        AuthRepository repo = mock(AuthRepository.class);
        Map<String, String> insertedTenant = new HashMap<>();
        when(repo.emailExists(anyString())).thenReturn(false);
        when(repo.tenantExists("mi-negocio")).thenReturn(false);
        doAnswer(inv -> { insertedTenant.put("id", inv.getArgument(0));
                          insertedTenant.put("name", inv.getArgument(1));
                          return null; })
                .when(repo).insertTenant(anyString(), anyString(), anyString());

        AuthService.AuthResponse res =
                newService(repo).register("¡Mi Negocio!", "owner@mn.co", "s3cret1");

        assertEquals("mi-negocio", res.tenantId(), "slug limpio del nombre");
        assertEquals("mi-negocio", insertedTenant.get("id"));
        assertEquals("¡Mi Negocio!", insertedTenant.get("name"), "guarda el nombre original");
        assertEquals("mi-negocio", tenantOf(res.token()));
        verify(repo).insertUser(eq("owner@mn.co"), anyString(), eq("mi-negocio"), eq("admin"));
    }

    @Test
    void registerCollidingSlugGetsSuffix() {
        AuthRepository repo = mock(AuthRepository.class);
        when(repo.emailExists(anyString())).thenReturn(false);
        when(repo.tenantExists("shark-burger")).thenReturn(true);
        when(repo.tenantExists("shark-burger-2")).thenReturn(false);

        AuthService.AuthResponse res =
                newService(repo).register("Shark Burger", "b@x.co", "s3cret1");

        assertEquals("shark-burger-2", res.tenantId());
    }

    @Test
    void registerDuplicateEmailIs409() {
        AuthRepository repo = mock(AuthRepository.class);
        when(repo.emailExists("dup@x.co")).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> newService(repo).register("Neg", "dup@x.co", "s3cret1"));
        assertEquals(409, ex.status());
        verify(repo, never()).insertTenant(any(), any(), any());
    }

    @Test
    void registerShortPasswordIs400() {
        AuthRepository repo = mock(AuthRepository.class);
        AuthException ex = assertThrows(AuthException.class,
                () -> newService(repo).register("Neg", "a@x.co", "123"));
        assertEquals(400, ex.status());
    }
}
