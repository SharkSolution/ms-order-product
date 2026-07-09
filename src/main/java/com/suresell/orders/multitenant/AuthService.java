package com.suresell.orders.multitenant;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;

/**
 * Auth real (F1): login por email+contraseña (deriva el tenant del usuario) y
 * alta self-service de un negocio. Emite el JWT de tenant que valida
 * {@link JwtTenantResolver}. SOLO perfil `cloud`. Ver docs/110-plan-auth-real.md.
 */
@Service
@Profile("cloud")
public class AuthService {

    private static final String DEFAULT_PLAN = "pro";
    private static final String ADMIN_ROLE = "admin";

    private final AuthRepository repo;
    private final SecretKey key;
    private final long ttlSeconds;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(
            AuthRepository repo,
            @Value("${security.jwt.secret:cambia-esta-clave-en-produccion-min-32-bytes!}") String secret,
            @Value("${auth.token.ttl-seconds:43200}") long ttlSeconds) {
        this.repo = repo;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = ttlSeconds;
    }

    public record AuthResponse(String token, String tenantId, String tenantName,
                               String plan, String userName, String role) {}

    /** Login por credenciales de usuario; el tenant sale de su cuenta. */
    public AuthResponse login(String email, String password) {
        if (isBlank(email) || isBlank(password)) {
            throw new AuthException(400, "Email y contraseña son requeridos");
        }
        // Mensaje genérico e idéntico para "no existe" y "clave mala": no revela
        // qué emails están registrados.
        AuthException invalid = new AuthException(401, "Credenciales inválidas");
        var user = repo.findUserByEmail(email.trim()).orElseThrow(() -> invalid);
        if (!encoder.matches(password, user.passwordHash())) {
            throw invalid;
        }
        if (!"active".equals(user.status())) {
            throw new AuthException(403, "Usuario deshabilitado");
        }
        var tenant = repo.findTenant(user.tenantId())
                .orElseThrow(() -> new AuthException(403, "El negocio no está disponible"));
        if (!"active".equals(tenant.status())) {
            throw new AuthException(403, "El negocio está suspendido");
        }
        String token = issueToken(tenant.id(), user.email(), user.role());
        return new AuthResponse(token, tenant.id(), tenant.name(), tenant.plan(),
                user.email(), user.role());
    }

    /** Alta self-service: crea negocio + usuario admin y devuelve sesión iniciada. */
    @Transactional
    public AuthResponse register(String businessName, String email, String password) {
        if (isBlank(businessName) || isBlank(email) || isBlank(password)) {
            throw new AuthException(400, "Negocio, email y contraseña son requeridos");
        }
        if (password.trim().length() < 6) {
            throw new AuthException(400, "La contraseña debe tener al menos 6 caracteres");
        }
        String cleanEmail = email.trim();
        if (repo.emailExists(cleanEmail)) {
            throw new AuthException(409, "Ese email ya está registrado");
        }
        String tenantId = uniqueSlug(businessName);
        repo.insertTenant(tenantId, businessName.trim(), DEFAULT_PLAN);
        repo.insertUser(cleanEmail, encoder.encode(password), tenantId, ADMIN_ROLE);

        String token = issueToken(tenantId, cleanEmail, ADMIN_ROLE);
        return new AuthResponse(token, tenantId, businessName.trim(), DEFAULT_PLAN,
                cleanEmail, ADMIN_ROLE);
    }

    private String issueToken(String tenantId, String subject, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim("tenant_id", tenantId)
                .claim("role", role)
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    /** Deriva un slug único desde el nombre del negocio (colisión → sufijo -2, -3…). */
    private String uniqueSlug(String businessName) {
        String base = businessName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (base.isBlank()) {
            base = "negocio";
        }
        if (!repo.tenantExists(base)) {
            return base;
        }
        for (int i = 2; i < 10_000; i++) {
            String candidate = base + "-" + i;
            if (!repo.tenantExists(candidate)) {
                return candidate;
            }
        }
        throw new AuthException(409, "No se pudo generar un identificador para el negocio");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
