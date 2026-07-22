package com.suresell.orders.infrastructure.web;

import com.suresell.orders.application.dto.WaiterDtos.CloseShiftRequest;
import com.suresell.orders.application.dto.WaiterDtos.CreateWaiterRequest;
import com.suresell.orders.application.dto.WaiterDtos.MenuCategoryDto;
import com.suresell.orders.application.dto.WaiterDtos.OpenShiftRequest;
import com.suresell.orders.application.dto.WaiterDtos.ShiftSummaryResponse;
import com.suresell.orders.application.dto.WaiterDtos.WaiterOrderRequest;
import com.suresell.orders.application.dto.WaiterDtos.WaiterOrderResponse;
import com.suresell.orders.application.usecase.WaiterService;
import com.suresell.orders.domain.model.Waiter;
import com.suresell.orders.domain.model.WaiterSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Módulo meseros (F4 Inc.3, docs/200): espejo multi-tenant de los endpoints del
 * ms-order-waiter legacy (/api/mobile y /api/shifts), bajo el JWT del negocio.
 * La app repunta sus dos base-URLs a `<backend>/api/waiter/mobile` y
 * `<backend>/api/waiter/shifts` (Inc.4).
 */
@RestController
@RequestMapping("/api/waiter")
public class WaiterController {

    private final WaiterService service;

    public WaiterController(WaiterService service) {
        this.service = service;
    }

    // ---------------------------- /mobile -----------------------------

    @GetMapping("/mobile/waiters")
    public List<Waiter> getWaiters() {
        return service.getActiveWaiters();
    }

    @PostMapping("/mobile/waiters")
    public Waiter createWaiter(@RequestBody CreateWaiterRequest request) {
        return service.createWaiter(request);
    }

    @PostMapping("/mobile/login/{waiterId}")
    public WaiterSession login(@PathVariable Long waiterId) {
        return service.login(waiterId);
    }

    @PostMapping("/mobile/logout/{sessionId}")
    public Map<String, String> logout(@PathVariable UUID sessionId) {
        service.logout(sessionId);
        return Map.of("message", "Sesión cerrada correctamente");
    }

    @GetMapping("/mobile/menu")
    public List<MenuCategoryDto> getMenu() {
        return service.getMenu();
    }

    @PostMapping("/mobile/orders")
    public ResponseEntity<WaiterOrderResponse> createOrder(@RequestBody WaiterOrderRequest request) {
        try {
            return ResponseEntity.status(201).body(service.createOrder(request));
        } catch (DataIntegrityViolationException e) {
            // Carrera de doble-envío con la misma idempotencyKey: el índice único
            // impidió el duplicado — devolvemos la orden ya existente (como el legacy).
            var existing = service.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                return ResponseEntity.ok(existing.get());
            }
            throw e;
        }
    }

    @GetMapping("/mobile/orders/history")
    public List<WaiterOrderResponse> getHistory(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String pagerNumber,
            @RequestParam(required = false) String pagerColor,
            @RequestParam(required = false) Long waiterId) {
        return service.getHistory(id, pagerNumber, pagerColor, waiterId);
    }

    // ---------------------------- /shifts -----------------------------

    @PostMapping("/shifts/open")
    public ResponseEntity<WaiterSession> openShift(@RequestBody OpenShiftRequest request) {
        return ResponseEntity.status(201).body(service.openShift(request));
    }

    @GetMapping("/shifts/{sessionId}/summary")
    public ShiftSummaryResponse getSummary(@PathVariable UUID sessionId) {
        return service.getShiftSummary(sessionId);
    }

    @PostMapping("/shifts/{sessionId}/close")
    public ShiftSummaryResponse closeShift(@PathVariable UUID sessionId,
                                           @RequestBody CloseShiftRequest request) {
        return service.closeShift(sessionId, request);
    }
}
