package com.suresell.orders.infrastructure.web;

import com.suresell.orders.application.dto.KitchenOrderDto;
import com.suresell.orders.application.dto.KitchenOrderDto.DeliverRequest;
import com.suresell.orders.application.dto.KitchenOrderDto.KitchenPageDto;
import com.suresell.orders.application.usecase.KitchenQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Módulo cocina (F4 Inc.1, docs/200): los mismos 3 endpoints que la app
 * `app_mobile_kitchen` consume hoy del ms-kitchen legacy, bajo el JWT de tenant.
 * La app repunta su `kitchenBaseUrl` a `<backend>/api` y las rutas relativas
 * (`/kitchen/orders/...`) no cambian.
 */
@RestController
@RequestMapping("/api/kitchen/orders")
public class KitchenController {

    private final KitchenQueryService service;

    public KitchenController(KitchenQueryService service) {
        this.service = service;
    }

    @GetMapping("/active")
    public List<KitchenOrderDto> getActiveOrders() {
        return service.getActiveOrdersFifo();
    }

    @GetMapping("/delivered")
    public KitchenPageDto getDeliveredOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.getDeliveredOrders(page, size);
    }

    @PatchMapping("/{orderUuid}/deliver")
    public ResponseEntity<Void> markDelivered(
            @PathVariable UUID orderUuid,
            @RequestBody(required = false) DeliverRequest request) {
        service.markDelivered(orderUuid, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * N3/#1 — Marca como preparado lo que está pendiente en la orden.
     *
     * El cocinero lo usa cuando despacha: lo que la mesa pida después llegará
     * sin marcar y se resaltará como nuevo, sin repetir lo ya hecho.
     */
    @PatchMapping("/{idOrder}/items/prepared")
    public ResponseEntity<Map<String, Object>> marcarPreparados(@PathVariable Long idOrder) {
        int marcados = service.marcarItemsPreparados(idOrder);
        return ResponseEntity.ok(Map.of("idOrder", idOrder, "marcados", marcados));
    }

}
