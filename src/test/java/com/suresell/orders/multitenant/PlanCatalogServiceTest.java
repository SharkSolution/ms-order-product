package com.suresell.orders.multitenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * N4 — El mapa plan → módulos pasó de constantes de Java a base de datos (V27).
 * Lo que se protege aquí es que ese cambio no pueda dejar a un negocio sin POS.
 */
class PlanCatalogServiceTest {

    private PlanRepository repo;
    private PlanCatalogService service;

    @BeforeEach
    void setUp() {
        repo = mock(PlanRepository.class);
        service = new PlanCatalogService(repo);
    }

    @Test
    void losModulosSalenDeLaBaseCuandoHayPlanes() {
        when(repo.findAll()).thenReturn(List.of(
                new PlanRepository.Plan("asociado", "Asociado", null, true,
                        List.of("ventas", "historial", "valeras"))));

        assertEquals(List.of("ventas", "historial", "valeras"), service.modulesForPlan("asociado"));
    }

    /**
     * Si la tabla falla, un negocio NO puede quedarse sin módulos: eso es
     * quedarse sin POS. Se cae a las constantes.
     */
    @Test
    void siLaBaseFallaSeCaeALasConstantesEnVezDeDejarSinModulos() {
        when(repo.findAll()).thenThrow(new RuntimeException("relation \"plans\" does not exist"));

        List<String> modulos = service.modulesForPlan("basico");

        assertEquals(PlanCatalog.modulesForPlan("basico"), modulos);
        assertTrue(modulos.contains(PlanCatalog.VENTAS));
    }

    /** Un fallo no se cachea: el siguiente request tiene que reintentar. */
    @Test
    void elFalloNoSeCachea() {
        when(repo.findAll()).thenThrow(new RuntimeException("caída transitoria"));
        service.modulesForPlan("basico");
        service.modulesForPlan("basico");

        verify(repo, times(2)).findAll();
    }

    /** Plan que no está en la base: mismo default que antes, no lista vacía. */
    @Test
    void planDesconocidoCaeAlDefault() {
        when(repo.findAll()).thenReturn(List.of(
                new PlanRepository.Plan("basico", "Básico", null, true, List.of("ventas"))));

        assertEquals(PlanCatalog.modulesForPlan("no-existe"), service.modulesForPlan("no-existe"));
    }

    @Test
    void losOverridesRegalanYQuitanSobreElPlanDeLaBase() {
        when(repo.findAll()).thenReturn(List.of(
                new PlanRepository.Plan("basico", "Básico", null, true,
                        List.of("ventas", "historial"))));

        List<String> efectivos = service.effectiveModules("basico",
                Map.of("valeras", true, "historial", false));

        assertTrue(efectivos.contains("ventas"));
        assertTrue(efectivos.contains("valeras"), "el override debe regalar el módulo");
        assertFalse(efectivos.contains("historial"), "el override debe poder quitarlo");
    }

    @Test
    void unModuloInventadoEnLosOverridesSeIgnora() {
        when(repo.findAll()).thenReturn(List.of(
                new PlanRepository.Plan("basico", "Básico", null, true, List.of("ventas"))));

        List<String> efectivos = service.effectiveModules("basico", Map.of("modulo-fantasma", true));

        assertEquals(List.of("ventas"), efectivos);
    }

    @Test
    void invalidarObligaAReleerLaBase() {
        when(repo.findAll()).thenReturn(List.of(
                new PlanRepository.Plan("basico", "Básico", null, true, List.of("ventas"))));
        service.modulesForPlan("basico");
        service.modulesForPlan("basico");
        verify(repo, times(1)).findAll();

        service.invalidar();
        service.modulesForPlan("basico");
        verify(repo, times(2)).findAll();
    }

    /** Con la base vacía el KAM no puede quedar en blanco. */
    @Test
    void elCatalogoNuncaSaleVacio() {
        when(repo.findAll()).thenReturn(List.of());

        List<PlanRepository.Plan> catalogo = service.catalogo();

        assertEquals(2, catalogo.size());
        assertTrue(catalogo.stream().anyMatch(p -> p.id().equals("basico")));
        assertTrue(catalogo.stream().anyMatch(p -> p.id().equals("pro")));
    }
}
