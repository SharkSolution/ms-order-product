package com.suresell.orders.multitenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * El pool vivo tiene los valores del YAML, y no los de fábrica de HikariCP.
 *
 * <h3>Por qué este test existe</h3>
 *
 * El 2026-08-25 se editó {@code application-cloud.yml} para arreglar los 500 de
 * la primera venta de la mañana. Se desplegó, salió verde, y el fallo volvió esa
 * misma noche: {@code DataSourceConfig} construía el pool sin
 * {@code @ConfigurationProperties}, así que <b>el bloque entero se ignoraba en
 * silencio</b>. El fichero decía lo correcto y no gobernaba nada.
 *
 * <h3>Qué vería si esto estuviera roto</h3>
 *
 * Un YAML impecable, un despliegue en verde, y el mismo 500. Ninguna de las tres
 * señales que se miraron aquella noche —el fichero, el commit, el despliegue—
 * podía distinguir «aplicado» de «ignorado». Este test sí: le pregunta al objeto
 * que de verdad reparte conexiones.
 *
 * <p><b>Control negativo, medido:</b> quitando la anotación del bean, cada uno de
 * los cinco valores de abajo cae a su defecto de HikariCP —
 * {@code keepaliveTime} a <b>0</b> (desactivado), {@code minimumIdle} a 10,
 * {@code maxLifetime} a 1 800 000, {@code validationTimeout} a 5 000 e
 * {@code idleTimeout} a 600 000. Las cinco aserciones se ponen en rojo. No es un
 * test que se satisfaga solo.
 *
 * <p>El más importante es {@code keepaliveTime}: es el único cuyo defecto
 * <b>desactiva</b> una función en vez de darle otro número. Con él en 0 nadie
 * pulsa las conexiones ociosas y nadie descubre que están muertas hasta que un
 * cliente se lo encuentra.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("cloud")
@Testcontainers
class ConfiguracionDelPoolTest {

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
        r.add("security.jwt.secret", () -> "clave-de-prueba-multitenant-min-32-bytes!!");
        r.add("auth.reset.link-base", () -> "https://pos-de-prueba.invalid");
    }

    @Autowired DataSource dataSource;

    /** Saca el Hikari de dentro del {@code TenantAwareDataSource} que lo envuelve. */
    private HikariDataSource pool() throws Exception {
        return dataSource.unwrap(HikariDataSource.class);
    }

    @Test
    @DisplayName("el pulso de vida está ENCENDIDO — su defecto es 0, que lo apaga")
    void elKeepaliveEstaEncendido() throws Exception {
        assertEquals(30_000L, pool().getKeepaliveTime(),
                "keepaliveTime en 0 significa que nadie pulsa las conexiones ociosas: "
                + "las muertas solo se descubren cuando un cliente tropieza con ellas");
    }

    @Test
    @DisplayName("las conexiones se retiran antes de que el pooler las mate")
    void lasConexionesSeRetiranATiempo() throws Exception {
        long vida = pool().getMaxLifetime();
        assertEquals(240_000L, vida, "max-lifetime no llegó al pool");
        assertTrue(vida < 7 * 60_000L,
                "medido en Producción: las conexiones mueren en menos de 7 minutos. "
                + "Retirarlas más tarde que eso es llegar siempre tarde");
    }

    @Test
    @DisplayName("en reposo el pool se queda en 2 conexiones, no en 10")
    void elPoolSeEncogeEnReposo() throws Exception {
        assertEquals(2, pool().getMinimumIdle(),
                "con minimumIdle=10 hay 10 conexiones pudriéndose de noche, "
                + "y la primera venta de la mañana tropieza con las 10");
        assertEquals(120_000L, pool().getIdleTimeout(), "idle-timeout no llegó al pool");
    }

    @Test
    @DisplayName("validar una conexión muerta cuesta 3 s, no 5")
    void validarNoSeEternizaa() throws Exception {
        long v = pool().getValidationTimeout();
        assertEquals(3_000L, v, "validation-timeout no llegó al pool");
        assertTrue(v * 9 < pool().getConnectionTimeout() * 1.5,
                "si validar cada muerta cuesta demasiado, agotar el pool supera el "
                + "connection-timeout y el usuario recibe un 500 en vez de una espera");
    }
}
