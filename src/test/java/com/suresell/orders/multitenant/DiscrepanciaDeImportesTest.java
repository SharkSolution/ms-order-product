package com.suresell.orders.multitenant;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
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
 * La discrepancia entre el total del cliente y el del servidor queda registrada.
 *
 * <h3>Qué señal es esta y qué NO es</h3>
 *
 * El servidor descarta los importes del cliente y usa siempre los suyos — eso no
 * cambia, y es lo que impide que un POS manipulado fije el importe de su propia
 * venta. Lo que cambia es que además los <b>compara</b>.
 *
 * <p>La diferencia detecta dos cosas distintas y las dos importan:
 *
 * <ul>
 *   <li>un POS con el código alterado para inflar o desinflar totales;</li>
 *   <li>un desfase de catálogo entre terminal y servidor —el POS vendió con un
 *       precio viejo— que hoy es completamente invisible.</li>
 * </ul>
 *
 * <p><b>La discrepancia es señal, no autoridad:</b> no participa en ningún
 * cálculo.
 */
@Testcontainers
class DiscrepanciaDeImportesTest {

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

    private void insertar(BigDecimal total, BigDecimal discrepancia) throws SQLException {
        try (Connection c = conexion();
             var ps = c.prepareStatement("""
                     INSERT INTO orders (uuid_id, tenant_id, status, total, total_discrepancia,
                                         registrado_en)
                     VALUES (?, ?, 'pagado', ?, ?, now())
                     """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, NEGOCIO);
            ps.setBigDecimal(3, total);
            ps.setBigDecimal(4, discrepancia);
            ps.executeUpdate();
        }
    }

    // =====================================================================

    @Test
    @DisplayName("la columna existe y admite negativos: un POS puede DESinflar, no solo inflar")
    void admiteDiferenciasEnLosDosSentidos() throws Exception {
        assertDoesNotThrow(() -> insertar(new BigDecimal("23000"), new BigDecimal("1000")));
        assertDoesNotThrow(() -> insertar(new BigDecimal("23000"), new BigDecimal("-1000")));
    }

    @Test
    @DisplayName("NULL y CERO son distintos: 'no había con qué comparar' no es 'coinciden'")
    void ausenteNoEsCero() throws Exception {
        insertar(new BigDecimal("10000"), null);              // cliente viejo, sin total
        insertar(new BigDecimal("10000"), BigDecimal.ZERO);   // comparado y coincide

        try (Connection c = conexion();
             var ps = c.prepareStatement("""
                     SELECT count(*) FILTER (WHERE total_discrepancia IS NULL)  AS sin_comparar,
                            count(*) FILTER (WHERE total_discrepancia = 0)      AS coinciden
                     FROM orders WHERE tenant_id = ? AND total = 10000
                     """)) {
            ps.setString(1, NEGOCIO);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("sin_comparar"),
                        "un cliente que no manda total no es un cliente que coincide");
                assertEquals(1, rs.getInt("coinciden"));
            }
        }
    }

    @Test
    @DisplayName("la consulta de vigilancia encuentra las órdenes con discrepancia")
    void laConsultaDeVigilanciaFunciona() throws Exception {
        String negocio = "negocio-vigilancia";
        try (Connection c = conexion();
             var ps = c.prepareStatement("""
                     INSERT INTO orders (uuid_id, tenant_id, status, total, total_discrepancia,
                                         registrado_en)
                     VALUES (?, ?, 'pagado', 1000, ?, now())
                     """)) {
            for (String d : new String[] {"0", "0", "500", null}) {
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, negocio);
                if (d == null) {
                    ps.setNull(3, java.sql.Types.NUMERIC);
                } else {
                    ps.setBigDecimal(3, new BigDecimal(d));
                }
                ps.executeUpdate();
            }
        }

        // Es la consulta de docs/CONSULTAS-VIGILANCIA.md §5, literal.
        try (Connection c = conexion();
             var ps = c.prepareStatement("""
                     SELECT count(*) AS ordenes, sum(abs(total_discrepancia)) AS acumulado
                     FROM   orders
                     WHERE  tenant_id = ?
                       AND  total_discrepancia IS NOT NULL
                       AND  total_discrepancia <> 0
                     """)) {
            ps.setString(1, negocio);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("ordenes"), "solo una de las cuatro discrepa");
                assertEquals(0, new BigDecimal("500").compareTo(rs.getBigDecimal("acumulado")));
            }
        }
    }

    @Test
    @DisplayName("guardar la diferencia y no el importe del cliente no pierde información")
    void laDiferenciaBastaParaReconstruir() throws Exception {
        // Se guarda la diferencia porque cero es la respuesta esperada y una
        // columna que casi siempre vale cero es barata de indexar. El importe
        // del cliente se reconstruye sumando, sin haber perdido nada.
        insertar(new BigDecimal("23000"), new BigDecimal("1000"));

        try (Connection c = conexion();
             var ps = c.prepareStatement("""
                     SELECT total + total_discrepancia AS declarado_por_el_cliente
                     FROM orders
                     WHERE tenant_id = ? AND total_discrepancia = 1000
                     LIMIT 1
                     """)) {
            ps.setString(1, NEGOCIO);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, new BigDecimal("24000")
                        .compareTo(rs.getBigDecimal("declarado_por_el_cliente")));
            }
        }
    }
}
