package com.suresell.orders.multitenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * La ventana de validez del token de recuperación de contraseña.
 *
 * <h3>Por qué existe este test</h3>
 *
 * Se reportó un token de recuperación "vencido nada más recibirse". La hipótesis
 * a descartar era de zona horaria: si {@code expires_at} se calculara en hora de
 * Bogotá y se comparara contra el {@code now()} de Postgres en UTC, el desfase de
 * cinco horas haría que todo token naciera vencido. No es una preocupación
 * abstracta — en este servicio {@code America/Bogota} está redefinida en más de
 * quince ficheros y {@code ZonaHoraria.java:21-23} advierte de que
 * {@code LocalDateTime.now()} sin zona es un error aquí.
 *
 * <h3>Qué cubre que no cubría nada</h3>
 *
 * {@code AuthServiceTest.resetValidoActualizaClaveYMarcaUsado} verifica
 * {@code insertReset} con {@code any()} en el argumento de la fecha
 * ({@code AuthServiceTest.java:431}): el cálculo de {@code expires_at} podía
 * romperse entero y ese test seguiría en verde.
 *
 * <p>Y la comprobación tiene que cruzar la frontera JVM↔Postgres para servir de
 * algo. El cálculo lo hace Java ({@code Instant.now().plus(...)},
 * {@code AuthService.java:302}) y la comparación la hace Postgres
 * ({@code expires_at > now()}, {@code AuthRepository.java:148-149}). Un test que
 * comparase dos {@code Instant} en Java daría verde con un desfase de cinco horas
 * en la base, porque nunca la tocaría. Por eso va contra un Postgres real.
 */
@Testcontainers
class VentanaDelTokenDeResetTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** El default de {@code auth.reset.ttl-minutes} ({@code AuthService.java:47-48}). */
    private static final int TTL_MINUTOS = 60;

    private static AuthRepository repo;

    @BeforeAll
    static void migrar() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        repo = new AuthRepository(new JdbcTemplate(ds));
    }

    private void insertar(String hash, Instant expira) {
        repo.insertReset(hash, "alguien@ejemplo.invalid", "negocio-demo", expira);
    }

    @Test
    @DisplayName("un token recién creado es válido: NO nace vencido")
    void elTokenReciennCreadoEsValido() {
        // Exactamente el cálculo de AuthService.java:302.
        Instant expira = Instant.now().plus(TTL_MINUTOS, ChronoUnit.MINUTES);
        insertar("token-recien-creado", expira);

        assertEquals(EstadoDelToken.valido, repo.buscarReset("token-recien-creado").estado(),
                "el token acaba de emitirse y la base ya lo da por vencido: "
                        + "el reloj del cálculo y el de la comparación no coinciden");
    }

    @Test
    @DisplayName("un token de hace dos horas está vencido")
    void elTokenViejoNoEsValido() {
        // Emitido hace 2 h con una ventana de 1 h: caducó hace una hora.
        Instant expira = Instant.now().minus(1, ChronoUnit.HOURS);
        insertar("token-de-hace-dos-horas", expira);

        assertEquals(EstadoDelToken.vencido, repo.buscarReset("token-de-hace-dos-horas").estado(),
                "un token caducado hace una hora no se reporta como vencido");
    }

    @Test
    @DisplayName("la ventana que la base registra es de 1 hora, no de -4 ni de 6")
    void laVentanaEsDeUnaHora() {
        // Este es el test que de verdad detecta el desfase de zona horaria, y es
        // el mismo cálculo que se hizo contra Producción para descartarlo.
        //
        // Los dos anteriores pasarían con un desfase de, por ejemplo, +5 h: el
        // token recién creado seguiría siendo válido (duraría 6 horas en vez de
        // 1) y el de hace dos horas seguiría vencido. Medir la DIFERENCIA entre
        // created_at (reloj de Postgres, por DEFAULT now()) y expires_at (reloj
        // de la JVM) es lo único que distingue "funciona" de "funciona por
        // ahora y con la ventana equivocada".
        insertar("token-para-medir-la-ventana",
                Instant.now().plus(TTL_MINUTOS, ChronoUnit.MINUTES));

        Double segundos = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()))
                .queryForObject(
                        "SELECT extract(epoch FROM (expires_at - created_at)) "
                                + "FROM password_resets WHERE token_hash = ?",
                        Double.class, "token-para-medir-la-ventana");

        long minutos = Math.round(segundos / 60.0);
        assertEquals(TTL_MINUTOS, minutos,
                "la ventana medida es de " + minutos + " minutos. Con 60 esperados, "
                        + "una diferencia de ±300 significa que el cálculo usa hora de "
                        + "Bogotá y la comparación UTC (o al revés)");

        // Y que el margen sea estrecho: 60 minutos ±5 segundos. Un redondeo a
        // minutos aceptaría hasta 30 s de deriva sin decir nada.
        assertTrue(Math.abs(segundos - Duration.ofMinutes(TTL_MINUTOS).getSeconds()) < 5,
                "la ventana se desvía más de 5 s de la hora exacta: " + segundos + " s");
    }

    @Test
    @DisplayName("pedir un enlace nuevo NO invalida el anterior: los dos siguen vivos")
    void pedirOtroEnlaceNoInvalidaElPrimero() {
        // No es el comportamiento deseable, es el que hay. Se deja escrito porque
        // fue una de las dos hipótesis del incidente y conviene que quede
        // medido y no supuesto: AuthService.forgotPassword (:294-311) solo
        // inserta, nunca borra ni marca las filas anteriores del mismo email.
        //
        // Si algún día se decide invalidar los previos, este test se pone rojo y
        // señala el sitio. Es la constancia de una decisión, no su defensa.
        Instant expira = Instant.now().plus(TTL_MINUTOS, ChronoUnit.MINUTES);
        insertar("primer-enlace-del-mismo-usuario", expira);
        insertar("segundo-enlace-del-mismo-usuario", expira);

        assertEquals(EstadoDelToken.valido, repo.buscarReset("primer-enlace-del-mismo-usuario").estado(),
                "el primer enlace dejó de valer al pedir el segundo");
        assertEquals(EstadoDelToken.valido, repo.buscarReset("segundo-enlace-del-mismo-usuario").estado());
    }
}
