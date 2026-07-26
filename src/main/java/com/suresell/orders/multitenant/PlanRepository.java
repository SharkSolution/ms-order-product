package com.suresell.orders.multitenant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Catálogo de planes en base de datos (V27). Antes vivía en constantes de Java,
 * así que crear un plan o cambiar qué incluye exigía redesplegar.
 *
 * <p>Tablas globales (sin tenant_id): un plan es del catálogo de SureSell, no de
 * un negocio. JDBC plano como el resto del paquete {@code multitenant}.
 */
@Repository
public class PlanRepository {

    private final JdbcTemplate jdbc;

    public PlanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Plan(String id, String name, String description, boolean active, List<String> modules) {
    }

    /** Todos los planes con sus módulos, ordenados por nombre. */
    public List<Plan> findAll() {
        Map<String, List<String>> modulesByPlan = new LinkedHashMap<>();
        jdbc.query("SELECT plan_id, module FROM plan_modules ORDER BY plan_id, module", rs -> {
            modulesByPlan.computeIfAbsent(rs.getString("plan_id"), k -> new ArrayList<>())
                    .add(rs.getString("module"));
        });
        return jdbc.query(
                "SELECT id, name, description, active FROM plans ORDER BY active DESC, name",
                (rs, i) -> new Plan(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getBoolean("active"),
                        List.copyOf(modulesByPlan.getOrDefault(rs.getString("id"), List.of()))));
    }

    public boolean exists(String id) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM plans WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    public void insert(String id, String name, String description) {
        jdbc.update("INSERT INTO plans (id, name, description) VALUES (?, ?, ?)", id, name, description);
    }

    public void update(String id, String name, String description, boolean active) {
        jdbc.update("UPDATE plans SET name = ?, description = ?, active = ?, updated_at = now() WHERE id = ?",
                name, description, active, id);
    }

    /** Reemplaza en bloque los módulos del plan: es lo que edita el KAM. */
    public void replaceModules(String planId, List<String> modules) {
        jdbc.update("DELETE FROM plan_modules WHERE plan_id = ?", planId);
        for (String m : modules) {
            jdbc.update("INSERT INTO plan_modules (plan_id, module) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    planId, m);
        }
    }

    /** Cuántos negocios están en este plan (para no dejarlo inutilizable sin avisar). */
    public int countTenants(String planId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM tenants WHERE plan = ?", Integer.class, planId);
        return n == null ? 0 : n;
    }
}
