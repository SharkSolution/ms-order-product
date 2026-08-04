package com.suresell.orders.infrastructure.web;

import com.suresell.orders.application.dto.WaiterDtos.CloseShiftRequest;
import com.suresell.orders.application.dto.WaiterDtos.CreateWaiterRequest;
import com.suresell.orders.application.dto.WaiterDtos.MenuCategoryDto;
import com.suresell.orders.application.dto.WaiterDtos.OpenShiftRequest;
import com.suresell.orders.application.dto.WaiterDtos.ShiftSummaryResponse;
import com.suresell.orders.application.dto.WaiterDtos.WaiterOrderRequest;
import com.suresell.orders.application.dto.WaiterDtos.WaiterOrderResponse;
import com.suresell.orders.application.usecase.PinDeMeseroService;
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
    private final PinDeMeseroService pinService;
    private final com.suresell.orders.multitenant.JwtTenantResolver resolver;

    public WaiterController(WaiterService service, PinDeMeseroService pinService,
                            com.suresell.orders.multitenant.JwtTenantResolver resolver) {
        this.service = service;
        this.pinService = pinService;
        this.resolver = resolver;
    }

    // ---------------------------- /mobile -----------------------------

    @GetMapping("/mobile/waiters")
    public List<Waiter> getWaiters(@RequestParam(name = "all", defaultValue = "false") boolean all) {
        return all ? service.getAllWaiters() : service.getActiveWaiters();
    }

    @PostMapping("/mobile/waiters")
    public Waiter createWaiter(@RequestBody CreateWaiterRequest request) {
        return service.createWaiter(request);
    }

    @org.springframework.web.bind.annotation.PutMapping("/mobile/waiters/{id}")
    public Waiter updateWaiter(@PathVariable Long id,
                               @RequestBody com.suresell.orders.application.dto.WaiterDtos.UpdateWaiterRequest request) {
        return service.updateWaiter(id, request);
    }

    /**
     * Marca que el 401/429 es por la CLAVE DEL MESERO y no porque se haya
     * vencido el JWT del negocio. El cliente necesita distinguirlos: uno se
     * resuelve escribiendo bien la clave, el otro sacando al usuario del
     * negocio entero.
     */
    static final String CODIGO_PIN = "PIN_MESERO";

    /** Clave del mesero al entrar (#20). Cuerpo opcional: `{"pin":"1234"}`. */
    public record LoginRequest(String pin) {
    }

    @PostMapping("/mobile/login/{waiterId}")
    public ResponseEntity<?> login(@PathVariable Long waiterId,
                                   @RequestBody(required = false) LoginRequest req) {
        try {
            return ResponseEntity.ok(service.login(waiterId, req == null ? null : req.pin()));
        } catch (PinDeMeseroService.DemasiadosIntentosException e) {
            return ResponseEntity.status(429)
                    .body(Map.of("error", e.getMessage(), "codigo", CODIGO_PIN));
        } catch (PinDeMeseroService.PinIncorrectoException e) {
            // 401 y no 403: la clave está mal, no es que le falten permisos.
            //
            // Con el CÓDIGO, porque el cliente ya trata cualquier 401 como
            // "se venció la sesión del negocio" y sacaría al mesero de todo.
            // Son dos 401 con significados muy distintos.
            return ResponseEntity.status(401)
                    .body(Map.of("error", e.getMessage(), "codigo", CODIGO_PIN));
        }
    }

    public record CambiarPinRequest(String pinActual, String pinNuevo) {
    }

    /**
     * El mesero configura o cambia SU clave (#20).
     *
     * <p>Lo hace él, no el administrador: una clave que otro conoce no protege
     * al mesero de que le cierren el turno, que es justo el problema.
     */
    @PostMapping("/mobile/waiters/{waiterId}/pin")
    public ResponseEntity<?> configurarPin(@PathVariable Long waiterId,
                                           @RequestBody CambiarPinRequest req) {
        try {
            pinService.configurar(waiterId, req == null ? null : req.pinActual(),
                    req == null ? null : req.pinNuevo());
            return ResponseEntity.ok(Map.of("message", "Clave actualizada"));
        } catch (PinDeMeseroService.DemasiadosIntentosException e) {
            return ResponseEntity.status(429)
                    .body(Map.of("error", e.getMessage(), "codigo", CODIGO_PIN));
        } catch (PinDeMeseroService.PinIncorrectoException e) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", e.getMessage(), "codigo", CODIGO_PIN));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Quita la clave de un mesero que la olvidó. Solo administrador. */
    @org.springframework.web.bind.annotation.DeleteMapping("/mobile/waiters/{waiterId}/pin")
    public ResponseEntity<?> quitarPin(@PathVariable Long waiterId,
                                       jakarta.servlet.http.HttpServletRequest http) {
        boolean esAdmin = resolver.resolveRole(http.getHeader("Authorization"))
                .map("admin"::equalsIgnoreCase).orElse(false);
        if (!esAdmin) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Solo un administrador puede quitar la clave de un mesero"));
        }
        try {
            pinService.quitar(waiterId);
            return ResponseEntity.ok(Map.of("message", "Clave eliminada"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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
