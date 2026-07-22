package com.suresell.orders.multitenant;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Mapa plan→módulos + overrides efectivos (F3). Puro, sin DB. */
class PlanCatalogTest {

    @Test
    void planBasicoNoTieneDescuentos_proSi() {
        assertTrue(PlanCatalog.modulesForPlan("basico").contains("cierre"));
        assertFalse(PlanCatalog.modulesForPlan("basico").contains("descuentos"));
        assertTrue(PlanCatalog.modulesForPlan("pro").contains("descuentos"));
    }

    @Test
    void overrideRegalaModulo() {
        assertTrue(PlanCatalog.effectiveModules("basico", Map.of("descuentos", true)).contains("descuentos"));
    }

    @Test
    void overrideQuitaModulo() {
        assertFalse(PlanCatalog.effectiveModules("pro", Map.of("descuentos", false)).contains("descuentos"));
    }

    @Test
    void ignoraModuloDesconocidoEnOverride() {
        assertFalse(PlanCatalog.effectiveModules("pro", Map.of("xyz", true)).contains("xyz"));
    }

    @Test
    void isKnownModule() {
        assertTrue(PlanCatalog.isKnownModule("descuentos"));
        assertFalse(PlanCatalog.isKnownModule("xyz"));
    }

    @Test
    void cocinaIncluidaEnAmbosPlanes() {
        assertTrue(PlanCatalog.modulesForPlan("basico").contains("cocina"));
        assertTrue(PlanCatalog.modulesForPlan("pro").contains("cocina"));
        assertTrue(PlanCatalog.isKnownModule("cocina"));
    }

    @Test
    void overridePuedeQuitarCocina() {
        assertFalse(PlanCatalog.effectiveModules("basico", Map.of("cocina", false)).contains("cocina"));
    }
}
