package com.suresell.orders.infrastructure.web;
import com.suresell.orders.application.dto.OrderRequestRecord;
import com.suresell.orders.application.dto.OrderResponseRecord;
import com.suresell.orders.application.dto.PageResponse;
import com.suresell.orders.application.dto.PagerAvailabilityResponse;
import com.suresell.orders.domain.model.OrderEditHistory;
import com.suresell.orders.domain.port.in.OrderPort;
import com.suresell.orders.infrastructure.web.adapter.OrderRequestWebAdapter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@Slf4j
@Tag(name = "Orders", description = "Gestión de órdenes")
public class OrderController {
    private static final int MAX_PAGE_SIZE = 50;
    private final OrderPort orderPort;
    private final OrderRequestWebAdapter orderRequestWebAdapter;
    public OrderController(OrderPort orderPort, OrderRequestWebAdapter orderRequestWebAdapter) {
        this.orderPort = orderPort;
        this.orderRequestWebAdapter = orderRequestWebAdapter;
    }
    @GetMapping("/pager-availability")
    @Operation(summary = "Obtener disponibilidad de pagers", description = "Lista los pagers disponibles y ocupados (Amarillo y Azul del 1 al 16).")
    public ResponseEntity<PagerAvailabilityResponse> getPagerAvailability() {
        return ResponseEntity.ok(orderPort.getPagerAvailability());
    }
    @PostMapping("/create")
    @Operation(summary = "Crear orden")
    public ResponseEntity<Map<String, String>> createOrder(@RequestBody Map<String, Object> payload) {
        OrderRequestRecord dto = orderRequestWebAdapter.normalize(payload);
        orderPort.createOrUpdateOrder(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Orden creada con éxito"));
    }
    @PutMapping("/{orderId}")
    @Operation(summary = "Editar orden")
    public ResponseEntity<Map<String, String>> updateOrder(
            @Parameter(description = "ID de la orden") @PathVariable Long orderId,
            @RequestBody Map<String, Object> payload) {
        OrderRequestRecord dto = orderRequestWebAdapter.normalize(payload);
        orderPort.updateOrder(orderId, dto);
        return ResponseEntity.ok(Map.of("message", "Orden actualizada con éxito"));
    }
    @GetMapping("/historial")
    @Operation(summary = "Historial de órdenes paginado con filtros")
    public ResponseEntity<PageResponse<OrderResponseRecord>> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String pagerColor,
            @RequestParam(required = false) String pagerNumber,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long afterId) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        if (afterId != null) {
            List<OrderResponseRecord> orders = orderPort.getAllOrdersKeyset(afterId, safeSize);
            return ResponseEntity.ok(new PageResponse<>(
                    orders,
                    orders.size(),
                    -1,
                    safeSize,
                    0,
                    orders.size() < safeSize));
        }
        String filterColor = (pagerColor == null || pagerColor.isEmpty() || pagerColor.equalsIgnoreCase("Todos")) ? null : pagerColor;
        String filterNumber = (pagerNumber == null || pagerNumber.isEmpty()) ? null : pagerNumber;
        Page<OrderResponseRecord> ordersPage = orderPort.getAllOrdersPaginated(filterColor, filterNumber, orderId, page, safeSize);
        return ResponseEntity.ok(PageResponse.from(ordersPage));
    }
    @GetMapping("/{orderId}")
    @Operation(summary = "Obtener orden por ID")
    public ResponseEntity<OrderResponseRecord> getOrderById(@PathVariable Long orderId) {
        OrderResponseRecord order = orderPort.getOrderById(orderId);
        return ResponseEntity.ok(order);
    }
    @PatchMapping("/{orderId}/apply-discount")
    @Operation(summary = "Aplicar cupón de descuento a orden")
    public ResponseEntity<OrderResponseRecord> applyDiscountToOrder(
            @Parameter(description = "ID de la orden") @PathVariable Long orderId,
            @RequestParam String discountCode) {
        OrderResponseRecord updatedOrder = orderPort.applyDiscountToOrder(orderId, discountCode);
        return ResponseEntity.ok(updatedOrder);
    }

    @PatchMapping("/{orderId}/mark-as-printed")
    @Operation(summary = "Marcar orden como IMPRESA", description = "Cambia el estado de la orden a 'impreso' localmente y sincroniza con AWS para que la cocina no la duplique.")
    public ResponseEntity<Map<String, String>> markAsPrinted(@PathVariable Long orderId) {
        orderPort.markAsPrinted(orderId);
        return ResponseEntity.ok(Map.of("message", "Orden marcada como impresa y sincronización encolada"));
    }

    @PutMapping("/{orderId}/deliver")
    @Operation(summary = "Marcar orden como ENTREGADA manualmente", description = "Libera el pager asociado a la orden en la base de datos local.")
    public ResponseEntity<Map<String, String>> markAsDelivered(@PathVariable Long orderId) {
        orderPort.markAsDeliveredLocally(orderId);
        return ResponseEntity.ok(Map.of("message", "Orden marcada como entregada y pager liberado"));
    }

    @DeleteMapping("/pagers/release")
    @Operation(summary = "Liberar un Pager por Color y Número", description = "Busca la orden activa con ese pager y marca el pager como devuelto localmente.")
    public ResponseEntity<Map<String, String>> releasePager(
            @RequestParam String color,
            @RequestParam String number) {
        orderPort.releasePager(color, number);
        return ResponseEntity.ok(Map.of("message", "Proceso de liberación de pager ejecutado"));
    }
}
