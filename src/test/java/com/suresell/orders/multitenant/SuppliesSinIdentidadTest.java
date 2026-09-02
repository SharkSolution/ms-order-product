package com.suresell.orders.multitenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * `supplies` deja de crear y de borrar insumos.
 *
 * <p>Existe por un caso real: el 2026-09-01 se borraron dos insumos de prueba
 * desde la pantalla vieja y <b>siguen vivos en la nueva</b>. Dos listas del
 * mismo inventario, y ninguna manda sobre la otra.
 *
 * <p>Lo que se prueba aquí es la <b>línea</b>: qué se frena y qué no. Frenar de
 * más habría quitado capacidad que todavía se usa —4 insumos tienen stock y 2
 * tienen alerta de mínimo—, y eso no arregla nada.
 */
@Testcontainers
class SuppliesSinIdentidadTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrar() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load().migrate();
    }

    private static Connection conexion() throws SQLException {
        Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        try (Statement st = c.createStatement()) {
            st.execute("SET app.tenant_id = 'shark-burger'");
        }
        return c;
    }

    /**
     * Una fila de partida, metida SIN pasar por el trigger.
     *
     * <p>Se desactiva a propósito: si se creara con el trigger puesto, no se
     * podría — que es justo lo que este test comprueba.
     */
    @BeforeEach
    void unInsumoDePartida() throws SQLException {
        try (Connection c = conexion(); Statement st = c.createStatement()) {
            st.execute("ALTER TABLE public.supplies DISABLE TRIGGER trg_supplies_sin_identidad");
            st.execute("DELETE FROM public.supplies WHERE tenant_id = 'shark-burger'");
            // `supply_category_id` es NOT NULL: hace falta una categoría antes.
            st.execute("INSERT INTO public.supply_categories (tenant_id, name)"
                    + " SELECT 'shark-burger','Secos' WHERE NOT EXISTS ("
                    + "   SELECT 1 FROM public.supply_categories"
                    + "    WHERE tenant_id='shark-burger' AND name='Secos')");
            st.execute("INSERT INTO public.supplies"
                    + " (tenant_id, name, unit, stock, min_stock, price,"
                    + "  supply_category_id, created_at, updated_at)"
                    + " SELECT 'shark-burger','Harina','Kg',10,2,0, c.id, now(), now()"
                    + "   FROM public.supply_categories c"
                    + "  WHERE c.tenant_id='shark-burger' AND c.name='Secos'");
            st.execute("ALTER TABLE public.supplies ENABLE TRIGGER trg_supplies_sin_identidad");
        }
    }

    private void ejecutar(String sql) throws SQLException {
        try (Connection c = conexion(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private String escalar(String sql) throws SQLException {
        try (Connection c = conexion(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    // =====================================================================

    @Test
    @DisplayName("🔴 no se puede CREAR un insumo aquí, y se dice dónde sí")
    void noSeCrea() {
        assertThatThrownBy(() -> ejecutar(
                "INSERT INTO public.supplies (tenant_id, name, unit)"
                + " VALUES ('shark-burger','Azúcar','Kg')"))
                .hasMessageContaining("Insumos y Compras");
    }

    @Test
    @DisplayName("🔴 no se puede BORRAR: es lo que pasó de verdad con aaa y test2")
    void noSeBorra() {
        assertThatThrownBy(() -> ejecutar(
                "DELETE FROM public.supplies WHERE name = 'Harina'"))
                .hasMessageContaining("seguiria existiendo");
    }

    @Test
    @DisplayName("no se puede renombrar: el mismo insumo con dos nombres")
    void noSeRenombra() {
        assertThatThrownBy(() -> ejecutar(
                "UPDATE public.supplies SET name = 'Harina de trigo' WHERE name = 'Harina'"))
                .hasMessageContaining("El nombre se cambia");
    }

    @Test
    @DisplayName("no se puede cambiar la unidad: aquí no cambia ningún cálculo")
    void noSeCambiaLaUnidad() {
        assertThatThrownBy(() -> ejecutar(
                "UPDATE public.supplies SET unit = 'Gr' WHERE name = 'Harina'"))
                .hasMessageContaining("La unidad se declara");
    }

    // ---------------------------------------------------------------------
    // Y ahora la otra mitad de la línea, que importa igual: frenar de más
    // habría roto lo poco del módulo viejo que todavía se usa.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("✅ el STOCK se sigue pudiendo tocar")
    void elStockSigue() throws SQLException {
        ejecutar("UPDATE public.supplies SET stock = 42 WHERE name = 'Harina'");
        assertThat(escalar("SELECT stock::int FROM public.supplies WHERE name='Harina'"))
                .isEqualTo("42");
    }

    @Test
    @DisplayName("✅ y la alerta de mínimo también")
    void elMinimoSigue() throws SQLException {
        ejecutar("UPDATE public.supplies SET min_stock = 7 WHERE name = 'Harina'");
        assertThat(escalar("SELECT min_stock::int FROM public.supplies WHERE name='Harina'"))
                .isEqualTo("7");
    }

    @Test
    @DisplayName("✅ y el precio, que no lo usa nadie para costear pero es suyo")
    void elPrecioSigue() throws SQLException {
        ejecutar("UPDATE public.supplies SET price = 1500 WHERE name = 'Harina'");
        assertThat(escalar("SELECT price::int FROM public.supplies WHERE name='Harina'"))
                .isEqualTo("1500");
    }

    @Test
    @DisplayName("un UPDATE que no toca la identidad pasa aunque toque varias columnas")
    void variasColumnasOperativas() throws SQLException {
        ejecutar("UPDATE public.supplies SET stock = 1, min_stock = 1, price = 1"
                 + " WHERE name = 'Harina'");
        assertThat(escalar("SELECT (stock+min_stock+price)::int FROM public.supplies"
                + " WHERE name='Harina'")).isEqualTo("3");
    }
}
