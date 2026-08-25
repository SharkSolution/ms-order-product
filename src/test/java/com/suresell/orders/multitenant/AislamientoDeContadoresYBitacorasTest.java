package com.suresell.orders.multitenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V38 — aislamiento de {@code tenant_order_counters}, {@code order_counter_audit}
 * y {@code site_mode_audit}.
 *
 * <p>Por qué este test existe aparte del bloque {@code DO $verificar$} de la
 * migración: ese bloque corre como el dueño de la tabla, que tiene BYPASSRLS.
 * Desde ahí se ven todas las filas siempre, con política abierta y con política
 * cerrada. Una comprobación de aislamiento escrita dentro de la migración daría
 * verde en los dos escenarios y no distinguiría nada. Aquí se conecta como
 * {@code app_user}, que es el rol con el que conecta la aplicación.
 *
 * <p>Cada prueba mide las filas AJENAS visibles, no solo las propias. Contar las
 * propias no sirve: con la política abierta también salen las suyas, así que el
 * número sería el mismo estando roto.
 */
@Testcontainers
class AislamientoDeContadoresYBitacorasTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String ALFA = "qa-alfa";
    private static final String BETA = "qa-beta";

    @BeforeAll
    static void migrarYSembrar() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        // La siembra va como dueño (BYPASSRLS): interesa que los datos existan,
        // no probar la escritura todavía.
        try (Connection c = conexionDueno()) {
            for (String t : new String[] {ALFA, BETA}) {
                ejecutar(c, "INSERT INTO tenants (id, name) VALUES ('" + t + "', '" + t + "')");
                ejecutar(c, "INSERT INTO sites (tenant_id, name, code, is_default) VALUES ('"
                        + t + "', 'Principal', 'PRINCIPAL', true)");
                ejecutar(c, "INSERT INTO tenant_order_counters (tenant_id, site_id, last_id) "
                        + "SELECT '" + t + "', id, 100 FROM sites WHERE tenant_id = '" + t + "'");
                ejecutar(c, "INSERT INTO order_counter_audit "
                        + "(tenant_id, site_id, valor_antes, valor_despues, motivo, hecho_por) "
                        + "SELECT '" + t + "', id, 0, 100, 'siembra', 'qa' "
                        + "FROM sites WHERE tenant_id = '" + t + "'");
                ejecutar(c, "INSERT INTO site_mode_audit "
                        + "(tenant_id, site_id, modo_antes, modo_despues, hecho_por) "
                        + "SELECT '" + t + "', id, 'PLAZOLETA', 'RESTAURANTE', 'qa' "
                        + "FROM sites WHERE tenant_id = '" + t + "'");
            }
        }
    }

    private static Connection conexionDueno() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** Conexión como rol de aplicación (sin BYPASSRLS) → RLS aplica. */
    private static Connection conexionApp() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_user", "app_pw");
    }

    private static void ejecutar(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private static void fijarNegocio(Connection c, String tenant) throws SQLException {
        try (PreparedStatement ps =
                c.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenant);
            ps.execute();
        }
    }

    private static int contar(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static final String[] TABLAS = {
        "tenant_order_counters", "order_counter_audit", "site_mode_audit"
    };

    @Nested
    @DisplayName("Lectura")
    class Lectura {

        @Test
        @DisplayName("cada negocio ve sus filas y CERO ajenas")
        void ningunaFilaAjenaEsVisible() throws SQLException {
            try (Connection c = conexionApp()) {
                for (String tenant : new String[] {ALFA, BETA}) {
                    fijarNegocio(c, tenant);
                    for (String tabla : TABLAS) {
                        int propias = contar(c, "SELECT count(*) FROM " + tabla
                                + " WHERE tenant_id = '" + tenant + "'");
                        int ajenas = contar(c, "SELECT count(*) FROM " + tabla
                                + " WHERE tenant_id <> '" + tenant + "'");
                        assertEquals(1, propias,
                                tabla + ": " + tenant + " debería ver su propia fila");
                        assertEquals(0, ajenas,
                                tabla + ": " + tenant + " ve " + ajenas + " filas de otro negocio");
                    }
                }
            }
        }

        @Test
        @DisplayName("sin negocio en sesión no se ve nada: falla CERRADO, no abierto")
        void sinNegocioNoSeVeNada() throws SQLException {
            try (Connection c = conexionApp()) {
                // La cadena vacía es exactamente lo que fija TenantAwareDataSource:54
                // cuando no hay negocio en contexto. No es un caso teórico.
                fijarNegocio(c, "");
                for (String tabla : TABLAS) {
                    assertEquals(0, contar(c, "SELECT count(*) FROM " + tabla),
                            tabla + ": con negocio vacío se están viendo filas");
                }
            }
        }
    }

    @Nested
    @DisplayName("Escritura")
    class Escritura {

        @Test
        @DisplayName("no se puede escribir una bitácora a nombre de otro negocio")
        void elWithCheckRechazaLaEscrituraCruzada() throws SQLException {
            try (Connection c = conexionApp()) {
                fijarNegocio(c, ALFA);
                // site_id literal: si se buscara la sede de BETA con un SELECT,
                // RLS sobre `sites` devolvería cero filas y el INSERT no
                // insertaría nada — pasaría el test sin haber probado el
                // WITH CHECK de site_mode_audit, que es lo que se quiere probar.
                SQLException e = assertThrows(SQLException.class, () ->
                        ejecutar(c, "INSERT INTO site_mode_audit "
                                + "(tenant_id, site_id, modo_antes, modo_despues, hecho_por) "
                                + "VALUES ('" + BETA + "', 1, 'PLAZOLETA', 'RESTAURANTE', 'intruso')"));
                assertTrue(e.getMessage().contains("row-level security"),
                        "esperaba rechazo de RLS, vino: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("el consecutivo propio sí se puede avanzar")
        void laEscrituraPropiaSigueFuncionando() throws SQLException {
            try (Connection c = conexionApp()) {
                fijarNegocio(c, ALFA);
                ejecutar(c, "UPDATE tenant_order_counters SET last_id = last_id + 1 "
                        + "WHERE tenant_id = '" + ALFA + "'");
                assertEquals(101, contar(c, "SELECT last_id FROM tenant_order_counters "
                        + "WHERE tenant_id = '" + ALFA + "'"));
            }
        }
    }

    @Nested
    @DisplayName("Forma de la política")
    class FormaDeLaPolitica {

        @Test
        @DisplayName("no sobrevive ninguna política abierta junto a la nueva")
        void ningunaPoliticaAbierta() throws SQLException {
            // La trampa concreta: la política vieja de tenant_order_counters se
            // llama `app_rw_order_counters`, no `app_rw_tenant_order_counters`.
            // Un DROP por nombre construido a partir de la tabla no la habría
            // encontrado, no habría dado error, y habría quedado viva. Postgres
            // combina las políticas permisivas con OR, así que `USING (true)`
            // habría anulado el aislamiento con la migración en verde.
            try (Connection c = conexionDueno()) {
                for (String tabla : TABLAS) {
                    assertEquals(1, contar(c, "SELECT count(*) FROM pg_policies "
                                    + "WHERE schemaname='public' AND tablename='" + tabla + "'"),
                            tabla + ": debe quedar exactamente una política");
                    assertEquals(0, contar(c, "SELECT count(*) FROM pg_policies "
                                    + "WHERE schemaname='public' AND tablename='" + tabla + "' "
                                    + "AND (coalesce(qual,'')='true' OR coalesce(with_check,'')='true')"),
                            tabla + ": conserva una política abierta");
                }
            }
        }

        @Test
        @DisplayName("las tres están en FORCE")
        void forceRowLevelSecurity() throws SQLException {
            try (Connection c = conexionDueno()) {
                for (String tabla : TABLAS) {
                    assertEquals(1, contar(c, "SELECT count(*) FROM pg_class c "
                                    + "JOIN pg_namespace n ON n.oid=c.relnamespace "
                                    + "WHERE n.nspname='public' AND c.relname='" + tabla + "' "
                                    + "AND c.relrowsecurity AND c.relforcerowsecurity"),
                            tabla + ": falta ENABLE o FORCE row level security");
                }
            }
        }

        @Test
        @DisplayName("las bitácoras siguen siendo solo-añadir: sin UPDATE ni DELETE")
        void lasBitacorasNoSeAmplian() throws SQLException {
            // V33 daba GRANT SELECT,INSERT,UPDATE,DELETE a ciegas. Si alguien
            // copia ese bloque aquí, estas dos tablas dejarían de ser bitácoras
            // y nada más lo notaría.
            try (Connection c = conexionDueno()) {
                for (String tabla : new String[] {"order_counter_audit", "site_mode_audit"}) {
                    assertEquals(0, contar(c, "SELECT count(*) FROM information_schema.role_table_grants "
                                    + "WHERE grantee='app_user' AND table_name='" + tabla + "' "
                                    + "AND privilege_type IN ('UPDATE','DELETE')"),
                            tabla + ": app_user ganó permiso de modificar o borrar la bitácora");
                }
            }
        }
    }

    @Nested
    @DisplayName("La cuarta tabla — cerrada en V40")
    class LaCuartaTabla {

        @Test
        @DisplayName("tenant_modules ya NO es legible sin negocio en sesión")
        void tenantModulesQuedoCerradaEnV40() throws SQLException {
            // Este caso afirmaba lo CONTRARIO hasta V40, y era deliberado: era una
            // guarda para que nadie cerrara la política sin arreglar antes el
            // login, que la lee (AuthService.login -> effectiveModulesFor ->
            // AuthRepository.getOverrides) con la conexión en app.tenant_id = ''.
            // Cerrarla a ciegas no habría dado error: habría devuelto cero
            // overrides y el JWT habría salido con los módulos del plan a secas.
            //
            // La guarda hizo su trabajo: se puso roja al cerrar la política en
            // V40 y obligó a comprobar que el login estaba resuelto. Lo está, y
            // por la vía barata —`set_config` dentro de la transacción del
            // login— y no con una función privilegiada, que aquí habría sido
            // ampliar superficie sin motivo: en ese punto el negocio ya se conoce.
            //
            // Lo que sostiene ahora la parte del login es
            // ModulosConLaPoliticaCerradaTest, que comprueba con un negocio que
            // TIENE overrides que el login se los sigue devolviendo. Con uno
            // limpio, "se aplican" y "se perdieron" darían la misma lista.
            try (Connection c = conexionDueno()) {
                ejecutar(c, "INSERT INTO tenant_modules (tenant_id, module, enabled) "
                        + "VALUES ('" + ALFA + "', 'valeras', true) ON CONFLICT DO NOTHING");
            }
            try (Connection c = conexionApp()) {
                fijarNegocio(c, "");
                assertEquals(0, contar(c, "SELECT count(*) FROM tenant_modules"),
                        "tenant_modules sigue siendo legible sin negocio en sesión");
                fijarNegocio(c, ALFA);
                assertEquals(0, contar(c, "SELECT count(*) FROM tenant_modules "
                                + "WHERE tenant_id <> '" + ALFA + "'"),
                        "se ven overrides de otro negocio");
            }
        }
    }
}
