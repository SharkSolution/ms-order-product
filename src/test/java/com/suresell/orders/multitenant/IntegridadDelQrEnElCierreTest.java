package com.suresell.orders.multitenant;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Los invariantes de procedencia del QR (V34) los sostiene la BASE, no el código.
 *
 * <p>Mismo criterio que el resto de invariantes de dinero de este esquema
 * (`ck_split_cuadra` en V29:55, `ux_table_session_abierta` en V25:30): un chequeo
 * en Java protege del código de hoy; una restricción en la base protege también
 * del script que alguien corra a mano dentro de dos años.
 *
 * <p>Lo que se prueba aquí es que **no se puede escribir un cierre que mienta
 * sobre la procedencia de su QR**.
 */
@Testcontainers
class IntegridadDelQrEnElCierreTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

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

    /**
     * Inserta un cierre mínimo con la procedencia indicada. Devuelve la excepción
     * si la base lo rechaza, o null si lo aceptó.
     */
    private SQLException insertarCierre(String fuente, Integer confianza, String detalle,
                                        LocalDate fecha) {
        String sql = """
                INSERT INTO daily_closures
                    (id, tenant_id, user_name, opening_time, closure_date,
                     qr_fuente, qr_confianza, qr_detalle, qr_capturado_en)
                VALUES (?, 'negocio-demo', 'cajero', now(), ?, ?, ?, ?, now())
                """;
        try (Connection c = conexion(); var ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, fecha);
            ps.setString(3, fuente);
            if (confianza == null) {
                ps.setNull(4, java.sql.Types.SMALLINT);
            } else {
                ps.setInt(4, confianza);
            }
            ps.setString(5, detalle);
            ps.executeUpdate();
            return null;
        } catch (SQLException e) {
            return e;
        }
    }

    // =====================================================================

    @Test
    @DisplayName("los tres valores del enum se aceptan")
    void losTresValoresValidos() {
        assertNull(insertarCierre("conciliado_core", 2, null, LocalDate.of(2026, 9, 1)));
        assertNull(insertarCierre("manual_cajero", 0, null, LocalDate.of(2026, 9, 2)));
        assertNull(insertarCierre("fallo_integracion", 0,
                "HTTP 401 Unauthorized de ms-core-app", LocalDate.of(2026, 9, 3)));
    }

    @Test
    @DisplayName("NO existe un valor 'otros': el enum es cerrado (regla 10)")
    void sinCajonDeSastre() {
        // Regla 10 de LINEAMIENTOS: "Prohibido Otros / Ajuste / Varios como
        // categoría por defecto. Ahí se esconde exactamente la señal que
        // buscamos". La base lo hace cumplir, no solo la convención.
        for (String inventado : new String[] {"otros", "otro", "ajuste", "varios", "OTROS", ""}) {
            SQLException e = insertarCierre(inventado, 0, "x", LocalDate.of(2026, 9, 10));
            assertNotNull(e, "la base aceptó el valor de fuente '" + inventado + "'");
            // No se exige CUÁL de las dos restricciones dispara. Postgres las
            // evalúa por orden alfabético, así que `ck_..._qr_coherencia` suele
            // ganarle a `ck_..._qr_fuente` — y eso es defensa en profundidad
            // funcionando: un valor fuera del enum tampoco encaja en ninguna
            // rama de la coherencia. Lo que importa es que la fila NO entra.
            assertTrue(e.getMessage().contains("ck_daily_closures_qr_"),
                    "esperaba el rechazo de alguna restricción de qr, vino: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("un fallo de integración SIN motivo técnico se rechaza")
    void elFalloExigeExplicacion() {
        // Un fallo sin explicación no sirve para diagnosticar nada, que es
        // justamente lo que pasó con "posible falta de internet".
        SQLException sinDetalle = insertarCierre("fallo_integracion", 0, null,
                LocalDate.of(2026, 9, 11));
        assertNotNull(sinDetalle);
        assertTrue(sinDetalle.getMessage().contains("ck_daily_closures_qr_detalle"));

        SQLException detalleVacio = insertarCierre("fallo_integracion", 0, "   ",
                LocalDate.of(2026, 9, 12));
        assertNotNull(detalleVacio, "un detalle en blanco no cuenta como explicación");
    }

    @Test
    @DisplayName("no se puede declarar conciliado con confianza 0")
    void coherenciaEntreFuenteYConfianza() {
        SQLException e = insertarCierre("conciliado_core", 0, null, LocalDate.of(2026, 9, 13));
        assertNotNull(e, "un dato conciliado no puede declararse sin confianza");
        assertTrue(e.getMessage().contains("ck_daily_closures_qr_coherencia"));
    }

    @Test
    @DisplayName("no se puede declarar un valor manual como fiable")
    void loManualNoPuedeSerFiable() {
        SQLException manual = insertarCierre("manual_cajero", 2, null, LocalDate.of(2026, 9, 14));
        assertNotNull(manual, "lo que teclea una persona no está conciliado contra nada");

        SQLException fallo = insertarCierre("fallo_integracion", 3, "HTTP 500",
                LocalDate.of(2026, 9, 15));
        assertNotNull(fallo, "un fallo no puede producir un dato de máxima confianza");
    }

    @Test
    @DisplayName("la confianza vive en la escala 0-3 (regla 5)")
    void escalaAcotada() {
        assertNotNull(insertarCierre("conciliado_core", 4, null, LocalDate.of(2026, 9, 16)));
        assertNotNull(insertarCierre("conciliado_core", -1, null, LocalDate.of(2026, 9, 17)));
    }

    @Test
    @DisplayName("los cierres anteriores a V34 se admiten con procedencia NULL")
    void elHistoricoNoSeInventa() {
        // No se les puede asignar una fuente: no hay forma de saber si su QR se
        // concilió. NULL significa "de antes de que esto se registrara" y no se
        // confunde con ninguno de los tres valores.
        assertNull(insertarCierre(null, null, null, LocalDate.of(2026, 9, 20)));
    }
}
