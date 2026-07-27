package com.suresell.orders.infrastructure.web;

import com.suresell.orders.application.dto.WaiterSalesDtos.WaiterSalesResponse;
import com.suresell.orders.application.usecase.WaiterSalesQueryService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ventas por mesero del día (cierre de caja del POS).
 *
 * Misma ruta y misma forma de respuesta que el endpoint de ms-core-app, pero
 * calculado sobre la base de V2: tras el cutover, el de ms-core-app lee la base
 * legacy y por eso la pantalla no se actualizaba y mostraba un solo mesero.
 */
@RestController
public class WaiterSalesController {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final WaiterSalesQueryService service;

    public WaiterSalesController(WaiterSalesQueryService service) {
        this.service = service;
    }

    @GetMapping("/api/waiter-sales")
    public ResponseEntity<WaiterSalesResponse> ventasDelDia(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        // Sin fecha, HOY en hora de Bogotá: con la del servidor (UTC), después de
        // las 7 p. m. locales el cierre pediría el día siguiente.
        LocalDate dia = date != null ? date : LocalDate.now(BOGOTA);
        return ResponseEntity.ok(service.ventasDelDia(dia));
    }
}
