package com.suresell.orders.multitenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V40 — que cerrar la política de {@code tenant_modules} no le quite a nadie sus
 * módulos.
 *
 * <h3>El fallo que esto vigila</h3>
 *
 * Con la política cerrada y sin fijar el negocio, la lectura de overrides no da
 * error: <b>devuelve cero filas</b>. El login responde 200 y el JWT sale con los
 * módulos del plan a secas. Un negocio con módulos regalados los pierde en cada
 * login y nada lo reporta.
 *
 * <p>Por eso todo aquí se prueba con un negocio que <b>tiene</b> overrides, y en
 * los dos sentidos: uno regalado (que el plan no da) y uno revocado (que el plan
 * sí da). Con un negocio limpio, "los overrides se aplican" y "los overrides se
 * perdieron" darían exactamente la misma lista de módulos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("cloud")
@Testcontainers
class ModulosConLaPoliticaCerradaTest {

    static final String SECRET = "clave-de-prueba-multitenant-min-32-bytes!!";
    static final String TENANT = "negocio-con-overrides";
    static final String EMAIL = "duena@negocio-con-overrides.invalid";
    static final String CLAVE = "claveDePrueba123";

    /** Módulo que el plan `basico` NO incluye: sirve para probar el regalo. */
    static final String REGALADO = "descuentos";
    /** Módulo que el plan `basico` SÍ incluye: sirve para probar la revocación. */
    static final String REVOCADO = "ventas";

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", () -> "app_user");
        r.add("spring.datasource.password", () -> "app_pw");
        r.add("spring.flyway.url", PG::getJdbcUrl);
        r.add("spring.flyway.user", PG::getUsername);
        r.add("spring.flyway.password", PG::getPassword);
        r.add("security.jwt.secret", () -> SECRET);
        r.add("auth.reset.link-base", () -> "https://pos-de-prueba.invalid");
    }

    @Autowired AuthService auth;

    /**
     * Conexión como dueño (BYPASSRLS). Igual que en
     * {@code ResetPasswordTransaccionalTest}: sembrar y —sobre todo— leer el
     * resultado sin que RLS enmascare la diferencia entre "no se escribió" y
     * "se escribió y no lo veo".
     */
    private JdbcTemplate dueno;

    @BeforeEach
    void sembrar() {
        dueno = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        dueno.update("DELETE FROM tenant_modules WHERE tenant_id = ?", TENANT);
        dueno.update("DELETE FROM users WHERE email = ?", EMAIL);
        dueno.update("DELETE FROM tenants WHERE id = ?", TENANT);
        dueno.update("INSERT INTO tenants (id, name, plan) VALUES (?, ?, 'basico')",
                TENANT, "Negocio Con Overrides");
        dueno.update("INSERT INTO users (email, password_hash, tenant_id, role) "
                        + "VALUES (?, ?, ?, 'admin')",
                EMAIL, new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                        .encode(CLAVE), TENANT);
        dueno.update("INSERT INTO tenant_modules (tenant_id, module, enabled) VALUES (?, ?, true)",
                TENANT, REGALADO);
        dueno.update("INSERT INTO tenant_modules (tenant_id, module, enabled) VALUES (?, ?, false)",
                TENANT, REVOCADO);
    }

    private int overridesEnLaBase() {
        return dueno.queryForObject(
                "SELECT count(*) FROM tenant_modules WHERE tenant_id = ?", Integer.class, TENANT);
    }

    @Test
    @DisplayName("🔴 el login de un negocio CON overrides los sigue aplicando")
    void elLoginConservaLosOverrides() {
        // Punto de partida: los dos overrides están en la base.
        assertEquals(2, overridesEnLaBase());

        AuthService.AuthResponse res = auth.login(EMAIL, CLAVE);
        List<String> modulos = res.modules();

        // El regalado, que el plan `basico` NO da. Si los overrides se hubieran
        // perdido, esta línea falla: es la que distingue "funciona" de "responde
        // 200 con los módulos del plan a secas".
        assertTrue(modulos.contains(REGALADO),
                "se perdió el módulo REGALADO (" + REGALADO + "): el login leyó "
                        + "cero overrides. Módulos devueltos: " + modulos);

        // Y el revocado, que el plan SÍ da. Sin este, un login que devolviera
        // "todos los del plan" pasaría el test anterior por accidente si el
        // módulo regalado también estuviera en el plan.
        assertFalse(modulos.contains(REVOCADO),
                "el módulo REVOCADO (" + REVOCADO + ") volvió a aparecer: el login "
                        + "leyó cero overrides. Módulos devueltos: " + modulos);
    }

    @Test
    @DisplayName("🔴 setModuleOverrides ESCRIBE de verdad sin negocio en sesión")
    void elEndpointDelKamEscribeDeVerdad() {
        // Este es el camino de /admin/tenants/{id}/modules: ruta exenta del
        // filtro de negocio, así que la conexión sale con app.tenant_id = ''.
        // Sin el set_config que se añadió junto a V40, el UPSERT afectaría a cero
        // filas y el método devolvería 200 igual.
        Map<String, Boolean> cambios = new HashMap<>();
        cambios.put("cocina", true);

        AuthService.ModuleConfig cfg = auth.setModuleOverrides(TENANT, cambios);

        // La aserción que importa NO es lo que devuelve el método —lo calcula él
        // mismo y podría estar de acuerdo consigo mismo estando roto— sino lo que
        // quedó EN LA BASE, leído por fuera con el rol dueño.
        assertEquals(1, (int) dueno.queryForObject(
                        "SELECT count(*) FROM tenant_modules "
                                + "WHERE tenant_id = ? AND module = 'cocina' AND enabled",
                        Integer.class, TENANT),
                "el override no llegó a la base: el UPSERT escribió cero filas");
        assertEquals(3, overridesEnLaBase());
        assertTrue(cfg.effectiveModules().contains("cocina"));
    }

    @Test
    @DisplayName("borrar un override tampoco es un no-op silencioso")
    void borrarUnOverrideFuncionaSinNegocioEnSesion() {
        Map<String, Boolean> cambios = new HashMap<>();
        cambios.put(REGALADO, null);   // null = borrar el override

        auth.setModuleOverrides(TENANT, cambios);

        assertEquals(0, (int) dueno.queryForObject(
                        "SELECT count(*) FROM tenant_modules WHERE tenant_id = ? AND module = ?",
                        Integer.class, TENANT, REGALADO),
                "el DELETE borró cero filas y nadie se enteró");
        // Y el módulo vuelve a decidirse por el plan, que en `basico` no lo da.
        assertFalse(auth.login(EMAIL, CLAVE).modules().contains(REGALADO));
    }

    @Test
    @DisplayName("getModuleConfig lee los overrides sin negocio en sesión")
    void getModuleConfigLeeLosOverrides() {
        AuthService.ModuleConfig cfg = auth.getModuleConfig(TENANT);

        assertEquals(2, cfg.overrides().size(),
                "getModuleConfig leyó " + cfg.overrides().size() + " overrides de 2: "
                        + "el panel del KAM mostraría una configuración que no es la real");
        assertEquals(Boolean.TRUE, cfg.overrides().get(REGALADO));
        assertEquals(Boolean.FALSE, cfg.overrides().get(REVOCADO));
    }
}
