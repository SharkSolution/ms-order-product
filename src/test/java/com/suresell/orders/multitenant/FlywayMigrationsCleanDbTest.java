package com.suresell.orders.multitenant;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Valida que TODAS las migraciones (V1→VN) aplican limpias sobre un Postgres fresco
 * — exactamente lo que hará un deploy de producción con Flyway ON (hoy staging las
 * aplica por MCP con Flyway OFF). Además comprueba end-to-end la numeración de orden
 * POR NEGOCIO introducida en V5. Ver docs/100 §5 / docs/120.
 */
@Testcontainers
class FlywayMigrationsCleanDbTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static MigrateResult result;

    @BeforeAll
    static void migrate() {
        result = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void allMigrationsApplyCleanly() {
        assertTrue(result.success, "Flyway debe migrar sin error");
        // Nota F5: las ramas nocturnas coordinan numeración (V11 meseros, V12 caja,
        // V13 pagos, V14 bajas) y cada rama puede tener huecos hasta el merge, así
        // que NO se exige contigüidad — solo éxito y un mínimo de migraciones.
        assertTrue(result.migrationsExecuted >= 9,
                "Deben ejecutarse al menos las 9 migraciones existentes (V1..V9)");
    }

    @Test
    void idOrderIsPerTenantCorrelativeStartingAtOne() throws Exception {
        // Nos conectamos como el superusuario del contenedor (bypassa RLS), pero el
        // trigger BEFORE INSERT sigue asignando id_order desde el contador por-tenant.
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement st = c.createStatement()) {

            insertOrder(c, "negocio-a");
            insertOrder(c, "negocio-a");
            insertOrder(c, "negocio-b");

            // Cada negocio numera desde 1, independiente del otro.
            assertEquals("1,2", idOrders(st, "negocio-a"));
            assertEquals("1", idOrders(st, "negocio-b"));
        }
    }

    private void insertOrder(Connection c, String tenant) throws Exception {
        try (var ps = c.prepareStatement(
                "INSERT INTO orders (uuid_id, tenant_id, status) VALUES (?, ?, 'TEST')")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, tenant);
            ps.executeUpdate();
        }
    }

    private String idOrders(Statement st, String tenant) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (ResultSet rs = st.executeQuery(
                "SELECT id_order FROM orders WHERE tenant_id = '" + tenant
                        + "' ORDER BY id_order")) {
            while (rs.next()) {
                if (sb.length() > 0) sb.append(',');
                sb.append(rs.getLong(1));
            }
        }
        return sb.toString();
    }
}
