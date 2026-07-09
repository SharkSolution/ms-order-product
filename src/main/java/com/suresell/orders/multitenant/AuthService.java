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
    private final String registerKey;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(
            AuthRepository repo,
            @Value("${security.jwt.secret:cambia-esta-clave-en-produccion-min-32-bytes!}") String secret,
            @Value("${auth.token.ttl-seconds:43200}") long ttlSeconds,
            @Value("${auth.register.key:}") String registerKey) {
        this.repo = repo;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = ttlSeconds;
        this.registerKey = registerKey;
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

    /**
     * Alta de un negocio (crea tenant + usuario admin y devuelve sesión iniciada).
     * NO es abierto al público: exige la clave de registro que solo conoce el KAM
     * (env AUTH_REGISTER_KEY). Fail-closed: si no hay clave configurada, el registro
     * queda deshabilitado. Ver docs/110 §8 / docs/120 §4.2.
     */
    @Transactional
    public AuthResponse register(String businessName, String email, String password,
                                 String providedRegisterKey) {
        if (isBlank(registerKey)) {
            throw new AuthException(403, "El registro no está habilitado");
        }
        if (isBlank(providedRegisterKey) || !registerKey.equals(providedRegisterKey)) {
            throw new AuthException(403, "Clave de registro inválida");
        }
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

    /**
     * Cambia la contraseña del usuario autenticado (email+tenant del JWT), tras
     * verificar la actual. No emite token nuevo (el actual sigue vigente). Ver docs/110 §8.
     */
    public void changePassword(String email, String tenantId, String currentPassword, String newPassword) {
        if (isBlank(currentPassword) || isBlank(newPassword)) {
            throw new AuthException(400, "La contraseña actual y la nueva son requeridas");
        }
        if (newPassword.trim().length() < 6) {
            throw new AuthException(400, "La nueva contraseña debe tener al menos 6 caracteres");
        }
        var user = repo.findUserByEmail(email)
                .orElseThrow(() -> new AuthException(401, "Sesión inválida"));
        if (!user.tenantId().equals(tenantId)) {
            // El email del token no pertenece al tenant del token: token manipulado.
            throw new AuthException(401, "Sesión inválida");
        }
        if (!encoder.matches(currentPassword, user.passwordHash())) {
            throw new AuthException(401, "La contraseña actual no es correcta");
        }
        repo.updatePasswordHash(email, tenantId, encoder.encode(newPassword));
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
