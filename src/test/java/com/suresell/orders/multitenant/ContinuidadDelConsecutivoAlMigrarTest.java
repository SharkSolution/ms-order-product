package com.suresell.orders.multitenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * El consecutivo fiscal NO se puede reiniciar al aplicar V23..V28.
 *
 * <p><b>Qué reproduce.</b> El estado exacto de Producción antes de este
 * despliegue: un negocio en Flyway <b>v22</b>, con órdenes ya emitidas y su
 * contador vivo. Encima corre el resto de migraciones —las que introducen sedes
 * y numeración por sede— y se comprueba que el siguiente folio <b>continúa</b>.
 *
 * <p><b>Por qué importa tanto.</b> V28 hace dos cosas que, mal encadenadas,
 * reinician la numeración en 1:
 *
 * <ol>
 *   <li>{@code DELETE FROM tenant_order_counters WHERE site_id IS NULL} —
 *       borra el contador de todo negocio que no tenga sede.</li>
 *   <li>El disparador, ante un negocio sin contador, inserta {@code last_id = 1}.</li>
 * </ol>
 *
 * Lo que evita el desastre es que V23 crea la sede Principal de cada negocio
 * existente y ancla el contador a ella. Si alguien reordena esas migraciones o
 * toca el sembrado de V23, el consecutivo vuelve a 1 y <b>se emiten folios
 * repetidos</b>: un problema fiscal que nadie ve hasta que lo ve la DIAN.
 *
 * <p>Leerlo en el SQL no alcanza. Esto lo ejecuta.
 */
@Testcontainers
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class ContinuidadDelConsecutivoAlMigrarTest {

    /** Último folio emitido en Producción antes del despliegue. */
    private static final long ULTIMO_FOLIO_EMITIDO = 331_398L;

    private static final String NEGOCIO = "shark";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void reproducirProduccionYMigrar() throws Exception {
        // 1. Dejar la base como esta Produccion HOY: Flyway v22.
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(org.flywaydb.core.api.MigrationVersion.fromVersion("22"))
                .load()
                .migrate();

        // 2. Sembrar el negocio con historial y su contador vivo.
        try (Connection c = conectar(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO tenants (id, name) VALUES ('" + NEGOCIO + "', 'Shark Burger')");

            // Tres ordenes ya emitidas, la ultima con el folio real de Produccion.
            sembrarOrden(c, ULTIMO_FOLIO_EMITIDO - 2);
            sembrarOrden(c, ULTIMO_FOLIO_EMITIDO - 1);
            sembrarOrden(c, ULTIMO_FOLIO_EMITIDO);

            st.execute("INSERT INTO tenant_order_counters (tenant_id, last_id) VALUES ('"
                    + NEGOCIO + "', " + ULTIMO_FOLIO_EMITIDO + ")");
        }

        // 3. Aplicar lo que falta: V23..V28.
        var resultado = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertTrue(resultado.success, "las migraciones pendientes deben aplicar limpias");
    }

    // El orden importa: varios casos emiten folios y cada uno consume uno.
    // El folio EXACTO solo se puede afirmar en la primera emision.
    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("LO CRÍTICO: el folio siguiente continúa, no vuelve a 1")
    void elFolioContinua() throws Exception {
        try (Connection c = conectar()) {
            long folio = emitirOrden(c);

            assertEquals(ULTIMO_FOLIO_EMITIDO + 1, folio,
                    "El consecutivo fiscal se reinicio. Se emitirian folios repetidos.");
        }
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("las órdenes ya emitidas conservan su folio")
    void elHistorialNoSeReescribe() throws Exception {
        try (Connection c = conectar(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT max(id_order), count(*) FROM orders WHERE tenant_id = '" + NEGOCIO + "'")) {
            assertTrue(rs.next());
            assertTrue(rs.getLong(1) >= ULTIMO_FOLIO_EMITIDO,
                    "una migracion no puede renumerar ordenes ya emitidas");
        }
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("el negocio quedó con su sede Principal y el contador anclado a ella")
    void laSedeSeCreoYElContadorQuedoAnclado() throws Exception {
        try (Connection c = conectar(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT id, code, is_default FROM sites WHERE tenant_id = '" + NEGOCIO + "'")) {
                assertTrue(rs.next(), "V23 debe crear la sede Principal de cada negocio existente");
                assertEquals("PRINCIPAL", rs.getString("code"));
                assertTrue(rs.getBoolean("is_default"));
            }

            // Si el contador quedara huerfano (site_id NULL) V28 lo habria borrado
            // y el folio arrancaria en 1.
            try (ResultSet rs = st.executeQuery(
                    "SELECT site_id, last_id FROM tenant_order_counters WHERE tenant_id = '"
                            + NEGOCIO + "'")) {
                assertTrue(rs.next(), "el contador vivo NO puede desaparecer en la migracion");
                assertNotNull(rs.getObject("site_id"), "el contador debe quedar anclado a una sede");
                assertTrue(rs.getLong("last_id") >= ULTIMO_FOLIO_EMITIDO,
                        "el contador no puede retroceder");
            }
        }
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("un negocio nuevo, sin sede, puede vender desde el primer día")
    void unNegocioNuevoPuedeVender() throws Exception {
        // La primera version de V28 lanzaba una excepcion cuando el negocio no
        // tenia sede: dejaba SIN VENDER a cualquier alta nueva, porque el alta no
        // crea sedes. Este test lo fija.
        try (Connection c = conectar(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO tenants (id, name) VALUES ('negocio-nuevo', 'Recien dado de alta')");

            long primerFolio = emitirOrdenDe(c, "negocio-nuevo");

            assertEquals(1L, primerFolio, "un negocio nuevo arranca su numeracion en 1");

            try (ResultSet rs = st.executeQuery(
                    "SELECT count(*) FROM sites WHERE tenant_id = 'negocio-nuevo' AND is_default")) {
                rs.next();
                assertEquals(1, rs.getInt(1), "se le crea la sede por defecto sola");
            }
        }
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("dos negocios no comparten numeración")
    void cadaNegocioLleveLaSuya() throws Exception {
        try (Connection c = conectar()) {
            long folioShark = emitirOrden(c);
            long folioOtro = emitirOrdenDe(c, "negocio-nuevo");

            assertTrue(folioShark > ULTIMO_FOLIO_EMITIDO);
            assertTrue(folioOtro < 100, "el negocio nuevo no hereda el consecutivo del otro");
        }
    }

    // --- utilidades -----------------------------------------------------

    private static Connection conectar() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** Orden historica: se le fija el folio a mano, como las ya emitidas. */
    private static void sembrarOrden(Connection c, long folio) throws Exception {
        try (var ps = c.prepareStatement(
                "INSERT INTO orders (uuid_id, tenant_id, status, id_order) VALUES (?, ?, 'pagado', ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, NEGOCIO);
            ps.setLong(3, folio);
            ps.executeUpdate();
        }
    }

    private static long emitirOrden(Connection c) throws Exception {
        return emitirOrdenDe(c, NEGOCIO);
    }

    /** Orden nueva: el folio lo asigna el disparador, que es lo que se prueba. */
    private static long emitirOrdenDe(Connection c, String negocio) throws Exception {
        try (var ps = c.prepareStatement(
                "INSERT INTO orders (uuid_id, tenant_id, status) VALUES (?, ?, 'pagado') "
                        + "RETURNING id_order")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, negocio);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
