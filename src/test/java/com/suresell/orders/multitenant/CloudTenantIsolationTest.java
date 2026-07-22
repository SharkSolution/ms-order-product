package com.suresell.orders.multitenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suresell.orders.domain.model.MenuProduct;
import com.suresell.orders.infrastructure.persistence.MenuProductRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de aislamiento multi-tenant END-TO-END en el perfil `cloud`, con Postgres
 * real (Testcontainers): arranca la app como `app_user`, Flyway crea el esquema con
 * RLS, y se verifica que:
 *
 *  1. Un request HTTP con JWT de tenant A solo ve los datos de A (lectura → RLS).
 *  2. Al guardar sin tenant_id, el entity listener lo puebla desde el JWT/contexto
 *     y la fila queda aislada (escritura → listener + RLS WITH CHECK).
 *  3. Sin token válido, el filtro rechaza el request (401).
 *
 * Es la verificación de "cablear la mecánica de RLS al ciclo de request real".
 * Ver docs/40-multitenant.md y docs/80-estado-y-pendientes.md §A.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("cloud")
@Testcontainers
class CloudTenantIsolationTest {

    static final String SECRET = "clave-de-prueba-multitenant-min-32-bytes!!";
    static final String LOGIN_PW = "clave-staging";

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // La app conecta como app_user (RLS aplica). Flyway migra como superusuario.
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", () -> "app_user");
        r.add("spring.datasource.password", () -> "app_pw");
        r.add("spring.flyway.url", PG::getJdbcUrl);
        r.add("spring.flyway.user", PG::getUsername);
        r.add("spring.flyway.password", PG::getPassword);
        r.add("security.jwt.secret", () -> SECRET);
        r.add("auth.login.password", () -> LOGIN_PW);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MenuProductRepository menuProductRepository;

    final ObjectMapper json = new ObjectMapper();

    /** Hash BCrypt de LOGIN_PW para sembrar el usuario de /auth/login. */
    static final String LOGIN_PW_HASH =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(LOGIN_PW);

    /** Siembra tenants, un usuario real y dos productos (uno por tenant) como superusuario, saltando RLS. */
    @BeforeEach
    void seed() throws Exception {
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM order_delivery_tracking");
            s.execute("DELETE FROM order_item");
            s.execute("DELETE FROM orders");
            s.execute("DELETE FROM menu_products");
            s.execute("DELETE FROM users");
            s.execute("DELETE FROM tenants");
            s.execute("INSERT INTO tenants (id, name, plan) VALUES "
                    + "('tenant-a', 'Tenant A', 'pro'), ('tenant-b', 'Tenant B', 'pro')");
            s.execute("INSERT INTO users (email, password_hash, tenant_id, role) "
                    + "VALUES ('angie@tenant-a.co', '" + LOGIN_PW_HASH + "', 'tenant-a', 'admin')");
            s.execute("INSERT INTO menu_products (id_product, tenant_id, name_product, price, active) "
                    + "VALUES ('P-A', 'tenant-a', 'Hamburguesa A', 10000, true)");
            s.execute("INSERT INTO menu_products (id_product, tenant_id, name_product, price, active) "
                    + "VALUES ('P-B', 'tenant-b', 'Perro B', 8000, true)");
        }
    }

    private String jwtFor(String tenant) {
        return Jwts.builder()
                .claim("tenant_id", tenant)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String jwtFor(String tenant, java.util.List<String> modules) {
        return Jwts.builder()
                .claim("tenant_id", tenant)
                .claim("modules", modules)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void tenantSoloVeSusProductosViaHttp() throws Exception {
        mockMvc.perform(get("/api/menu/products")
                        .header("Authorization", "Bearer " + jwtFor("tenant-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nameProduct").value("Hamburguesa A"));

        mockMvc.perform(get("/api/menu/products")
                        .header("Authorization", "Bearer " + jwtFor("tenant-b")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nameProduct").value("Perro B"));
    }

    @Test
    void sinTokenValidoRechaza401() throws Exception {
        mockMvc.perform(get("/api/menu/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void escrituraPoblaTenantIdYAisla() throws Exception {
        // Guarda SIN fijar tenant_id: el entity listener debe poblarlo desde el contexto.
        TenantContext.set("tenant-a");
        try {
            MenuProduct p = new MenuProduct();
            p.setIdProduct("P-NEW");
            p.setNameProduct("Nuevo A");
            p.setPrice(5000);
            p.setActive(true);
            menuProductRepository.saveAndFlush(p);
        } finally {
            TenantContext.clear();
        }

        // Como superusuario (salta RLS) el tenant_id quedó poblado por el listener.
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT tenant_id FROM menu_products WHERE id_product = 'P-NEW'")) {
            assertTrue(rs.next(), "La fila nueva debe existir");
            assertEquals("tenant-a", rs.getString("tenant_id"),
                    "El entity listener debe poblar tenant_id desde el contexto");
        }

        // Otro tenant no ve la fila (RLS).
        TenantContext.set("tenant-b");
        try {
            assertTrue(menuProductRepository.findById("P-NEW").isEmpty(),
                    "Tenant B no debe ver el producto de Tenant A");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void loginEmiteTokenQueAutorizaYRespetaTenant() throws Exception {
        // 1) Login real (endpoint público) con el usuario sembrado → deriva el tenant.
        String body = "{\"email\":\"angie@tenant-a.co\",\"password\":\"" + LOGIN_PW + "\"}";
        String resp = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(resp).get("token").asText();
        assertNotNull(token);

        // 2) Usar el token emitido en un endpoint protegido → ve solo su tenant.
        mockMvc.perform(get("/api/menu/products").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nameProduct").value("Hamburguesa A"));
    }

    @Test
    void loginConClaveIncorrectaRechaza401() throws Exception {
        String body = "{\"email\":\"angie@tenant-a.co\",\"password\":\"mala\"}";
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Módulo cocina (F4 Inc.1): aislamiento por tenant + gating por módulo.
    // ------------------------------------------------------------------

    private void seedKitchenOrder(String uuid, String tenant, String producto) throws Exception {
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement s = c.createStatement()) {
            s.execute("INSERT INTO orders (uuid_id, tenant_id, created_at, synced, is_printed) "
                    + "VALUES ('" + uuid + "', '" + tenant + "', now(), true, false)");
            s.execute("INSERT INTO order_item (uuid_id, tenant_id, order_uuid_id, product_id, quantity) "
                    + "VALUES ('" + java.util.UUID.randomUUID() + "', '" + tenant + "', '" + uuid + "', '"
                    + producto + "', 1)");
            s.execute("INSERT INTO order_delivery_tracking (order_id_uuid, tenant_id, delivered, pager_returned) "
                    + "VALUES ('" + uuid + "', '" + tenant + "', false, false)");
        }
    }

    @Test
    void cocinaActivasAisladasPorTenant() throws Exception {
        String uuidA = java.util.UUID.randomUUID().toString();
        String uuidB = java.util.UUID.randomUUID().toString();
        seedKitchenOrder(uuidA, "tenant-a", "P-A");
        seedKitchenOrder(uuidB, "tenant-b", "P-B");

        mockMvc.perform(get("/api/kitchen/orders/active")
                        .header("Authorization", "Bearer " + jwtFor("tenant-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderUuid").value(uuidA))
                .andExpect(jsonPath("$[0].items[0].productName").value("Hamburguesa A"));

        // Tenant B no puede entregar la orden de A (RLS: para él no existe → 400).
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/kitchen/orders/" + uuidA + "/deliver")
                        .header("Authorization", "Bearer " + jwtFor("tenant-b"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preparationDurationSeconds\":60}"))
                .andExpect(status().isBadRequest());

        // Tenant A sí entrega, y la orden sale de activas y aparece en entregadas.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/kitchen/orders/" + uuidA + "/deliver")
                        .header("Authorization", "Bearer " + jwtFor("tenant-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preparationDurationSeconds\":60}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/kitchen/orders/active")
                        .header("Authorization", "Bearer " + jwtFor("tenant-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/kitchen/orders/delivered")
                        .header("Authorization", "Bearer " + jwtFor("tenant-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].tracking.preparationDurationSeconds").value(60))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void cocinaRequiereElModuloSiElTokenTraeClaim() throws Exception {
        mockMvc.perform(get("/api/kitchen/orders/active")
                        .header("Authorization", "Bearer "
                                + jwtFor("tenant-a", java.util.List.of("ventas", "historial"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/kitchen/orders/active")
                        .header("Authorization", "Bearer "
                                + jwtFor("tenant-a", java.util.List.of("ventas", "cocina"))))
                .andExpect(status().isOk());
    }
}
