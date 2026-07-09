package com.suresell.orders.multitenant;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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

    public record TenantRow(String id, String name, String plan, String status) {}

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
                    "SELECT id, name, plan, status FROM tenants WHERE id = ?",
                    (rs, i) -> new TenantRow(rs.getString("id"), rs.getString("name"),
                            rs.getString("plan"), rs.getString("status")),
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

    public void insertTenant(String id, String name, String plan) {
        jdbc.update("INSERT INTO tenants (id, name, plan) VALUES (?, ?, ?)", id, name, plan);
    }

    public void insertUser(String email, String passwordHash, String tenantId, String role) {
        jdbc.update("INSERT INTO users (email, password_hash, tenant_id, role) VALUES (?, ?, ?, ?)",
                email, passwordHash, tenantId, role);
    }
}
