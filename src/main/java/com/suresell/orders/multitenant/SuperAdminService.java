package com.suresell.orders.multitenant;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Operación del super-admin (KAM) GLOBAL: login propio y administración cross-tenant
 * (listar negocios, cambiar plan, ajustar módulos, ver usuarios). Reusa
 * {@link AuthService} para la config de módulos/usuarios por tenant. F3, Inc.3.
 * SOLO perfil `cloud`. Emite un JWT con claim `super_admin=true` (sin tenant).
 */
@Service
@Profile("cloud")
public class SuperAdminService {

    private final SuperAdminRepository saRepo;
    private final AuthService authService;
    // N4 — el catálogo de planes vive en BD (V27) y lo edita el KAM.
    private final PlanRepository planRepo;
    private final PlanCatalogService planes;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final SecretKey key;
    private final long ttlSeconds;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public SuperAdminService(
            SuperAdminRepository saRepo,
            AuthService authService,
            PlanRepository planRepo,
            PlanCatalogService planes,
            org.springframework.jdbc.core.JdbcTemplate jdbc,
            @Value("${security.jwt.secret:cambia-esta-clave-en-produccion-min-32-bytes!}") String secret,
            @Value("${auth.token.ttl-seconds:43200}") long ttlSeconds) {
        this.saRepo = saRepo;
        this.authService = authService;
        this.planRepo = planRepo;
        this.planes = planes;
        this.jdbc = jdbc;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = ttlSeconds;
    }

    public record LoginResponse(String token, String email) {}

    public record TenantDetail(String tenantId, String name, String status,
                               AuthService.ModuleConfig modules,
                               List<AuthRepository.UserSummary> users) {}

    public LoginResponse login(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new AuthException(400, "Email y contraseña son requeridos");
        }
        AuthException invalid = new AuthException(401, "Credenciales inválidas");
        var sa = saRepo.findByEmail(email.trim()).orElseThrow(() -> invalid);
        if (!encoder.matches(password, sa.passwordHash())) {
            throw invalid;
        }
        return new LoginResponse(issueToken(sa.email()), sa.email());
    }

    public List<SuperAdminRepository.TenantListItem> listTenants() {
        return saRepo.listTenants();
    }

    public TenantDetail getTenant(String tenantId) {
        AuthService.ModuleConfig cfg = authService.getModuleConfig(tenantId); // 404 si no existe
        List<AuthRepository.UserSummary> users = authService.listUsers(tenantId);
        // El nombre/status salen de la lista de negocios (evita otra consulta).
        var item = saRepo.listTenants().stream().filter(t -> t.id().equals(tenantId)).findFirst();
        return new TenantDetail(tenantId,
                item.map(SuperAdminRepository.TenantListItem::name).orElse(tenantId),
                item.map(SuperAdminRepository.TenantListItem::status).orElse("active"),
                cfg, users);
    }

    public void setPlan(String tenantId, String plan) {
        String p = plan == null ? "" : plan.trim().toLowerCase();
        // Se valida contra el catálogo REAL: con un Set quemado, un plan creado
        // desde el KAM era inasignable.
        List<String> validos = planes.catalogo().stream()
                .filter(PlanRepository.Plan::active)
                .map(PlanRepository.Plan::id)
                .toList();
        if (!validos.contains(p)) {
            throw new AuthException(400, "Plan inválido (válidos: " + String.join("|", validos) + ")");
        }
        if (saRepo.updateTenantPlan(tenantId, p) == 0) {
            throw new AuthException(404, "Negocio no encontrado");
        }
    }

    public AuthService.ModuleConfig setModules(String tenantId, Map<String, Boolean> overrides) {
        return authService.setModuleOverrides(tenantId, overrides);
    }

    // ------------------------------------------------------------------
    // N4 — Catálogo: módulos conocidos y planes.
    // ------------------------------------------------------------------

    /** Un módulo, con etiqueta legible y dónde aplica. */
    public record ModuleInfo(String id, String label, String scope) {}

    public record Catalog(List<ModuleInfo> modules, List<PlanRepository.Plan> plans) {}

    /**
     * Lo que el KAM necesita para pintarse. El panel tenía la lista de módulos
     * QUEMADA con 4 de los 16 que conoce el backend, así que los demás no se
     * podían tocar y cada módulo nuevo había que acordarse de copiarlo.
     */
    public Catalog catalog() {
        List<ModuleInfo> mods = new java.util.ArrayList<>();
        for (String m : ORDEN_MODULOS) {
            mods.add(new ModuleInfo(m, ETIQUETAS.getOrDefault(m, m),
                    MODULOS_POS.contains(m) ? "pos" : "panel"));
        }
        // Cualquier módulo que exista en el backend y no esté en el orden de
        // arriba igual se muestra: mejor desordenado que invisible.
        for (String m : PlanCatalog.KNOWN) {
            if (!ORDEN_MODULOS.contains(m)) {
                mods.add(new ModuleInfo(m, m, "otro"));
            }
        }
        return new Catalog(mods, planes.catalogo());
    }

    private static final Set<String> MODULOS_POS = Set.of(
            PlanCatalog.VENTAS, PlanCatalog.HISTORIAL, PlanCatalog.CIERRE,
            PlanCatalog.DESCUENTOS, PlanCatalog.COCINA, PlanCatalog.MESEROS);

    private static final List<String> ORDEN_MODULOS = List.of(
            PlanCatalog.VENTAS, PlanCatalog.HISTORIAL, PlanCatalog.CIERRE,
            PlanCatalog.DESCUENTOS, PlanCatalog.COCINA, PlanCatalog.MESEROS,
            PlanCatalog.PANEL, PlanCatalog.ANALITICA, PlanCatalog.MENU_ADMIN,
            PlanCatalog.GASTOS, PlanCatalog.NOMINA, PlanCatalog.EMPLEADOS,
            PlanCatalog.VALERAS, PlanCatalog.INSUMOS, PlanCatalog.COMPRAS,
            PlanCatalog.CARTERA);

    private static final Map<String, String> ETIQUETAS = Map.ofEntries(
            Map.entry(PlanCatalog.VENTAS, "Ventas (POS)"),
            Map.entry(PlanCatalog.HISTORIAL, "Historial de órdenes"),
            Map.entry(PlanCatalog.CIERRE, "Cierre de caja"),
            Map.entry(PlanCatalog.DESCUENTOS, "Descuentos y cupones"),
            Map.entry(PlanCatalog.COCINA, "App de cocina"),
            Map.entry(PlanCatalog.MESEROS, "App de meseros"),
            Map.entry(PlanCatalog.PANEL, "Panel de administración"),
            Map.entry(PlanCatalog.ANALITICA, "Analítica"),
            Map.entry(PlanCatalog.MENU_ADMIN, "Menú y productos"),
            Map.entry(PlanCatalog.GASTOS, "Gastos"),
            Map.entry(PlanCatalog.NOMINA, "Nómina"),
            Map.entry(PlanCatalog.EMPLEADOS, "Empleados"),
            Map.entry(PlanCatalog.VALERAS, "Valeras"),
            Map.entry(PlanCatalog.INSUMOS, "Insumos"),
            Map.entry(PlanCatalog.COMPRAS, "Compras"),
            Map.entry(PlanCatalog.CARTERA, "Cartera"));

    /** Crea un plan. El id es el slug con el que se guarda en `tenants.plan`. */
    public PlanRepository.Plan createPlan(String id, String name, String description,
                                          List<String> modules) {
        String slug = id == null ? "" : id.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "");
        if (slug.isBlank()) {
            throw new AuthException(400, "El id del plan es obligatorio (letras, números, - y _)");
        }
        if (planRepo.exists(slug)) {
            throw new AuthException(409, "Ya existe un plan con id '" + slug + "'");
        }
        String nombre = name == null || name.isBlank() ? slug : name.trim();
        planRepo.insert(slug, nombre, description == null ? null : description.trim());
        planRepo.replaceModules(slug, validarModulos(modules));
        planes.invalidar();
        return buscarPlan(slug);
    }

    /** Edita nombre, descripción, estado y módulos de un plan. */
    public PlanRepository.Plan updatePlan(String id, String name, String description,
                                          Boolean active, List<String> modules) {
        if (!planRepo.exists(id)) {
            throw new AuthException(404, "Plan no encontrado: " + id);
        }
        boolean activo = active == null || active;
        if (!activo && planRepo.countTenants(id) > 0) {
            // Desactivar solo lo saca del selector; los negocios que ya lo tienen
            // lo conservan. Avisar es mejor que sorprender.
            throw new AuthException(409, "No se desactiva: hay " + planRepo.countTenants(id)
                    + " negocio(s) en este plan. Muévelos primero.");
        }
        PlanRepository.Plan actual = buscarPlan(id);
        planRepo.update(id,
                name == null || name.isBlank() ? actual.name() : name.trim(),
                description == null ? actual.description() : description.trim(),
                activo);
        if (modules != null) {
            planRepo.replaceModules(id, validarModulos(modules));
        }
        planes.invalidar();
        return buscarPlan(id);
    }

    private List<String> validarModulos(List<String> modules) {
        if (modules == null) {
            return List.of();
        }
        List<String> desconocidos = modules.stream()
                .filter(m -> !PlanCatalog.isKnownModule(m))
                .toList();
        if (!desconocidos.isEmpty()) {
            throw new AuthException(400, "Módulos desconocidos: " + String.join(", ", desconocidos));
        }
        return modules.stream().map(m -> m.trim().toLowerCase()).distinct().toList();
    }

    private PlanRepository.Plan buscarPlan(String id) {
        return planRepo.findAll().stream()
                .filter(p -> p.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AuthException(404, "Plan no encontrado: " + id));
    }

    /**
     * Modo de POS de las sedes de un negocio (Inc. 1 del modo Restaurante).
     *
     * Va por JdbcTemplate con `set_config` explícito porque `sites` tiene RLS
     * FORCE y el KAM es cross-tenant: sin fijar el tenant en la sesión no vería
     * ninguna fila. El `true` del tercer parámetro lo acota a la transacción.
     */
    public java.util.List<Map<String, Object>> getSites(String tenantId) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId);
        return jdbc.queryForList(
                "SELECT id, name, code, pos_mode, active, is_default FROM sites ORDER BY id");
    }

    /** Cambia el modo de una sede. Es potestad EXCLUSIVA del KAM: se vende, no se elige. */
    @org.springframework.transaction.annotation.Transactional
    public java.util.List<Map<String, Object>> setSiteMode(String tenantId, Long siteId, String mode) {
        String normalizado = mode == null ? "" : mode.trim().toUpperCase();
        if (!"PLAZOLETA".equals(normalizado) && !"RESTAURANTE".equals(normalizado)) {
            throw new AuthException(400, "Modo inválido. Use PLAZOLETA o RESTAURANTE");
        }
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId);
        int filas = jdbc.update("UPDATE sites SET pos_mode = ? WHERE id = ?", normalizado, siteId);
        if (filas == 0) {
            throw new AuthException(404, "No existe la sede " + siteId + " en el negocio " + tenantId);
        }
        return jdbc.queryForList(
                "SELECT id, name, code, pos_mode, active, is_default FROM sites ORDER BY id");
    }

    private String issueToken(String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim("super_admin", true)
                .subject(email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }
}
