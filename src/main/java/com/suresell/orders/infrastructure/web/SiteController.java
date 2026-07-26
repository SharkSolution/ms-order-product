package com.suresell.orders.infrastructure.web;

import com.suresell.orders.application.usecase.SiteService;
import com.suresell.orders.domain.model.Site;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Sedes y modo de POS (Inc. 1 del modo Restaurante).
 *
 * El POS consulta su modo al arrancar para saber qué flujo dibujar. Cambiarlo
 * NO está aquí: es potestad del KAM y vive en /admin (ver SuperAdminController).
 */
@RestController
@RequestMapping("/account/sites")
@RequiredArgsConstructor
@Tag(name = "Sedes", description = "Sedes y modo de POS (Plazoleta / Restaurante)")
public class SiteController {

    private final SiteService service;

    @GetMapping
    @Operation(summary = "Sedes del negocio")
    public ResponseEntity<List<Site>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @GetMapping("/mode")
    @Operation(summary = "Modo de POS efectivo del negocio (PLAZOLETA o RESTAURANTE)")
    public ResponseEntity<Map<String, Object>> modo() {
        return ResponseEntity.ok(Map.of(
                "posMode", service.modoEfectivo(),
                "restaurante", service.enModoRestaurante()));
    }
}
