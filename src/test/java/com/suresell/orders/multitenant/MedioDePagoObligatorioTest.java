package com.suresell.orders.multitenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * T3 — el servidor NO pone efectivo por defecto, y nadie puede hacérselo poner.
 *
 * <h3>Por qué este test es el que importa de los tres</h3>
 *
 * La preselección de efectivo estaba en TRES sitios: la app de mesero
 * ({@code cart_screen.dart:31}), el POS web ({@code pos.component.ts:395}) y,
 * supuestamente, el servidor. Medido, el servidor <b>ya rechazaba</b>.
 *
 * <p>Pero no había ni un test que lo fijara. Un default en el servidor
 * reintroducido mañana anularía de golpe los arreglos de las dos pantallas, y
 * el servidor es la única capa que un cliente no puede saltarse: una app vieja,
 * un cliente propio, o un {@code curl} llegan igualmente aquí.
 *
 * <h3>Qué vería si esto estuviera roto</h3>
 *
 * Órdenes con {@code payment_method = 'CASH'} que nadie eligió, indistinguibles
 * de las que sí se eligieron. Por eso el test no se conforma con el 400:
 * comprueba además que <b>no quedó ninguna fila</b>. Un 400 devuelto después de
 * insertar sería peor que no validar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("cloud")
@Testcontainers
class MedioDePagoObligatorioTest {

    static final String SECRET = "clave-de-prueba-multitenant-min-32-bytes!!";
    static final String TENANT = "negocio-medio-pago";

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
        r.add("auth.reset.link-base", () -> "https://pos-de-prueba.invalid");
    }

    @Autowired MockMvc mockMvc;

    private String bearer() {
        return "Bearer " + Jwts.builder()
                .claim("tenant_id", TENANT)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @BeforeEach
    void sembrar() throws Exception {
        try (Connection c = DriverManager.getConnection(
                     PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM order_item");
            s.execute("DELETE FROM orders");
            s.execute("DELETE FROM menu_products");
            s.execute("INSERT INTO tenants (id, name, plan) VALUES ('" + TENANT + "','M','pro') "
                    + "ON CONFLICT (id) DO NOTHING");
            s.execute("INSERT INTO menu_products (id_product, tenant_id, name_product, price, active) "
                    + "VALUES ('P-M','" + TENANT + "','Hamburguesa',10000,true)");
        }
    }

    private long ordenes() throws Exception {
        try (Connection c = DriverManager.getConnection(
                     PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM orders")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Las tres formas en que un cliente puede "no mandar" el medio de pago. */
    private static final String[] SIN_MEDIO = {
        "",                                    // el campo no está
        "\"paymentMethod\":null,",             // está y es nulo
        "\"paymentMethod\":\"\",",             // está y viene vacío
    };

    private String cuerpo(String medio, String clave) {
        return "{\"pagerColor\":\"MESA\",\"pagerNumber\":\"3\"," + medio
                + "\"items\":[{\"productId\":\"P-M\",\"quantity\":1,\"unitPrice\":10000}],"
                + "\"idempotencyKey\":\"" + clave + "\"}";
    }

    @Test
    @DisplayName("🔴 el camino del MESERO rechaza con 400 y no deja fila")
    void elMeseroSinMedioDePagoEs400() throws Exception {
        int n = 0;
        for (String medio : SIN_MEDIO) {
            int estado = mockMvc.perform(post("/api/waiter/mobile/orders")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(medio, "sin-medio-mesero-" + n++)))
                    .andReturn().getResponse().getStatus();
            assertEquals(400, estado, "se aceptó una orden de mesero sin medio de pago: " + medio);
        }
        assertEquals(0, ordenes(),
                "el servidor respondió 400 pero la orden quedó escrita: un rechazo "
                        + "después de insertar es peor que no validar");
    }

    @Test
    @DisplayName("🔴 el camino del POS rechaza con 400 y no deja fila")
    void elPosSinMedioDePagoEs400() throws Exception {
        int n = 0;
        for (String medio : SIN_MEDIO) {
            int estado = mockMvc.perform(post("/orders/create")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(medio, "sin-medio-pos-" + n++)))
                    .andReturn().getResponse().getStatus();
            assertEquals(400, estado, "se aceptó una orden del POS sin medio de pago: " + medio);
        }
        assertEquals(0, ordenes(), "el servidor respondió 400 pero la orden quedó escrita");
    }

    @Test
    @DisplayName("con medio de pago SÍ se registra: el rechazo no es que todo falle")
    void conMedioDePagoSeRegistra() throws Exception {
        // El contraste. Sin este caso, un servidor que rechazara TODAS las
        // órdenes pasaría los dos tests de arriba con nota.
        int estado = mockMvc.perform(post("/api/waiter/mobile/orders")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("\"paymentMethod\":\"CASH\",", "con-medio-1")))
                .andReturn().getResponse().getStatus();
        assertEquals(201, estado);
        assertEquals(1, ordenes());
    }

    @Test
    @DisplayName("NEQUI de un APK viejo se sigue aceptando y se normaliza a QR")
    void elClienteViejoSigueVendiendo() throws Exception {
        // Nequi se retiró en N2/6.6, pero en los últimos 90 días llegaron 38
        // órdenes con ese medio desde APKs que nadie ha podido actualizar.
        // Rechazarlas tumbaría ventas en dispositivos que no controlamos.
        int estado = mockMvc.perform(post("/api/waiter/mobile/orders")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("\"paymentMethod\":\"NEQUI\",", "nequi-viejo-1")))
                .andReturn().getResponse().getStatus();
        assertEquals(201, estado, "un APK viejo dejó de poder vender");

        try (Connection c = DriverManager.getConnection(
                     PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT payment_method FROM orders WHERE idempotency_key='nequi-viejo-1'")) {
            rs.next();
            assertEquals("QR", rs.getString(1));
        }
    }
}
