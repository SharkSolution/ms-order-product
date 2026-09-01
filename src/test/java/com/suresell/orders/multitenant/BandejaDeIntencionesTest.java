package com.suresell.orders.multitenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * La bandeja por la que la venta le dice al inventario que descuente.
 *
 * <p>Lo que se prueba son las invariantes del esquema, porque son las que
 * sobreviven. La alternativa que se descartó —una llamada HTTP dentro de la
 * venta— ya se probó en producción sin querer: el cierre de caja llamaba a
 * {@code /qr-payments} sin JWT, recibió 401 durante tres semanas y nadie se
 * enteró, porque un error que solo se registra en un log no existe.
 *
 * <p>Aquí un fallo deja una fila {@code PENDIENTE} que envejece. Esa es toda la
 * diferencia, y estos tests fijan que la fila no pueda mentir sobre su estado.
 */
@Testcontainers
class BandejaDeIntencionesTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String CLAVE_APP = "clave-de-prueba";

    @BeforeAll
    static void migrar() throws SQLException {
        try (Connection d = comoDuenno(); Statement st = d.createStatement()) {
            st.execute("CREATE ROLE app_user LOGIN PASSWORD '" + CLAVE_APP + "'");
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static Connection comoDuenno() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private Connection comoApp(String tenant) throws SQLException {
        Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_user", CLAVE_APP);
        try (Statement st = c.createStatement()) {
            st.execute("SET app.tenant_id = '" + tenant + "'");
        }
        return c;
    }

    private String insertar(String extraCols, String extraVals) {
        return """
                INSERT INTO public.inventario_intenciones
                    (orden_id, ocurrido_en, lineas, idempotency_key %s)
                VALUES (901, now(), '[{"producto_id":"p1","cantidad":2}]'::jsonb, %s)"""
                .formatted(extraCols, extraVals);
    }

    // =====================================================================

    @Test
    @DisplayName("una intención normal entra y nace PENDIENTE")
    void elCaminoNormal() throws SQLException {
        try (Connection a = comoApp("negocio-a"); Statement st = a.createStatement()) {
            assertThatCode(() -> st.execute(insertar("", "'orden-901'")))
                    .doesNotThrowAnyException();

            try (ResultSet rs = st.executeQuery(
                    "SELECT estado, intentos, aplicada_en FROM public.inventario_intenciones")) {
                rs.next();
                assertThat(rs.getString("estado")).isEqualTo("PENDIENTE");
                assertThat(rs.getInt("intentos")).isZero();
                assertThat(rs.getTimestamp("aplicada_en")).isNull();
            }
        }
    }

    @Test
    @DisplayName("reprocesar la misma venta no crea una segunda intención")
    void idempotencia() throws SQLException {
        // Sin esto, un reintento descontaría el inventario dos veces por la
        // misma venta — y el descuadre aparecería semanas después, sin forma
        // de saber de dónde salió.
        try (Connection a = comoApp("negocio-b"); Statement st = a.createStatement()) {
            st.execute(insertar("", "'orden-902'"));
            assertThatThrownBy(() -> st.execute(insertar("", "'orden-902'")))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ux_int_idempotencia");
        }
    }

    @Test
    @DisplayName("una fila no puede mentir sobre su propio estado")
    void estadoCoherente() throws SQLException {
        try (Connection a = comoApp("negocio-c"); Statement st = a.createStatement()) {
            // APLICADA sin fecha de aplicación.
            assertThatThrownBy(() -> st.execute(
                    insertar(", estado", "'orden-903', 'APLICADA'")))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_int_aplicada");

            // FALLIDA sin explicación. Un fallo sin motivo no sirve para
            // diagnosticar nada, que es lo que pasó con "posible falta de
            // internet" en el cierre de caja.
            assertThatThrownBy(() -> st.execute(
                    insertar(", estado", "'orden-904', 'FALLIDA'")))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_int_error");
        }
    }

    @Test
    @DisplayName("no existe un estado inventado")
    void estadoCerrado() throws SQLException {
        try (Connection a = comoApp("negocio-d"); Statement st = a.createStatement()) {
            assertThatThrownBy(() -> st.execute(
                    insertar(", estado", "'orden-905', 'EN_PROCESO'")))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_int_estado");
        }
    }

    @Test
    @DisplayName("una intención no se puede borrar: sería una venta que descuenta dos veces")
    void sinDelete() throws SQLException {
        try (Connection a = comoApp("negocio-a"); Statement st = a.createStatement()) {
            assertThatThrownBy(() -> st.execute("DELETE FROM public.inventario_intenciones"))
                    .isInstanceOf(SQLException.class)
                    .extracting(e -> ((SQLException) e).getSQLState())
                    .isEqualTo("42501");
        }
    }

    @Test
    @DisplayName("el consumidor SÍ puede marcarla aplicada")
    void elConsumidorPuedeCerrarla() throws SQLException {
        try (Connection a = comoApp("negocio-e"); Statement st = a.createStatement()) {
            st.execute(insertar("", "'orden-906'"));
            assertThatCode(() -> st.execute("""
                    UPDATE public.inventario_intenciones
                       SET estado = 'APLICADA', aplicada_en = now()
                     WHERE idempotency_key = 'orden-906'"""))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("un negocio no ve las intenciones de otro")
    void aislamiento() throws SQLException {
        try (Connection a = comoApp("negocio-a"); Statement st = a.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT count(*) FROM public.inventario_intenciones
                     WHERE tenant_id <> 'negocio-a'""")) {
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    @Test
    @DisplayName("un evento no puede registrarse antes de ocurrir")
    void relojCoherente() throws SQLException {
        try (Connection a = comoApp("negocio-f"); Statement st = a.createStatement()) {
            assertThatThrownBy(() -> st.execute("""
                    INSERT INTO public.inventario_intenciones
                        (orden_id, ocurrido_en, registrado_en, lineas, idempotency_key)
                    VALUES (907, now() + interval '1 day', now(),
                            '[]'::jsonb, 'orden-907')"""))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_int_reloj");
        }
    }

    @Test
    @DisplayName("hay una consulta que convierte un fallo silencioso en uno visible")
    void loPendienteEnvejeceALaVista() throws SQLException {
        // Es la razón de ser del diseño entero. La alternativa descartada —la
        // llamada HTTP— no dejaba NADA que consultar cuando fallaba.
        try (Connection a = comoApp("negocio-a"); Statement st = a.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT count(*) FROM public.inventario_intenciones
                     WHERE estado = 'PENDIENTE'
                       AND registrado_en < now() - interval '15 minutes'""")) {
            rs.next();
            assertThat(rs.getInt(1))
                    .as("nada pendiente y viejo en una base recién migrada")
                    .isZero();
        }
    }
}
