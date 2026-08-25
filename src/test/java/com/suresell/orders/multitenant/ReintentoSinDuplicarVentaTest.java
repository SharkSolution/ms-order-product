package com.suresell.orders.multitenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * P-6 · OFFLINE → ONLINE: que un reintento no duplique la venta.
 *
 * <p>Es la unica prueba pendiente de la matriz que puede costar plata de
 * verdad: si al recuperar la señal se duplicaran las ordenes, se duplicaria la
 * venta del dia, y eso se descubre cuadrando caja.
 *
 * <p>El reintento LIMPIO —mandar dos veces seguidas la misma clave— ya estaba
 * cubierto. Lo que no estaba cubierto es la forma en que esto pasa de verdad:
 * el telefono no espera a que le respondan. Se le va la señal, reintenta, y las
 * dos peticiones quedan <b>en vuelo a la vez</b>. Ahi el
 * "consultar y despues insertar" de {@code createOrder} no alcanza: las dos
 * pasan el consultar antes de que ninguna inserte.
 *
 * <p>Lo que hay debajo es un indice unico por (tenant, idempotency_key), asi
 * que la base nunca deja entrar la segunda. La pregunta que responde esta
 * prueba es que ve el mesero cuando eso ocurre: si le responden su orden, o si
 * le responden un error y vuelve a tomar el pedido a mano —creando el duplicado
 * que el indice acababa de impedir—.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("cloud")
@Testcontainers
class ReintentoSinDuplicarVentaTest {

    static final String SECRET = "clave-de-prueba-multitenant-min-32-bytes!!";
    static final String TENANT = "tenant-a";
    /** Cuantas veces reintenta el telefono con la señal intermitente. */
    static final int REINTENTOS_SIMULTANEOS = 8;

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
        // Obligatoria desde ResetLinkBaseValidator: sin ella el contexto del
        // perfil cloud no levanta, que es justo lo que se quiere en producción.
        r.add("auth.reset.link-base", () -> "https://pos-de-prueba.invalid");
    }

    @Autowired
    MockMvc mockMvc;

    final ObjectMapper json = new ObjectMapper();

    private String bearer() {
        return "Bearer " + Jwts.builder()
                .claim("tenant_id", TENANT)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @BeforeEach
    void seed() throws Exception {
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM order_item");
            s.execute("DELETE FROM orders");
            s.execute("DELETE FROM menu_products");
            s.execute("INSERT INTO tenants (id, name, plan) VALUES ('" + TENANT + "', 'A', 'pro') "
                    + "ON CONFLICT (id) DO NOTHING");
            s.execute("INSERT INTO menu_products (id_product, tenant_id, name_product, price, active) "
                    + "VALUES ('P-A', '" + TENANT + "', 'Hamburguesa', 10000, true)");
        }
    }

    private long ordenesConLaClave(String clave) throws Exception {
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT count(*) FROM orders WHERE idempotency_key = '" + clave + "'")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    @DisplayName("ocho reintentos a la vez con la misma clave = UNA venta y ninguna respuesta de error")
    void elReintentoSimultaneoNoDuplicaNiRompe() throws Exception {
        String clave = "idem-señal-intermitente";
        String cuerpo = "{\"pagerColor\":\"AMARILLO\",\"pagerNumber\":\"5\",\"paymentMethod\":\"CASH\","
                + "\"items\":[{\"productId\":\"P-A\",\"quantity\":2,\"unitPrice\":10000}],"
                + "\"idempotencyKey\":\"" + clave + "\"}";

        // Todas salen a la vez: es lo que hace un telefono que reintenta sin
        // esperar respuesta, y lo que un reintento secuencial nunca reproduce.
        CyclicBarrier salida = new CyclicBarrier(REINTENTOS_SIMULTANEOS);
        ExecutorService pool = Executors.newFixedThreadPool(REINTENTOS_SIMULTANEOS);
        List<Callable<Integer>> intentos = new ArrayList<>();
        for (int i = 0; i < REINTENTOS_SIMULTANEOS; i++) {
            intentos.add(() -> {
                salida.await(10, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/waiter/mobile/orders")
                                .header("Authorization", bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(cuerpo))
                        .andReturn().getResponse().getStatus();
            });
        }

        List<Integer> estados = new ArrayList<>();
        for (Future<Integer> f : pool.invokeAll(intentos)) {
            estados.add(f.get());
        }
        pool.shutdown();

        assertEquals(1, ordenesConLaClave(clave),
                "Si entra mas de una, se duplico la venta del dia");

        List<Integer> errores = estados.stream().filter(e -> e >= 500).toList();
        assertTrue(errores.isEmpty(),
                "Un 500 le dice al mesero que el pedido no entro, y lo vuelve a tomar a mano: "
                        + "ahi nace el duplicado que el indice unico acababa de impedir. Estados: "
                        + estados);
    }

    @Test
    @DisplayName("el reintento devuelve LA MISMA orden, no una nueva")
    void elReintentoDevuelveLaMismaOrden() throws Exception {
        String clave = "idem-secuencial";
        String cuerpo = "{\"pagerColor\":\"AMARILLO\",\"pagerNumber\":\"6\",\"paymentMethod\":\"CASH\","
                + "\"items\":[{\"productId\":\"P-A\",\"quantity\":1,\"unitPrice\":10000}],"
                + "\"idempotencyKey\":\"" + clave + "\"}";

        long primera = idDeLaOrden(cuerpo);
        long segunda = idDeLaOrden(cuerpo);

        assertEquals(primera, segunda, "El mesero tiene que ver su pedido, no uno nuevo");
        assertEquals(1, ordenesConLaClave(clave));
    }

    private long idDeLaOrden(String cuerpo) throws Exception {
        String resp = mockMvc.perform(post("/api/waiter/mobile/orders")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resp).get("idOrder").asLong();
    }
}
