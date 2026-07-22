package com.suresell.orders.multitenant;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fuente de verdad (server-authoritative) del mapa **plan → módulos** (F3, docs/160
 * / docs/50). La UI del POS refleja estos módulos; el backend los HACE CUMPLIR
 * (ver {@link ModuleAccessFilter}). Espejo temporal de `feature.model.ts` del front;
 * en F3.2 los overrides por-tenant se suman encima, y en F3.3 esto se mueve a DB
 * gestionable desde el panel.
 */
public final class PlanCatalog {

    private PlanCatalog() {}

    public static final String VENTAS = "ventas";
    public static final String HISTORIAL = "historial";
    public static final String CIERRE = "cierre";
    public static final String DESCUENTOS = "descuentos";
    public static final String COCINA = "cocina";

    private static final Map<String, List<String>> PLAN_MODULES = Map.of(
            "basico", List.of(VENTAS, HISTORIAL, CIERRE, COCINA),
            "pro", List.of(VENTAS, HISTORIAL, CIERRE, DESCUENTOS, COCINA));

    private static final List<String> DEFAULT = List.of(VENTAS, HISTORIAL, CIERRE, DESCUENTOS, COCINA);

    /** Todos los módulos conocidos (para validar overrides). */
    public static final Set<String> KNOWN = Set.of(VENTAS, HISTORIAL, CIERRE, DESCUENTOS, COCINA);

    /** Módulos incluidos por el plan; si el plan es desconocido, cae a `pro` (todos). */
    public static List<String> modulesForPlan(String plan) {
        if (plan == null) {
            return DEFAULT;
        }
        return PLAN_MODULES.getOrDefault(plan.trim().toLowerCase(), DEFAULT);
    }

    public static boolean isKnownModule(String module) {
        return module != null && KNOWN.contains(module.trim().toLowerCase());
    }

    /**
     * Módulos EFECTIVOS = (módulos del plan ∪ grants) − revokes (F3, Inc.2).
     * `overrides`: módulo → enabled (true regala, false quita).
     */
    public static List<String> effectiveModules(String plan, Map<String, Boolean> overrides) {
        Set<String> set = new LinkedHashSet<>(modulesForPlan(plan));
        if (overrides != null) {
            for (Map.Entry<String, Boolean> e : overrides.entrySet()) {
                if (!isKnownModule(e.getKey())) {
                    continue;
                }
                if (Boolean.TRUE.equals(e.getValue())) {
                    set.add(e.getKey());
                } else {
                    set.remove(e.getKey());
                }
            }
        }
        return List.copyOf(set);
    }
}
