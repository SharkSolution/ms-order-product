package com.suresell.orders.multitenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EL EFECTIVO DEL MESERO — que el numero que se le cobra sea el correcto.
 *
 * <p>Cuando el mesero cierra su turno, la cajera le pide `expectedCash`. Si ese
 * numero esta mal, la diferencia sale del bolsillo de una persona. Esto cubre
 * los dos errores que tenia y que lo movian en direcciones opuestas:
 *
 * <ol>
 *   <li>Una venta MIXED metia su total entero bajo la etiqueta "MIXED" y
 *       `cashSales` solo leia "CASH": la parte en efectivo desaparecia y al
 *       mesero le figuraba un SOBRANTE por plata que si habia entregado.</li>
 *   <li>Una cuenta de mesa todavia abierta contaba como venta —nace `abierta`
 *       pero ya con metodo de pago— y le figuraba un FALTANTE por plata que
 *       nunca recibio.</li>
 * </ol>
 *
 * <p>Y sobre todo: que el mesero y la cajera vean <b>el mismo numero</b>. Son
 * dos rutas de codigo distintas (`WaiterService.buildSummary` contra
 * `WaiterSalesQueryService`) sobre los mismos hechos; mientras no coincidan,
 * cerrar un turno es una discusion.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("cloud")
@Testcontainers
class EfectivoDelMeseroTest {

    static final String SECRET = "clave-de-prueba-multitenant-min-32-bytes!!";
    static final String TENANT = "tenant-a";

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", () -> "app_user");
        r.add("spring.datasource.password", () -> "app_pw");
        r.add("spring.flyway.url", PG::getJdbcUrl);
        r.add("spring.flyway.user", PG::getUsername);
        r.add("spring.flyway.password", PG::getPassword);
        r.add("security.jwt.secret", () -> SECRET);
    }

    @Autowired
    MockMvc mockMvc;

    final ObjectMapper json = new ObjectMapper();

    /** Turno del mesero, con base de $50.000. */
    UUID sesion;

    private String bearer() {
        return "Bearer " + Jwts.builder()
                .claim("tenant_id", TENANT)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private Connection comoDueno() throws Exception {
        return DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
    }

    @BeforeEach
    void seed() throws Exception {
        sesion = UUID.randomUUID();
        try (Connection c = comoDueno(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM order_payments");
            s.execute("DELETE FROM order_item");
            s.execute("DELETE FROM orders");
            s.execute("DELETE FROM waiter_sessions");
            s.execute("DELETE FROM waiters");
            // Los tenants NO se borran: las migraciones siembran uno con usuarios
            // colgando, y el FK lo impide. Basta con asegurar el de la prueba.
            s.execute("INSERT INTO tenants (id, name, plan) VALUES ('" + TENANT + "', 'A', 'pro') "
                    + "ON CONFLICT (id) DO NOTHING");
            s.execute("INSERT INTO waiters (id, tenant_id, name, active) "
                    + "VALUES (1, '" + TENANT + "', 'Angie', true)");
            s.execute("INSERT INTO waiter_sessions (id, tenant_id, waiter_id, waiter_name, status, "
                    + "login_time, opening_cash_base) VALUES ('" + sesion + "', '" + TENANT
                    + "', 1, 'Angie', 'ACTIVE', now(), 50000)");
        }
    }

    /**
     * Siembra una orden del turno. `estado` es 'pagado' o 'abierta'.
     * Devuelve el uuid para colgarle splits si es MIXED.
     */
    private UUID orden(String metodo, String total, String estado) throws Exception {
        UUID uuid = UUID.randomUUID();
        try (Connection c = comoDueno(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO orders (uuid_id, tenant_id, created_at, payment_method, total, "
                    + "status, waiter_id, waiter_session_id, synced, is_printed) VALUES ('"
                    + uuid + "', '" + TENANT + "', now(), '" + metodo + "', " + total + ", '"
                    + estado + "', 1, '" + sesion + "', true, false)");
        }
        return uuid;
    }

    private void split(UUID orden, String metodo, String monto) throws Exception {
        try (Connection c = comoDueno(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO order_payments (tenant_id, order_uuid_id, method, amount, created_at) "
                    + "VALUES ('" + TENANT + "', '" + orden + "', '" + metodo + "', " + monto + ", now())");
        }
    }

    private JsonNode resumenDelTurno() throws Exception {
        String cuerpo = mockMvc.perform(get("/api/waiter/shifts/" + sesion + "/summary")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    /** Lo que ve la CAJERA en el cierre de caja, para el mismo mesero. */
    private JsonNode ventasSegunLaCajera() throws Exception {
        String cuerpo = mockMvc.perform(get("/api/waiter-sales")
                        .param("date", LocalDate.now().toString())
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("la parte en efectivo de una venta mixta entra en el efectivo esperado")
    void laParteEnEfectivoDeUnaMixtaCuenta() throws Exception {
        UUID mixta = orden("MIXED", "20000", "pagado");
        split(mixta, "CASH", "12000");
        split(mixta, "CARD", "8000");

        JsonNode r = resumenDelTurno();

        assertEquals(12000, r.get("cashSales").asInt(),
                "El mesero tiene $12.000 en la mano: si no se cuentan, entrega de mas");
        assertEquals(62000, r.get("expectedCash").asInt(), "base 50.000 + 12.000 en efectivo");
        assertEquals(8000, r.get("salesByMethod").get("CARD").asInt(),
                "La parte en tarjeta se ve como tal, no como 'MIXED'");
        assertEquals(20000, r.get("totalSales").asInt(), "La venta no se cuenta dos veces");
    }

    @Test
    @DisplayName("una cuenta de mesa todavia abierta NO es venta")
    void laCuentaAbiertaNoCuenta() throws Exception {
        orden("CASH", "30000", "pagado");
        orden("CASH", "45000", "abierta");   // mesa consumiendo

        JsonNode r = resumenDelTurno();

        assertEquals(30000, r.get("cashSales").asInt(),
                "La mesa abierta no ha pagado: cobrarsela al mesero es un faltante inventado");
        assertEquals(80000, r.get("expectedCash").asInt(), "base 50.000 + 30.000 cobrados");
        assertEquals(1, r.get("totalOrders").asInt());
    }

    @Test
    @DisplayName("NEQUI se muestra como QR, igual que en el cierre de caja")
    void nequiSePliegaEnQr() throws Exception {
        orden("NEQUI", "15000", "pagado");

        JsonNode r = resumenDelTurno();

        assertEquals(15000, r.get("salesByMethod").get("QR").asInt());
        assertEquals(0, r.get("cashSales").asInt(), "NEQUI no es efectivo");
    }

    /**
     * LA PRUEBA QUE IMPORTA. Un turno con de todo —efectivo, tarjeta, una mixta y
     * una mesa sin cobrar— y las dos rutas tienen que dar lo mismo.
     */
    @Test
    @DisplayName("el mesero y la cajera ven el MISMO efectivo")
    void elMeseroYLaCajeraCoinciden() throws Exception {
        orden("CASH", "30000", "pagado");
        orden("CARD", "25000", "pagado");
        UUID mixta = orden("MIXED", "20000", "pagado");
        split(mixta, "CASH", "12000");
        split(mixta, "CARD", "8000");
        orden("CASH", "45000", "abierta");   // no cobrada

        JsonNode mesero = resumenDelTurno();
        JsonNode cajera = ventasSegunLaCajera().get("waiters").get(0);

        int efectivoMesero = mesero.get("cashSales").asInt();
        int efectivoCajera = cajera.get("breakdown").get("CASH").asInt();
        assertEquals(efectivoCajera, efectivoMesero,
                "Si estos dos numeros no coinciden, cerrar el turno es una discusion "
                        + "y el que pierde es el mesero");

        assertEquals(42000, efectivoMesero, "30.000 en efectivo + 12.000 de la mixta");
        assertEquals(92000, mesero.get("expectedCash").asInt(), "base 50.000 + 42.000");
        assertEquals(75000, mesero.get("totalSales").asInt(),
                "30.000 + 25.000 + 20.000; la mesa abierta no suma");
        assertEquals(75000, cajera.get("total").asInt(), "Y la cajera ve el mismo total");
        assertEquals(3, cajera.get("ordersCount").asInt(), "3 cobradas, la abierta no");
    }
}
