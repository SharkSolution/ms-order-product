package com.suresell.orders.multitenant;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a las tablas GLOBALES de auth (`tenants`, `users`) vía JdbcTemplate.
 *
 * Deliberadamente NO usa JPA/entidades: estas tablas no llevan tenant_id ni la
 * política RLS por tenant (ver V4__auth.sql y docs/110 §3). El login ocurre sin
 * tenant en contexto, así que se consultan por email/slug directo. SOLO perfil `cloud`.
 */
@Repository
@Profile("cloud")
public class AuthRepository {

    private final JdbcTemplate jdbc;

    public AuthRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record UserRow(long id, String email, String passwordHash, String tenantId,
                          String role, String status) {}

    public record TenantRow(String id, String name, String plan, String status,
                            String nit, String address, String phone, String ticketFooter) {}

    /** Proyección de usuario SIN el hash (para listar en el panel de usuarios). */
    public record UserSummary(long id, String email, String role, String status) {}

    /** Override de módulo por tenant: enabled=true regala, false quita. */
    public record ModuleOverride(String module, boolean enabled) {}

    /** Busca el usuario por email (case-insensitive). */
    public Optional<UserRow> findUserByEmail(String email) {
        try {
            UserRow row = jdbc.queryForObject(
                    "SELECT id, email, password_hash, tenant_id, role, status "
                            + "FROM users WHERE lower(email) = lower(?)",
                    (rs, i) -> new UserRow(
                            rs.getLong("id"), rs.getString("email"), rs.getString("password_hash"),
                            rs.getString("tenant_id"), rs.getString("role"), rs.getString("status")),
                    email);
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<TenantRow> findTenant(String id) {
        try {
            TenantRow row = jdbc.queryForObject(
                    "SELECT id, name, plan, status, nit, address, phone, ticket_footer "
                            + "FROM tenants WHERE id = ?",
                    (rs, i) -> new TenantRow(rs.getString("id"), rs.getString("name"),
                            rs.getString("plan"), rs.getString("status"),
                            rs.getString("nit"), rs.getString("address"),
                            rs.getString("phone"), rs.getString("ticket_footer")),
                    id);
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean emailExists(String email) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE lower(email) = lower(?)", Integer.class, email);
        return n != null && n > 0;
    }

    public boolean tenantExists(String id) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM tenants WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    public void insertTenant(String id, String name, String plan,
                             String nit, String address, String phone) {
        jdbc.update("INSERT INTO tenants (id, name, plan, nit, address, phone) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, name, plan, nit, address, phone);
    }

    /** Actualiza el perfil del negocio (datos del ticket). Acotado por id de tenant. */
    public void updateBusinessProfile(String tenantId, String name, String nit,
                                      String address, String phone, String ticketFooter) {
        jdbc.update("UPDATE tenants SET name = ?, nit = ?, address = ?, phone = ?, "
                        + "ticket_footer = ? WHERE id = ?",
                name, nit, address, phone, ticketFooter, tenantId);
    }

    public void insertUser(String email, String passwordHash, String tenantId, String role) {
        jdbc.update("INSERT INTO users (email, password_hash, tenant_id, role) VALUES (?, ?, ?, ?)",
                email, passwordHash, tenantId, role);
    }

    /** Usuarios de un tenant (sin hash), para el panel de gestión (F3, admin). */
    public List<UserSummary> listUsers(String tenantId) {
        return jdbc.query(
                "SELECT id, email, role, status FROM users WHERE tenant_id = ? ORDER BY created_at",
                (rs, i) -> new UserSummary(rs.getLong("id"), rs.getString("email"),
                        rs.getString("role"), rs.getString("status")),
                tenantId);
    }

    /** Overrides de módulos del tenant (F3, Inc.2). */
    public List<ModuleOverride> getOverrides(String tenantId) {
        return jdbc.query(
                "SELECT module, enabled FROM tenant_modules WHERE tenant_id = ?",
                (rs, i) -> new ModuleOverride(rs.getString("module"), rs.getBoolean("enabled")),
                tenantId);
    }

    /** Fija (o actualiza) un override de módulo para el tenant. */
    public void upsertOverride(String tenantId, String module, boolean enabled) {
        jdbc.update(
                "INSERT INTO tenant_modules (tenant_id, module, enabled) VALUES (?, ?, ?) "
                        + "ON CONFLICT (tenant_id, module) DO UPDATE SET enabled = EXCLUDED.enabled",
                tenantId, module, enabled);
    }

    /** Quita un override (el módulo vuelve a decidirse solo por el plan). */
    public void deleteOverride(String tenantId, String module) {
        jdbc.update("DELETE FROM tenant_modules WHERE tenant_id = ? AND module = ?", tenantId, module);
    }

    // ---------- Reset de contraseña (F3, Inc.5) ----------

    public record ResetRow(String email, String tenantId) {}

    public void insertReset(String tokenHash, String email, String tenantId, java.time.Instant expiresAt) {
        jdbc.update(
                "INSERT INTO password_resets (token_hash, email, tenant_id, expires_at) VALUES (?, ?, ?, ?)",
                tokenHash, email, tenantId, java.sql.Timestamp.from(expiresAt));
    }

    /**
     * Resultado de consultar un token de recuperación.
     *
     * <p>{@code email} y {@code tenantId} vienen <b>en nulo salvo que el estado
     * sea {@code valido}</b>. No es una precaución decorativa: son los dos
     * únicos datos personales de la fila, y para decidir que un token está
     * vencido no hacen ninguna falta.
     */
    public record ConsultaDeReset(EstadoDelToken estado, String email, String tenantId) {}

    /**
     * Busca un token de recuperación y dice <b>por qué</b> no sirve, si no sirve.
     *
     * <p>Sustituye a {@code findValidReset}, que devolvía un {@code Optional}
     * vacío para cuatro situaciones distintas —no existe, caducado, ya usado— y
     * hacía imposible diagnosticar un reporte de "el enlace no funciona".
     *
     * <p><b>Precedencia deliberada:</b> un token usado Y caducado se reporta como
     * {@code usado}. Es lo que primero hay que saber: significa que el flujo sí
     * llegó al final alguna vez, y eso cambia por dónde se busca el problema.
     */
    public ConsultaDeReset buscarReset(String tokenHash) {
        try {
            ConsultaDeReset r = jdbc.queryForObject(
                    "SELECT CASE WHEN used THEN 'usado' "
                            + "          WHEN expires_at <= now() THEN 'vencido' "
                            + "          ELSE 'valido' END AS estado, "
                            + "       CASE WHEN NOT used AND expires_at > now() "
                            + "            THEN email END AS email, "
                            + "       CASE WHEN NOT used AND expires_at > now() "
                            + "            THEN tenant_id END AS tenant_id "
                            + "FROM password_resets WHERE token_hash = ?",
                    (rs, i) -> new ConsultaDeReset(
                            EstadoDelToken.valueOf(rs.getString("estado")),
                            rs.getString("email"), rs.getString("tenant_id")),
                    tokenHash);
            return r != null ? r : new ConsultaDeReset(EstadoDelToken.no_existe, null, null);
        } catch (EmptyResultDataAccessException e) {
            return new ConsultaDeReset(EstadoDelToken.no_existe, null, null);
        }
    }

    /** @return cuántas filas marcó (0 = el token no existe o ya estaba usado). */
    public int markResetUsed(String tokenHash) {
        return jdbc.update(
                "UPDATE password_resets SET used = true WHERE token_hash = ? AND used = false",
                tokenHash);
    }

    /**
     * Actualiza el hash de un usuario acotando por email+tenant (defensa en
     * profundidad: aunque la política RLS de app_user es abierta, nunca se toca la
     * fila de otro negocio). Devuelve cuántas filas cambió (0 = no coincide).
     */
    public int updatePasswordHash(String email, String tenantId, String newHash) {
        return jdbc.update(
                "UPDATE users SET password_hash = ? "
                        + "WHERE lower(email) = lower(?) AND tenant_id = ?",
                newHash, email, tenantId);
    }
}
