package com.suresell.orders.multitenant;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
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

    private static final String REG_KEY = "KAM-SECRET";
    private static final String BIZ_KEY = "FISCAL-KEY";

    private AuthService newService(AuthRepository repo) {
        return new AuthService(repo, SECRET, 3600, REG_KEY, BIZ_KEY);
    }

    /** Servicio con el registro DESHABILITADO (sin clave configurada). */
    private AuthService newServiceNoRegister(AuthRepository repo) {
        return new AuthService(repo, SECRET, 3600, "", BIZ_KEY);
    }

    private String tenantOf(String jwt) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(jwt).getPayload();
        return c.get("tenant_id", String.class);
    }

    @SuppressWarnings("unchecked")
    private List<String> modulesOf(String jwt) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(jwt).getPayload();
        return (List<String>) c.get("modules", List.class);
    }

    private AuthRepository repoWithTenant(String plan) {
        AuthRepository repo = mock(AuthRepository.class);
        when(repo.findUserByEmail(anyString())).thenReturn(Optional.of(
                new AuthRepository.UserRow(1, "u@x.co", encoder.encode("s3cret"),
                        "t1", "admin", "active")));
        when(repo.findTenant("t1")).thenReturn(Optional.of(
                new AuthRepository.TenantRow("t1", "T", plan, "active", null, null, null, null)));
        return repo;
    }

    @Test
    void loginProReturnsModulesIncludingDescuentos_yEnElJwt() {
        AuthService.AuthResponse res = newService(repoWithTenant("pro")).login("u@x.co", "s3cret");
        assertTrue(res.modules().contains("descuentos"));
        assertTrue(res.modules().contains("ventas"));
        assertTrue(modulesOf(res.token()).contains("descuentos"), "el JWT trae el claim modules");
    }

    @Test
    void loginBasicoNoIncluyeDescuentos() {
        AuthService.AuthResponse res = newService(repoWithTenant("basico")).login("u@x.co", "s3cret");
        assertFalse(res.modules().contains("descuentos"));
        assertTrue(res.modules().contains("cierre"));
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
                new AuthRepository.TenantRow("shark-burger", "Shark Burger", "pro", "active", "NIT-1", "Calle 1", "3001", "Gracias")));

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
                new AuthRepository.TenantRow("shark-burger", "Shark Burger", "pro", "suspended", null, null, null, null)));

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
                .when(repo).insertTenant(anyString(), anyString(), anyString(), any(), any(), any());

        AuthService.AuthResponse res =
                newService(repo).register("¡Mi Negocio!", "owner@mn.co", "s3cret1", REG_KEY, null, null, null);

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
                newService(repo).register("Shark Burger", "b@x.co", "s3cret1", REG_KEY, null, null, null);

        assertEquals("shark-burger-2", res.tenantId());
    }

    @Test
    void registerDuplicateEmailIs409() {
        AuthRepository repo = mock(AuthRepository.class);
        when(repo.emailExists("dup@x.co")).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> newService(repo).register("Neg", "dup@x.co", "s3cret1", REG_KEY, null, null, null));
        assertEquals(409, ex.status());
        verify(repo, never()).insertTenant(any(), any(), any(), any(), any(), any());
    }

    @Test
    void registerShortPasswordIs400() {
        AuthRepository repo = mock(AuthRepository.class);
        AuthException ex = assertThrows(AuthException.class,
                () -> newService(repo).register("Neg", "a@x.co", "123", REG_KEY, null, null, null));
        assertEquals(400, ex.status());
    }

    @Test
    void registerWrongKeyIs403AndCreatesNothing() {
        AuthRepository repo = mock(AuthRepository.class);
        AuthException ex = assertThrows(AuthException.class,
                () -> newService(repo).register("Neg", "a@x.co", "s3cret1", "clave-mala", null, null, null));
        assertEquals(403, ex.status());
        verify(repo, never()).insertTenant(any(), any(), any(), any(), any(), any());
        verify(repo, never()).insertUser(any(), any(), any(), any());
    }

    @Test
    void registerDisabledWhenNoKeyConfiguredIs403() {
        AuthRepository repo = mock(AuthRepository.class);
        AuthException ex = assertThrows(AuthException.class,
                () -> newServiceNoRegister(repo).register("Neg", "a@x.co", "s3cret1", "cualquiera", null, null, null));
        assertEquals(403, ex.status());
        verify(repo, never()).insertTenant(any(), any(), any(), any(), any(), any());
    }

    @Test
    void changePasswordHappyPathUpdatesHash() {
        AuthRepository repo = mock(AuthRepository.class);
        when(repo.findUserByEmail("ana@shark.co")).thenReturn(Optional.of(
                new AuthRepository.UserRow(1, "ana@shark.co", encoder.encode("vieja"),
                        "shark-burger", "admin", "active")));

        newService(repo).changePassword("ana@shark.co", "shark-burger", "vieja", "nueva123");

        verify(repo).updatePasswordHash(eq("ana@shark.co"), eq("shark-burger"), anyString());
    }

    @Test
    void changePasswordWrongCurrentIs401() {
        AuthRepository repo = mock(AuthRepository.class);
        when(repo.findUserByEmail(anyString())).thenReturn(Optional.of(
                new AuthRepository.UserRow(1, "ana@shark.co", encoder.encode("vieja"),
                        "shark-burger", "admin", "active")));

        AuthException ex = assertThrows(AuthException.class, () ->
                newService(repo).changePassword("ana@shark.co", "shark-burger", "otra", "nueva123"));
        assertEquals(401, ex.status());
        verify(repo, never()).updatePasswordHash(any(), any(), any());
    }

    @Test
    void changePasswordShortNewIs400() {
        AuthRepository repo = mock(AuthRepository.class);
        AuthException ex = assertThrows(AuthException.class, () ->
                newService(repo).changePassword("ana@shark.co", "shark-burger", "vieja", "123"));
        assertEquals(400, ex.status());
    }

    @Test
    void changePasswordTenantMismatchIs401() {
        AuthRepository repo = mock(AuthRepository.class);
        when(repo.findUserByEmail(anyString())).thenReturn(Optional.of(
                new AuthRepository.UserRow(1, "ana@shark.co", encoder.encode("vieja"),
                        "otro-tenant", "admin", "active")));

        AuthException ex = assertThrows(AuthException.class, () ->
                newService(repo).changePassword("ana@shark.co", "shark-burger", "vieja", "nueva123"));
        assertEquals(401, ex.status());
        verify(repo, never()).updatePasswordHash(any(), any(), any());
    }

    @Test
    void loginReturnsBusinessProfileForTicket() {
        AuthRepository repo = mock(AuthRepository.class);
        when(repo.findUserByEmail("ana@shark.co")).thenReturn(Optional.of(
                new AuthRepository.UserRow(1, "ana@shark.co", encoder.encode("s3cret"),
                        "shark-burger", "admin", "active")));
        when(repo.findTenant("shark-burger")).thenReturn(Optional.of(
                new AuthRepository.TenantRow("shark-burger", "Shark Burger", "pro", "active",
                        "NIT-1", "Calle 1", "3001", "Gracias")));

        AuthService.AuthResponse res = newService(repo).login("ana@shark.co", "s3cret");

        assertEquals("NIT-1", res.nit());
        assertEquals("Calle 1", res.address());
        assertEquals("3001", res.phone());
        assertEquals("Gracias", res.ticketFooter());
    }

    @Test
    void updateBusinessPersistsAndReturnsProfile() {
        AuthRepository repo = mock(AuthRepository.class);
        when(repo.findTenant("shark-burger")).thenReturn(Optional.of(
                new AuthRepository.TenantRow("shark-burger", "Nuevo Nombre", "pro", "active",
                        "NIT-9", "Nueva Dir", "555", "Vuelva pronto")));

        AuthService.BusinessProfile p = newService(repo).updateBusiness(
                "shark-burger", "Nuevo Nombre", "NIT-9", "Nueva Dir", "555", "Vuelva pronto", BIZ_KEY);

        verify(repo).updateBusinessProfile("shark-burger", "Nuevo Nombre", "NIT-9",
                "Nueva Dir", "555", "Vuelva pronto");
        assertEquals("Nuevo Nombre", p.name());
        assertEquals("NIT-9", p.nit());
    }

    @Test
    void updateBusinessBlankNameIs400() {
        AuthRepository repo = mock(AuthRepository.class);
        AuthException ex = assertThrows(AuthException.class, () ->
                newService(repo).updateBusiness("shark-burger", "  ", "n", "a", "p", "f", BIZ_KEY));
        assertEquals(400, ex.status());
        verify(repo, never()).updateBusinessProfile(any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateBusinessWrongEditPasswordIs403() {
        AuthRepository repo = mock(AuthRepository.class);
        AuthException ex = assertThrows(AuthException.class, () ->
                newService(repo).updateBusiness("shark-burger", "Nombre", "n", "a", "p", "f", "mala"));
        assertEquals(403, ex.status());
        verify(repo, never()).updateBusinessProfile(any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateBusinessDisabledWhenNoEditKeyIs403() {
        AuthRepository repo = mock(AuthRepository.class);
        // Servicio sin clave de edición configurada.
        AuthService svc = new AuthService(repo, SECRET, 3600, REG_KEY, "");
        AuthException ex = assertThrows(AuthException.class, () ->
                svc.updateBusiness("shark-burger", "Nombre", "n", "a", "p", "f", "cualquiera"));
        assertEquals(403, ex.status());
        verify(repo, never()).updateBusinessProfile(any(), any(), any(), any(), any(), any());
    }
}
