package com.suresell.orders.multitenant;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de un negocio completa, en un solo paso.
 *
 * <p><b>Por qué existe.</b> Dar de alta un cliente eran cinco pasos repartidos
 * en dos sesiones distintas —el token del KAM y el del administrador del propio
 * negocio—: registrar el negocio, crear la sede, ponerle el modo, crear las
 * mesas y ajustar el plan. Media docena de oportunidades de equivocarse, y el
 * paso más propenso a error de toda la operación.
 *
 * <p><b>Todo o nada.</b> Corre dentro de una transacción: si algo falla, no
 * queda un negocio a medio crear. Un negocio sin sede o sin usuario admin es
 * peor que ninguno — no se puede entrar a arreglarlo desde la aplicación.
 *
 * <p>Escribe directo con JDBC en vez de reusar los repositorios de cada área
 * porque el alta ocurre <b>antes</b> de que exista el tenant, así que no hay
 * contexto de negocio: las políticas RLS y los repositorios acotados por tenant
 * todavía no aplican.
 */
@Service
public class AltaDeNegocioService {

    /** Lo que el KAM manda para dar de alta. */
    public record Solicitud(
            String nombreNegocio,
            String emailAdmin,
            String clave,
            String plan,
            String modo,
            Integer cantidadMesas,
            String nit,
            String direccion,
            String telefono) {}

    /** Lo que se creó, para mostrárselo al KAM. */
    public record Resultado(
            String tenantId,
            String nombreNegocio,
            String emailAdmin,
            String plan,
            String modo,
            long siteId,
            int mesasCreadas,
            List<String> modulos) {}

    /** El alta falló por un dato del formulario, no por un fallo del sistema. */
    public static class AltaInvalidaException extends RuntimeException {
        private final int codigo;

        public AltaInvalidaException(int codigo, String mensaje) {
            super(mensaje);
            this.codigo = codigo;
        }

        public int codigo() {
            return codigo;
        }
    }

    private static final String ROL_ADMIN = "admin";
    private static final String MODO_PLAZOLETA = "PLAZOLETA";
    private static final String MODO_RESTAURANTE = "RESTAURANTE";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final PlanCatalogService planes;

    // Se crea acá y no se inyecta: el proyecto no publica un bean de
    // PasswordEncoder (AuthService también instancia el suyo). Inyectarlo hacía
    // fallar el arranque entero por una dependencia que nadie provee.
    //
    // El @Autowired es imprescindible teniendo dos constructores: sin él Spring
    // no sabe cuál usar, busca el vacío y el contexto no levanta.
    @org.springframework.beans.factory.annotation.Autowired
    public AltaDeNegocioService(JdbcTemplate jdbc, PlanCatalogService planes) {
        this(jdbc, new BCryptPasswordEncoder(), planes);
    }

    /** Para tests: permite inyectar el codificador. */
    AltaDeNegocioService(JdbcTemplate jdbc, PasswordEncoder encoder, PlanCatalogService planes) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.planes = planes;
    }

    @Transactional
    public Resultado darDeAlta(Solicitud s) {
        String nombre = limpiar(s.nombreNegocio());
        String email = s.emailAdmin() == null ? "" : s.emailAdmin().trim().toLowerCase(Locale.ROOT);
        String clave = s.clave() == null ? "" : s.clave();

        if (nombre.isBlank() || email.isBlank() || clave.isBlank()) {
            throw new AltaInvalidaException(400, "El nombre del negocio, el email y la clave son obligatorios");
        }
        if (!email.contains("@")) {
            throw new AltaInvalidaException(400, "El email no parece válido");
        }
        if (clave.length() < 6) {
            throw new AltaInvalidaException(400, "La clave debe tener al menos 6 caracteres");
        }

        Integer yaExiste = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE lower(email) = ?", Integer.class, email);
        if (yaExiste != null && yaExiste > 0) {
            throw new AltaInvalidaException(409, "Ese email ya está registrado en otro negocio");
        }

        String modo = normalizarModo(s.modo());
        String plan = planValido(s.plan());

        // Un restaurante SIN mesas no puede vender: el POS en modo Restaurante
        // muestra el plano de mesas y estaria vacio.
        int mesas = 0;
        if (MODO_RESTAURANTE.equals(modo)) {
            mesas = s.cantidadMesas() == null ? 0 : s.cantidadMesas();
            if (mesas < 1) {
                throw new AltaInvalidaException(400,
                        "Un negocio en modo Restaurante necesita al menos una mesa");
            }
            if (mesas > 500) {
                throw new AltaInvalidaException(400, "El máximo es 500 mesas");
            }
        }

        String tenantId = slugUnico(nombre);

        jdbc.update("INSERT INTO tenants (id, name, plan, nit, address, phone) VALUES (?, ?, ?, ?, ?, ?)",
                tenantId, nombre, plan, limpiarONulo(s.nit()),
                limpiarONulo(s.direccion()), limpiarONulo(s.telefono()));

        jdbc.update("INSERT INTO users (email, password_hash, tenant_id, role) VALUES (?, ?, ?, ?)",
                email, encoder.encode(clave), tenantId, ROL_ADMIN);

        // `sites`, `restaurant_tables` y `tenant_order_counters` tienen RLS en
        // modo FORCE: aplica hasta al dueño de la tabla. El KAM es cross-tenant
        // y no trae negocio en contexto, así que sin fijarlo acá los INSERT de
        // abajo NO insertarían nada —y sin error—. El `true` del tercer
        // parámetro lo acota a esta transacción.
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId);

        // La sede se crea explicitamente y no se deja al disparador de V28: asi
        // queda con el modo elegido desde el minuto cero. Si se dejara al
        // disparador nacería en PLAZOLETA y un restaurante arrancaria sin mesas.
        Long siteId = jdbc.queryForObject(
                "INSERT INTO sites (tenant_id, name, code, pos_mode, is_default) "
                        + "VALUES (?, 'Principal', 'PRINCIPAL', ?, true) RETURNING id",
                Long.class, tenantId, modo);

        // El contador arranca en 0: la primera venta sera el folio 1.
        jdbc.update("INSERT INTO tenant_order_counters (tenant_id, site_id, last_id) VALUES (?, ?, 0) "
                + "ON CONFLICT (tenant_id, site_id) DO NOTHING", tenantId, siteId);

        for (int n = 1; n <= mesas; n++) {
            jdbc.update("INSERT INTO restaurant_tables (tenant_id, site_id, number, active) "
                    + "VALUES (?, ?, ?, true)", tenantId, siteId, n);
        }

        List<String> modulos = new ArrayList<>(planes.modulesForPlan(plan));

        return new Resultado(tenantId, nombre, email, plan, modo, siteId, mesas, modulos);
    }

    private String normalizarModo(String modo) {
        String m = modo == null ? MODO_PLAZOLETA : modo.trim().toUpperCase(Locale.ROOT);
        if (!MODO_PLAZOLETA.equals(m) && !MODO_RESTAURANTE.equals(m)) {
            throw new AltaInvalidaException(400, "El modo debe ser PLAZOLETA o RESTAURANTE");
        }
        return m;
    }

    private String planValido(String plan) {
        String p = plan == null || plan.isBlank() ? "pro" : plan.trim().toLowerCase(Locale.ROOT);
        boolean existe = planes.catalogo().stream().anyMatch(x -> x.id().equalsIgnoreCase(p));
        if (!existe) {
            throw new AltaInvalidaException(400, "El plan '" + p + "' no existe");
        }
        return p;
    }

    /**
     * Identificador legible derivado del nombre, sin chocar con otro negocio.
     *
     * <p>Los acentos y la eñe se convierten a su letra base ANTES de limpiar.
     * Sin eso, "Pizzería" quedaba como {@code pizzer-a} y "Antioqueña" como
     * {@code antioque-a}: el carácter acentuado no entraba en {@code [a-z0-9]} y
     * se volvía un guion. En español eso es la norma, no la excepción.
     */
    static String slugDe(String nombre) {
        String sinAcentos = java.text.Normalizer
                .normalize(nombre, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return sinAcentos.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
    }

    private String slugUnico(String nombre) {
        String base = slugDe(nombre);
        if (base.isBlank()) {
            base = "negocio";
        }
        if (!existeTenant(base)) {
            return base;
        }
        for (int i = 2; i < 10_000; i++) {
            String candidato = base + "-" + i;
            if (!existeTenant(candidato)) {
                return candidato;
            }
        }
        throw new AltaInvalidaException(409, "No se pudo generar un identificador libre para ese nombre");
    }

    private boolean existeTenant(String id) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM tenants WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    private static String limpiar(String s) {
        return s == null ? "" : s.trim();
    }

    private static String limpiarONulo(String s) {
        String t = limpiar(s);
        return t.isEmpty() ? null : t;
    }
}
