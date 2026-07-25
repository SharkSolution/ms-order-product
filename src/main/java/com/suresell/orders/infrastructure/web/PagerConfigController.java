package com.suresell.orders.infrastructure.web;

import com.suresell.orders.application.dto.PagerGroupDto;
import com.suresell.orders.application.usecase.PagerConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Configuración de rastreadores del negocio (N2/6.7). El POS lee GET al arrancar;
 * la edición es solo del admin.
 */
@RestController
@RequestMapping("/account/pagers")
@RequiredArgsConstructor
@Tag(name = "Rastreadores", description = "Configuración de rastreadores por negocio")
public class PagerConfigController {

    private final PagerConfigService service;
    private final com.suresell.orders.multitenant.JwtTenantResolver resolver;

    @GetMapping
    @Operation(summary = "Grupos de rastreadores del negocio")
    public ResponseEntity<List<PagerGroupDto>> get() {
        return ResponseEntity.ok(service.getGroups());
    }

    @PutMapping
    @Operation(summary = "Actualizar nombre, color y cantidad de los grupos (solo admin)")
    public ResponseEntity<?> update(@RequestHeader(value = "Authorization", required = false) String auth,
                                    @RequestBody List<PagerGroupDto> groups) {
        if (!resolver.resolveRole(auth).map("admin"::equalsIgnoreCase).orElse(false)) {
            return ResponseEntity.status(403).body(Map.of("error", "Solo un administrador puede configurar los rastreadores"));
        }
        try {
            return ResponseEntity.ok(service.updateGroups(groups));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
