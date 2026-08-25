package com.suresell.orders.multitenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Que {@code resetPassword} sea atómico de verdad.
 *
 * <h3>Por qué no basta con el test de Mockito</h3>
 *
 * {@code AuthServiceTest} comprueba que el método <b>lanza</b> cuando marcar el
 * token falla. Eso no prueba nada sobre la reversión: {@code @Transactional} solo
 * hace algo si Spring envuelve el bean en un proxy y hay un gestor de
 * transacciones detrás. Con un {@code new AuthService(...)} —que es lo que hace
 * el test de Mockito— la anotación <b>se ignora en silencio</b>, y ese test
 * seguiría verde con la transacción rota.
 *
 * <p>Así que esto necesita el contexto de Spring y un Postgres real. La pregunta
 * que responde es la única que importa: tras el fallo, ¿la contraseña volvió a
 * ser la de antes?
 *
 * <h3>Por qué un espía y no un mock entero</h3>
 *
 * El fallo se inyecta en {@code markResetUsed} —devuelve 0, que es justo lo que
 * devolverá cuando la fila deje de ser visible por RLS—, pero todo lo demás va
 * contra la base de verdad. Con el repositorio entero simulado no habría nada
 * que revertir y el test no probaría nada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("cloud")
@Testcontainers
class ResetPasswordTransaccionalTest {

    static final String SECRET = "clave-de-prueba-multitenant-min-32-bytes!!";
    static final String TENANT = "negocio-reset";
    static final String EMAIL = "duena@negocio-reset.invalid";

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

    @Autowired AuthService auth;
    @MockitoSpyBean AuthRepository repo;

    /**
     * Conexión como <b>dueño</b> de las tablas, que en el contenedor tiene
     * BYPASSRLS. Se usa para dos cosas distintas y las dos a propósito:
     *
     * <ol>
     *   <li><b>Sembrar.</b> Desde V39 {@code users} y {@code password_resets}
     *       están en FORCE, así que el {@code JdbcTemplate} de la aplicación
     *       —que conecta como {@code app_user} sin negocio en sesión— no puede
     *       insertar la fixture. Montar una transacción con {@code set_config}
     *       solo para sembrar mezclaría el andamio con lo que se prueba.</li>
     *   <li><b>Leer el resultado.</b> Y esto importa más: si las aserciones
     *       leyeran como {@code app_user}, una contraseña sin revertir y una
     *       fila invisible por RLS <b>darían el mismo resultado</b>. El oráculo
     *       tiene que ver la base entera; si no, el test comparte la suposición
     *       con el código que verifica.</li>
     * </ol>
     */
    private JdbcTemplate dueno;

    private String hashActual() {
        return dueno.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, EMAIL);
    }

    @BeforeEach
    void sembrar() {
        reset(repo);
        dueno = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        dueno.update("DELETE FROM password_resets WHERE email = ?", EMAIL);
        dueno.update("DELETE FROM users WHERE email = ?", EMAIL);
        dueno.update("DELETE FROM tenants WHERE id = ?", TENANT);
        dueno.update("INSERT INTO tenants (id, name) VALUES (?, ?)", TENANT, "Negocio Reset");
        dueno.update("INSERT INTO users (email, password_hash, tenant_id, role) "
                + "VALUES (?, ?, ?, 'admin')", EMAIL, "$2a$10$hashviejoquenoimporta", TENANT);
    }

    /** Inserta un token de recuperación vivo y devuelve el token en claro. */
    private String tokenVivo() {
        return tokenCon(Instant.now().plus(1, ChronoUnit.HOURS));
    }

    private String tokenCon(Instant expira) {
        String token = "token-de-prueba-" + System.nanoTime();
        dueno.update("INSERT INTO password_resets (token_hash, email, tenant_id, expires_at) "
                + "VALUES (?, ?, ?, ?)",
                sha256Base64Url(token), EMAIL, TENANT, java.sql.Timestamp.from(expira));
        return token;
    }

    /** Mismo cálculo que {@code AuthService.sha256}: Base64 URL-safe sin relleno. */
    private static String sha256Base64Url(String s) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("el camino feliz cambia la clave Y marca el token, las dos cosas")
    void caminoFeliz() {
        String antes = hashActual();

        auth.resetPassword(tokenVivo(), "claveNueva123");

        assertNotEquals(antes, hashActual(), "la contraseña no cambió");
        assertEquals(Boolean.TRUE, dueno.queryForObject(
                "SELECT used FROM password_resets WHERE email = ?", Boolean.class, EMAIL),
                "el token quedó sin marcar: se podría volver a usar");
    }

    @Test
    @DisplayName("🔴 si marcar el token falla, la contraseña VUELVE ATRÁS")
    void siFallaMarcarElTokenSeRevierteElCambioDeClave() {
        String antes = hashActual();
        String token = tokenVivo();

        // markResetUsed devuelve 0: exactamente lo que devolverá cuando la fila
        // deje de ser visible por RLS. Todo lo demás —incluido el UPDATE que sí
        // cambia la contraseña— va contra la base real.
        doReturn(0).when(repo).markResetUsed(anyString());

        assertThrows(AuthException.class, () -> auth.resetPassword(token, "claveNueva123"));

        // ESTA es la aserción del test. Sin @Transactional efectivo, la
        // contraseña se habría quedado cambiada y esto fallaría.
        assertEquals(antes, hashActual(),
                "la contraseña quedó cambiada pese a que la operación falló: "
                        + "la transacción no revirtió");
        assertEquals(Boolean.FALSE, dueno.queryForObject(
                "SELECT used FROM password_resets WHERE email = ?", Boolean.class, EMAIL));
    }

    @Test
    @DisplayName("🔴 un token ya usado no cambia la contraseña")
    void tokenYaUsadoNoCambiaNada() {
        String token = tokenVivo();
        auth.resetPassword(token, "primeraClave123");
        String despuesDelPrimerUso = hashActual();

        AuthException ex = assertThrows(AuthException.class,
                () -> auth.resetPassword(token, "segundaClave123"));

        assertEquals(400, ex.status());
        assertEquals("Enlace inválido o expirado", ex.getMessage());
        assertEquals(despuesDelPrimerUso, hashActual(),
                "el segundo uso del mismo enlace cambió la contraseña");
    }

    @Test
    @DisplayName("un token vencido no cambia la contraseña y el mensaje no lo delata")
    void tokenVencidoNoCambiaNada() {
        String antes = hashActual();
        String token = tokenCon(Instant.now().minus(1, ChronoUnit.HOURS));

        AuthException ex = assertThrows(AuthException.class,
                () -> auth.resetPassword(token, "claveNueva123"));

        assertEquals(400, ex.status());
        assertEquals("Enlace inválido o expirado", ex.getMessage());
        assertEquals(antes, hashActual());
    }

    @Test
    @DisplayName("buscarReset distingue los cuatro estados, aunque el mensaje no lo haga")
    void elEstadoSeDistingueHaciaDentro() {
        String vivo = tokenVivo();
        assertEquals(EstadoDelToken.valido, repo.buscarReset(sha256Base64Url(vivo)).estado());

        String vencido = tokenCon(Instant.now().minus(1, ChronoUnit.HOURS));
        assertEquals(EstadoDelToken.vencido, repo.buscarReset(sha256Base64Url(vencido)).estado());

        auth.resetPassword(vivo, "otraClave123");
        assertEquals(EstadoDelToken.usado, repo.buscarReset(sha256Base64Url(vivo)).estado());

        assertEquals(EstadoDelToken.no_existe,
                repo.buscarReset("hash-que-no-existe-en-ninguna-parte").estado());
    }

    @Test
    @DisplayName("los estados que no son válidos NO devuelven email ni negocio")
    void losEstadosInvalidosNoFiltranDatosPersonales() {
        String vencido = tokenCon(Instant.now().minus(1, ChronoUnit.HOURS));

        var consulta = repo.buscarReset(sha256Base64Url(vencido));

        assertEquals(EstadoDelToken.vencido, consulta.estado());
        assertNull(consulta.email(), "un token vencido no debe devolver el email");
        assertNull(consulta.tenantId());
    }
}
