package com.suresell.orders.multitenant;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Los invariantes del modelo temporal (V36) los sostiene la BASE.
 *
 * <p>Mismo criterio que el resto de invariantes de este esquema: un chequeo en
 * Java protege del código de hoy; una restricción en la base protege también del
 * script que alguien corra a mano dentro de dos años. Es el razonamiento que
 * argumenta {@code V17:5-8} y que sostiene {@code ck_split_cuadra} (V29:55).
 *
 * <p>Lo que se prueba aquí es que <b>no se puede escribir una orden que mienta
 * sobre su procedencia</b>, y —lo que más importa— que {@code created_at} sigue
 * intacta.
 */
@Testcontainers
class ModeloTemporalDeLaOrdenTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String NEGOCIO = "negocio-demo";

    @BeforeAll
    static void migrar() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private Connection conexion() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** Da de alta un terminal para poder referenciarlo. */
    private UUID terminal() throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection c = conexion();
             var ps = c.prepareStatement(
                     "INSERT INTO terminals (id, tenant_id) VALUES (?, ?)")) {
            ps.setObject(1, id);
            ps.setString(2, NEGOCIO);
            ps.executeUpdate();
        }
        return id;
    }

    /**
     * Inserta una orden con la procedencia indicada. Devuelve la excepción si la
     * base la rechaza, o null si la aceptó.
     */
    private SQLException insertar(UUID terminalId, Integer epoch, Long seq, String hash) {
        String sql = """
                INSERT INTO orders (uuid_id, tenant_id, status,
                                    ocurrido_en, registrado_en,
                                    terminal_id, epoch, seq, hash_anterior)
                VALUES (?, ?, 'pagado', now(), now(), ?, ?, ?, ?)
                """;
        try (Connection c = conexion(); var ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, NEGOCIO);
            ps.setObject(3, terminalId);
            if (epoch == null) ps.setNull(4, java.sql.Types.INTEGER); else ps.setInt(4, epoch);
            if (seq == null) ps.setNull(5, java.sql.Types.BIGINT); else ps.setLong(5, seq);
            ps.setString(6, hash);
            ps.executeUpdate();
            return null;
        } catch (SQLException e) {
            return e;
        }
    }

    private static String hashValido() {
        return "a".repeat(64);
    }

    // =====================================================================

    @Test
    @DisplayName("created_at NO cambió de tipo: esta migración se define por lo que no toca")
    void createdAtIntacta() throws Exception {
        try (Connection c = conexion();
             var ps = c.prepareStatement("""
                     SELECT data_type FROM information_schema.columns
                     WHERE table_name = 'orders' AND column_name = 'created_at'
                     """);
             var rs = ps.executeQuery()) {
            assertTrue(rs.next(), "created_at debe seguir existiendo");
            assertEquals("timestamp without time zone", rs.getString(1),
                    "V36 NO debe tocar created_at: cinco servicios lo leen");
        }
    }

    @Test
    @DisplayName("las dos fechas son TIMESTAMPTZ, no TIMESTAMP (regla 3 de LINEAMIENTOS)")
    void lasFechasNuevasLlevanZona() throws Exception {
        try (Connection c = conexion();
             var ps = c.prepareStatement("""
                     SELECT column_name, data_type FROM information_schema.columns
                     WHERE table_name = 'orders'
                       AND column_name IN ('ocurrido_en','registrado_en')
                     ORDER BY column_name
                     """);
             var rs = ps.executeQuery()) {
            int vistas = 0;
            while (rs.next()) {
                assertEquals("timestamp with time zone", rs.getString(2),
                        rs.getString(1) + " debe llevar zona horaria");
                vistas++;
            }
            assertEquals(2, vistas);
        }
    }

    @Test
    @DisplayName("una orden con procedencia completa se acepta")
    void procedenciaCompleta() throws Exception {
        assertNull(insertar(terminal(), 1, 1L, null), "el primero de un epoch lleva hash nulo");
        assertNull(insertar(terminal(), 1, 1L, hashValido()));
    }

    @Test
    @DisplayName("una orden sin procedencia se acepta: los clientes viejos siguen vendiendo")
    void sinProcedencia() {
        // Es el caso de compatibilidad hacia atrás. Si esto fallara, un POS sin
        // actualizar dejaría de poder vender el día del despliegue.
        assertNull(insertar(null, null, null, null));
    }

    @Test
    @DisplayName("un terminal no puede producir dos eventos con el mismo (epoch, seq)")
    void secuenciaUnicaPorTerminalYEpoch() throws Exception {
        UUID t = terminal();
        assertNull(insertar(t, 1, 7L, null));

        SQLException repetido = insertar(t, 1, 7L, hashValido());
        assertNotNull(repetido, "dos hechos no pueden ocupar la misma posicion de la cadena");
        assertTrue(repetido.getMessage().contains("ux_orders_terminal_epoch_seq"));

        // Mismo seq en OTRO epoch sí se permite: es lo que hace que un reinicio
        // de secuencia tras perder el estado local sea representable.
        assertNull(insertar(t, 2, 7L, null),
                "tras perder el estado local, el terminal reinicia seq en un epoch nuevo");
    }

    @Test
    @DisplayName("no hay secuencias huérfanas: seq/epoch/hash exigen terminal")
    void sinTerminalNoHayProcedencia() {
        assertNotNull(insertar(null, 1, 1L, null), "un seq sin terminal no pertenece a nadie");
        assertNotNull(insertar(null, null, 5L, null));
        assertNotNull(insertar(null, null, null, hashValido()));
    }

    @Test
    @DisplayName("el hash debe ser SHA-256 hexadecimal de 64 caracteres")
    void formatoDelHash() throws Exception {
        UUID t = terminal();
        // Un hash mal construido se detecta AL ESCRIBIR, no meses después cuando
        // alguien intente verificar la cadena y se encuentre con basura.
        assertNotNull(insertar(t, 1, 10L, "demasiado-corto"));
        assertNotNull(insertar(t, 1, 11L, "A".repeat(64)), "mayusculas no: el formato es fijo");
        assertNotNull(insertar(t, 1, 12L, "z".repeat(64)), "'z' no es hexadecimal");
        assertNull(insertar(t, 1, 13L, hashValido()));
    }

    @Test
    @DisplayName("epoch y seq empiezan en 1 y no admiten cero ni negativos")
    void rangosDeEpochYSeq() throws Exception {
        UUID t = terminal();
        assertNotNull(insertar(t, 0, 1L, null));
        assertNotNull(insertar(t, -1, 1L, null));
        assertNotNull(insertar(t, 1, 0L, null));
        assertNotNull(insertar(t, 1, -5L, null));
    }

    @Test
    @DisplayName("terminal_id debe existir en terminals: no se inventan cajas")
    void laClaveForaneaAplica() {
        SQLException e = insertar(UUID.randomUUID(), 1, 1L, null);
        assertNotNull(e, "un terminal_id que no existe no puede entrar");
        assertTrue(e.getMessage().toLowerCase().contains("foreign key")
                        || e.getMessage().toLowerCase().contains("llave foránea")
                        || e.getMessage().toLowerCase().contains("clave foránea"),
                "esperaba violacion de clave foranea, vino: " + e.getMessage());
    }
}
