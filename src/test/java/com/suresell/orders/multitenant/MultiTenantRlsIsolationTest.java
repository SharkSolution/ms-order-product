package com.suresell.orders.multitenant;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifica el AISLAMIENTO REAL entre tenants (Row-Level Security) contra un
 * Postgres efímero (Testcontainers). Prueba que la política RLS de la migración
 * V1 impide que un tenant vea o escriba datos de otro. Ver docs/40-multitenant.md.
 *
 * Clave: nos conectamos como `app_user` (no superusuario) para que RLS aplique;
 * un superusuario la saltaría.
 */
@Testcontainers
class MultiTenantRlsIsolationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    /** Conexión como rol de aplicación (no superusuario) → RLS activa. */
    private Connection appConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_user", "app_pw");
    }

    private void setTenant(Connection c, String tenant) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenant);
            ps.execute();
        }
    }

    private void insertOrder(Connection c, String tenant, long idOrder) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO orders (uuid_id, tenant_id, id_order, total) VALUES (?, ?, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, tenant);
            ps.setLong(3, idOrder);
            ps.setBigDecimal(4, new BigDecimal("10000"));
            ps.executeUpdate();
        }
    }

    private int countOrders(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT count(*) FROM orders")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void cadaTenantSoloVeSusPropiasFilas() throws SQLException {
        try (Connection a = appConnection()) {
            setTenant(a, "t-iso-a");
            insertOrder(a, "t-iso-a", 1);
            insertOrder(a, "t-iso-a", 2);
        }
        try (Connection b = appConnection()) {
            setTenant(b, "t-iso-b");
            insertOrder(b, "t-iso-b", 1);
        }
        try (Connection a = appConnection()) {
            setTenant(a, "t-iso-a");
            assertEquals(2, countOrders(a), "Tenant A debe ver solo sus 2 órdenes");
        }
        try (Connection b = appConnection()) {
            setTenant(b, "t-iso-b");
            assertEquals(1, countOrders(b), "Tenant B debe ver solo su orden");
        }
    }

    @Test
    void sinTenantFijadoNoVeNingunaFila() throws SQLException {
        try (Connection a = appConnection()) {
            setTenant(a, "t-none");
            insertOrder(a, "t-none", 10);
        }
        try (Connection c = appConnection()) {
            // No se fija app.tenant_id → current_setting(...,true) = NULL → 0 filas.
            assertEquals(0, countOrders(c), "Sin tenant fijado no debe verse ninguna fila");
        }
    }

    @Test
    void noSePuedeInsertarParaOtroTenant() throws SQLException {
        try (Connection a = appConnection()) {
            setTenant(a, "t-check");
            // WITH CHECK rechaza insertar una fila con un tenant distinto al del contexto.
            SQLException ex = assertThrows(SQLException.class, () -> insertOrder(a, "t-otro", 99));
            assertNotNull(ex);
        }
    }

    // ----- V2: resto de tablas (discount_coupon) -----

    private void insertCoupon(Connection c, String tenant, long id, String code) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO discount_coupon (id, tenant_id, code) VALUES (?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setString(2, tenant);
            ps.setString(3, code);
            ps.executeUpdate();
        }
    }

    @Test
    void rlsAplicaAlRestoDeTablas_discountCoupon() throws SQLException {
        try (Connection a = appConnection()) {
            setTenant(a, "t-rls-a");
            insertCoupon(a, "t-rls-a", 2001, "A1");
            insertCoupon(a, "t-rls-a", 2002, "A2");
        }
        try (Connection b = appConnection()) {
            setTenant(b, "t-rls-b");
            insertCoupon(b, "t-rls-b", 2003, "B1");
        }
        try (Connection a = appConnection()) {
            setTenant(a, "t-rls-a");
            try (Statement s = a.createStatement();
                 ResultSet rs = s.executeQuery("SELECT count(*) FROM discount_coupon")) {
                rs.next();
                assertEquals(2, rs.getInt(1), "Tenant A ve solo sus 2 cupones");
            }
        }
    }

    @Test
    void codigoDeCuponEsUnicoPorTenantNoGlobal() throws SQLException {
        // Dos tenants pueden tener el MISMO código de cupón (único por tenant).
        try (Connection a = appConnection()) {
            setTenant(a, "t-cup-a");
            insertCoupon(a, "t-cup-a", 1001, "BIENVENIDA");
        }
        try (Connection b = appConnection()) {
            setTenant(b, "t-cup-b");
            insertCoupon(b, "t-cup-b", 1002, "BIENVENIDA");
        }
        // El MISMO tenant NO puede repetir el código.
        try (Connection a = appConnection()) {
            setTenant(a, "t-cup-a");
            SQLException ex = assertThrows(SQLException.class,
                    () -> insertCoupon(a, "t-cup-a", 1003, "BIENVENIDA"));
            assertNotNull(ex);
        }
    }
}
