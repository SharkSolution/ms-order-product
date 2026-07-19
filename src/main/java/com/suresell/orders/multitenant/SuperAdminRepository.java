package com.suresell.orders.multitenant;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a la tabla global `super_admins` y a operaciones cross-tenant que solo el
 * super-admin (KAM) realiza (listar todos los negocios, cambiar plan). F3, Inc.3.
 * SOLO perfil `cloud`. Corre sin tenant en contexto (endpoints /admin/**); las
 * tablas globales tienen política RLS abierta para app_user.
 */
@Repository
@Profile("cloud")
public class SuperAdminRepository {

    private final JdbcTemplate jdbc;

    public SuperAdminRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record SuperAdminRow(long id, String email, String passwordHash) {}

    public record TenantListItem(String id, String name, String plan, String status, int users) {}

    public Optional<SuperAdminRow> findByEmail(String email) {
        try {
            SuperAdminRow row = jdbc.queryForObject(
                    "SELECT id, email, password_hash FROM super_admins WHERE lower(email) = lower(?)",
                    (rs, i) -> new SuperAdminRow(rs.getLong("id"), rs.getString("email"),
                            rs.getString("password_hash")),
                    email);
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /** Todos los negocios con su conteo de usuarios (vista del panel). */
    public List<TenantListItem> listTenants() {
        return jdbc.query(
                "SELECT t.id, t.name, t.plan, t.status, "
                        + "(SELECT count(*) FROM users u WHERE u.tenant_id = t.id) AS users "
                        + "FROM tenants t ORDER BY t.created_at",
                (rs, i) -> new TenantListItem(rs.getString("id"), rs.getString("name"),
                        rs.getString("plan"), rs.getString("status"), rs.getInt("users")));
    }

    /** Cambia el plan de un negocio. Devuelve cuántas filas cambió (0 = no existe). */
    public int updateTenantPlan(String tenantId, String plan) {
        return jdbc.update("UPDATE tenants SET plan = ? WHERE id = ?", plan, tenantId);
    }
}
