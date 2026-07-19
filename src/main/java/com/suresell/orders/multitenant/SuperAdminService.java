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

    private static final Set<String> VALID_PLANS = Set.of("basico", "pro");

    private final SuperAdminRepository saRepo;
    private final AuthService authService;
    private final SecretKey key;
    private final long ttlSeconds;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public SuperAdminService(
            SuperAdminRepository saRepo,
            AuthService authService,
            @Value("${security.jwt.secret:cambia-esta-clave-en-produccion-min-32-bytes!}") String secret,
            @Value("${auth.token.ttl-seconds:43200}") long ttlSeconds) {
        this.saRepo = saRepo;
        this.authService = authService;
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
        if (!VALID_PLANS.contains(p)) {
            throw new AuthException(400, "Plan inválido (basico|pro)");
        }
        if (saRepo.updateTenantPlan(tenantId, p) == 0) {
            throw new AuthException(404, "Negocio no encontrado");
        }
    }

    public AuthService.ModuleConfig setModules(String tenantId, Map<String, Boolean> overrides) {
        return authService.setModuleOverrides(tenantId, overrides);
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
