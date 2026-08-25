package com.suresell.orders.multitenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V39 — aislamiento de {@code users} y {@code password_resets}, y blindaje de las
 * cuatro funciones {@code SECURITY DEFINER} que las hacen usables sin sesión.
 *
 * <p>Todas las lecturas van como {@code app_user}, que es el rol de la
 * aplicación y no tiene BYPASSRLS. Desde el dueño se ve todo siempre, con la
 * política abierta y con la cerrada, así que un test escrito desde ahí daría
 * verde en los dos escenarios.
 *
 * <p>Y cada caso mide las filas <b>ajenas</b> visibles, no las propias: contar
 * las propias daría el mismo número estando roto.
 */
@Testcontainers
class AislamientoDeIdentidadTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String ALFA = "qa-alfa";
    private static final String BETA = "qa-beta";
    private static final String EMAIL_ALFA = "duena@qa-alfa.invalid";
    private static final String EMAIL_BETA = "dueno@qa-beta.invalid";

    @BeforeAll
    static void migrarYSembrar() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection c = conexionDueno()) {
            for (String[] par : new String[][] {{ALFA, EMAIL_ALFA}, {BETA, EMAIL_BETA}}) {
                ejecutar(c, "INSERT INTO tenants (id, name) VALUES ('" + par[0] + "','" + par[0] + "')");
                ejecutar(c, "INSERT INTO users (email, password_hash, tenant_id, role) VALUES ('"
                        + par[1] + "', '$2a$10$hashdeprueba', '" + par[0] + "', 'admin')");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO password_resets (token_hash, email, tenant_id, expires_at) "
                            + "VALUES (?, ?, ?, ?)")) {
                for (String[] par : new String[][] {{ALFA, EMAIL_ALFA}, {BETA, EMAIL_BETA}}) {
                    ps.setString(1, "hash-de-" + par[0]);
                    ps.setString(2, par[1]);
                    ps.setString(3, par[0]);
                    ps.setTimestamp(4, Timestamp.from(Instant.now().plus(1, ChronoUnit.HOURS)));
                    ps.executeUpdate();
                }
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

    // =====================================================================
    @Nested
    @DisplayName("Aislamiento de las tablas")
    class Tablas {

        @Test
        @DisplayName("cada negocio ve sus cuentas y CERO ajenas")
        void ningunaCuentaAjenaEsVisible() throws SQLException {
            try (Connection c = conexionApp()) {
                for (String tenant : new String[] {ALFA, BETA}) {
                    fijarNegocio(c, tenant);
                    for (String tabla : new String[] {"users", "password_resets"}) {
                        // Las propias se comprueban con >= 1 y no con un número
                        // exacto: otros casos de esta clase siembran filas y el
                        // conteo exacto ataría el test al orden de ejecución. Lo
                        // que de verdad se afirma es la línea de abajo.
                        assertTrue(contar(c, "SELECT count(*) FROM " + tabla
                                        + " WHERE tenant_id = '" + tenant + "'") >= 1,
                                tabla + ": " + tenant + " no ve lo suyo");
                        assertEquals(0, contar(c, "SELECT count(*) FROM " + tabla
                                        + " WHERE tenant_id <> '" + tenant + "'"),
                                tabla + ": " + tenant + " ve filas de otro negocio");
                    }
                }
            }
        }

        @Test
        @DisplayName("sin negocio en sesión no se ve nada: falla CERRADO")
        void sinNegocioNoSeVeNada() throws SQLException {
            try (Connection c = conexionApp()) {
                // La cadena vacía es lo que fija TenantAwareDataSource:54 cuando
                // no hay negocio en contexto. No es un caso teórico: es el estado
                // de TODA petición a /auth/**.
                fijarNegocio(c, "");
                assertEquals(0, contar(c, "SELECT count(*) FROM users"));
                assertEquals(0, contar(c, "SELECT count(*) FROM password_resets"));
            }
        }

        @Test
        @DisplayName("no se puede crear un usuario a nombre de otro negocio")
        void elWithCheckRechazaLaEscrituraCruzada() throws SQLException {
            try (Connection c = conexionApp()) {
                fijarNegocio(c, ALFA);
                SQLException e = assertThrows(SQLException.class, () ->
                        ejecutar(c, "INSERT INTO users (email, password_hash, tenant_id, role) "
                                + "VALUES ('intruso@x.invalid', 'h', '" + BETA + "', 'admin')"));
                assertTrue(e.getMessage().contains("row-level security"),
                        "esperaba rechazo de RLS, vino: " + e.getMessage());
            }
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("Las funciones SECURITY DEFINER")
    class Funciones {

        @Test
        @DisplayName("🔴 buscar_usuario_para_login encuentra al usuario SIN negocio en sesión")
        void elLoginFuncionaSinNegocio() throws SQLException {
            try (Connection c = conexionApp()) {
                fijarNegocio(c, "");

                // El contraste ES la prueba. La consulta directa no ve nada
                // —eso ya lo comprueba el test de arriba— y la función sí. Si se
                // midiera solo la función, no se distinguiría "la función
                // funciona" de "la política nunca se cerró".
                assertEquals(0, contar(c, "SELECT count(*) FROM users WHERE email = '"
                        + EMAIL_ALFA + "'"), "la consulta directa no debería ver nada");

                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT email, tenant_id, password_hash, rol, activo "
                                + "FROM buscar_usuario_para_login(?)")) {
                    ps.setString(1, EMAIL_ALFA);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next(), "la función no encontró al usuario");
                        assertEquals(EMAIL_ALFA, rs.getString("email"));
                        assertEquals(ALFA, rs.getString("tenant_id"));
                        assertEquals("admin", rs.getString("rol"));
                        assertTrue(rs.getBoolean("activo"));
                        assertTrue(rs.getString("password_hash").startsWith("$2a$"));
                        assertTrue(!rs.next(), "devolvió más de una fila");
                    }
                }
            }
        }

        @Test
        @DisplayName("buscar_usuario_para_login devuelve CINCO columnas, y ninguna es users.id")
        void devuelveElMinimo() throws SQLException {
            // El encargo listaba `id` entre las columnas. No se devuelve: el login
            // no lo usa —issueToken firma con tenant+email+rol y AuthResponse no
            // lo expone— y sacarlo de una tabla de credenciales sin que nadie lo
            // consuma solo aumenta lo expuesto.
            try (Connection c = conexionApp();
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                         "SELECT string_agg(p.nombre, ',' ORDER BY p.orden) FROM ("
                                 + "SELECT unnest(proargnames) AS nombre, "
                                 + "       generate_subscripts(proargnames, 1) AS orden "
                                 + "FROM pg_proc WHERE proname = 'buscar_usuario_para_login') p "
                                 + "WHERE p.nombre <> 'p_email'")) {
                rs.next();
                assertEquals("email,tenant_id,password_hash,rol,activo", rs.getString(1));
            }
        }

        @Test
        @DisplayName("🔴 buscar_token_de_reset encuentra el token SIN negocio en sesión")
        void elResetFuncionaSinNegocio() throws SQLException {
            try (Connection c = conexionApp()) {
                fijarNegocio(c, "");
                assertEquals(0, contar(c, "SELECT count(*) FROM password_resets"));

                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT estado, email, tenant_id FROM buscar_token_de_reset(?)")) {
                    ps.setString(1, "hash-de-" + BETA);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("valido", rs.getString("estado"));
                        assertEquals(BETA, rs.getString("tenant_id"));
                    }
                }
            }
        }

        @Test
        @DisplayName("la misma forma exista o no el usuario: cero filas, sin error distinto")
        void mismaFormaExistaONo() throws SQLException {
            try (Connection c = conexionApp()) {
                fijarNegocio(c, "");
                // Un email desconocido devuelve cero filas por el mismo camino,
                // sin excepción ni mensaje distinto. La diferencia "hay fila / no
                // hay fila" es inevitable —es la pregunta que el login hace— y ya
                // la expone /auth/login; lo que no debe haber es NINGUNA otra
                // señal. AuthService devuelve el mismo 401 para los dos casos.
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT count(*) FROM buscar_usuario_para_login(?)")) {
                    ps.setString(1, "nadie@ninguna-parte.invalid");
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        assertEquals(0, rs.getInt(1));
                    }
                }
            }
        }

        @Test
        @DisplayName("existe_email responde en toda la plataforma, no solo en el negocio actual")
        void existeEmailEsCrossTenant() throws SQLException {
            try (Connection c = conexionApp()) {
                fijarNegocio(c, ALFA);
                try (PreparedStatement ps = c.prepareStatement("SELECT existe_email(?)")) {
                    // El email es de BETA. Con un count(*) normal daría false
                    // —RLS lo oculta— y el INSERT chocaría contra el índice único.
                    ps.setString(1, EMAIL_BETA);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        assertTrue(rs.getBoolean(1),
                                "un email de otro negocio debe contar como tomado");
                    }
                }
            }
        }

        @Test
        @DisplayName("contar_usuarios_por_negocio ve los dos negocios y no devuelve datos personales")
        void elConteoDelKamEsCrossTenant() throws SQLException {
            try (Connection c = conexionApp()) {
                fijarNegocio(c, "");
                // Sin negocio en sesión, una consulta directa a `users` no vería
                // ninguno. La función ve los dos qa- y además `shark-burger`, que
                // siembra V4:56 — por eso se comprueba la presencia de cada uno y
                // no el total, que depende de la semilla.
                assertEquals(0, contar(c, "SELECT count(*) FROM users"));
                for (String t : new String[] {ALFA, BETA}) {
                    assertEquals(1, contar(c, "SELECT usuarios FROM contar_usuarios_por_negocio() "
                                    + "WHERE tenant_id = '" + t + "'"),
                            "el conteo de " + t + " no llegó");
                }
                // Y devuelve exactamente dos columnas: ni email, ni hash, ni rol.
                try (Statement s = c.createStatement();
                     ResultSet rs = s.executeQuery(
                             "SELECT array_to_string(proargnames, ',') FROM pg_proc "
                                     + "WHERE proname = 'contar_usuarios_por_negocio'")) {
                    rs.next();
                    assertEquals("tenant_id,usuarios", rs.getString(1));
                }
            }
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("Blindaje de las funciones")
    class Blindaje {

        private static final String[] FUNCIONES = {
            "buscar_usuario_para_login", "buscar_token_de_reset",
            "existe_email", "contar_usuarios_por_negocio"
        };

        @Test
        @DisplayName("las cuatro son SECURITY DEFINER y STABLE")
        void definerYStable() throws SQLException {
            try (Connection c = conexionDueno()) {
                for (String f : FUNCIONES) {
                    assertEquals(1, contar(c, "SELECT count(*) FROM pg_proc p "
                                    + "JOIN pg_namespace n ON n.oid = p.pronamespace "
                                    + "WHERE n.nspname='public' AND p.proname='" + f + "' "
                                    + "AND p.prosecdef AND p.provolatile = 's'"),
                            f + ": no es SECURITY DEFINER + STABLE");
                }
            }
        }

        @Test
        @DisplayName("🔴 las cuatro fijan search_path: sin eso son una vía de inyección")
        void searchPathFijado() throws SQLException {
            // Una función SECURITY DEFINER sin search_path fijado se secuestra
            // creando un objeto homónimo en un esquema que vaya antes en la ruta
            // de búsqueda: el atacante acaba ejecutando código con los privilegios
            // del dueño, que aquí tiene BYPASSRLS.
            try (Connection c = conexionDueno()) {
                for (String f : FUNCIONES) {
                    assertEquals(1, contar(c, "SELECT count(*) FROM pg_proc p "
                                    + "WHERE p.proname='" + f + "' AND p.proconfig IS NOT NULL "
                                    + "AND EXISTS (SELECT 1 FROM unnest(p.proconfig) x "
                                    + "            WHERE x LIKE 'search_path=%')"),
                            f + ": no fija search_path");
                }
            }
        }

        @Test
        @DisplayName("🔴 ninguna tiene EXECUTE para PUBLIC: no son alcanzables desde PostgREST")
        void sinExecuteParaPublic() throws SQLException {
            // Por defecto Postgres concede EXECUTE a PUBLIC en toda función nueva.
            // Sin el REVOKE, los roles anon/authenticated de PostgREST podrían
            // llamarlas por la API REST de Supabase — y eso convertiría
            // buscar_usuario_para_login en un endpoint público que devuelve hashes.
            try (Connection c = conexionDueno()) {
                for (String f : FUNCIONES) {
                    // Primero que EXISTE. Sin esta línea el test pasaría en vacío
                    // cuando la migración no se hubiera aplicado —cero funciones
                    // también son cero funciones con EXECUTE para PUBLIC—, que es
                    // el modo de fallo que esta suite entera existe para evitar.
                    assertEquals(1, contar(c, "SELECT count(*) FROM pg_proc p "
                                    + "JOIN pg_namespace n ON n.oid = p.pronamespace "
                                    + "WHERE n.nspname='public' AND p.proname='" + f + "'"),
                            f + ": la función no existe");
                    assertEquals(0, contar(c, "SELECT count(*) FROM pg_proc p "
                                    + "WHERE p.proname='" + f + "' AND ("
                                    + "  p.proacl IS NULL "          // NULL = defecto = PUBLIC
                                    + "  OR EXISTS (SELECT 1 FROM unnest(p.proacl) a "
                                    + "             WHERE a::text LIKE '=X/%'))"),
                            f + ": tiene EXECUTE para PUBLIC (o permisos por defecto)");
                }
            }
        }

        @Test
        @DisplayName("app_user sí puede ejecutarlas")
        void appUserPuedeEjecutar() throws SQLException {
            try (Connection c = conexionDueno()) {
                for (String f : FUNCIONES) {
                    assertEquals(1, contar(c, "SELECT count(*) FROM pg_proc p "
                                    + "WHERE p.proname='" + f + "' AND EXISTS ("
                                    + "  SELECT 1 FROM unnest(p.proacl) a "
                                    + "  WHERE a::text LIKE 'app_user=X/%')"),
                            f + ": app_user no puede ejecutarla");
                }
            }
        }

        @Test
        @DisplayName("un token vencido no devuelve email ni negocio")
        void tokenVencidoNoFiltraDatos() throws SQLException {
            try (Connection c = conexionDueno()) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO password_resets (token_hash, email, tenant_id, expires_at) "
                                + "VALUES ('hash-vencido', ?, ?, ?)")) {
                    ps.setString(1, EMAIL_ALFA);
                    ps.setString(2, ALFA);
                    ps.setTimestamp(3, Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)));
                    ps.executeUpdate();
                }
            }
            try (Connection c = conexionApp()) {
                fijarNegocio(c, "");
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT estado, email, tenant_id FROM buscar_token_de_reset(?)")) {
                    ps.setString(1, "hash-vencido");
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("vencido", rs.getString("estado"));
                        assertNull(rs.getString("email"));
                        assertNull(rs.getString("tenant_id"));
                    }
                }
            }
        }
    }
}
