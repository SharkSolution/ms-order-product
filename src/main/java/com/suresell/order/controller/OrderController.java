package com.suresell.order.controller;

import com.suresell.order.adapter.OrderRequestAdapter;
import com.suresell.order.model.entity.OrderEditHistory;
import com.suresell.order.model.record.OrderItemResponseRecord;
import com.suresell.order.model.record.OrderRequestRecord;
import com.suresell.order.model.record.OrderResponseRecord;
import com.suresell.order.model.record.OrderSyncResponse;
import com.suresell.order.model.record.PageResponse;
import com.suresell.order.serivices.OrderService;
import com.suresell.order.serivices.ResilientOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/**
 * Controller de órdenes con soporte offline-first.
 * Los endpoints críticos (create, cocina) usan ResilientOrderService.
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;
    private final ResilientOrderService resilientOrderService;
    private final OrderRequestAdapter orderRequestAdapter;

    /**
     * Crea una orden con fallback automático a modo offline si AWS falla.
     * NUNCA debe retornar error 500 por fallo de AWS.
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> createOrder(@RequestBody Map<String, Object> payload) {
        try {
            OrderRequestRecord dto = orderRequestAdapter.normalize(payload);
            resilientOrderService.createOrder(dto);

            log.info("Order created successfully");

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Orden creada con éxito"));
        } catch (Exception e) {
            log.error("Critical error creating order: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al crear orden. Por favor intente nuevamente."));
        }
    }

    /**
     * Endpoint idempotente para sincronización de órdenes offline.
     * Usa idempotencyKey en el header para prevenir duplicados.
     *
     * Si ya existe una orden con el mismo idempotencyKey, retorna la existente.
     * Si no existe, crea una nueva.
     *
     * Uso:
     * POST /orders/sync
     * Header: X-Idempotency-Key: uuid-aqui
     * Body: { mismo formato que /orders/create }
     */
    @PostMapping("/sync")
    public ResponseEntity<OrderSyncResponse> syncOrder(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody Map<String, Object> payload) {
        try {
            log.info("🔄 [SYNC] Received sync request with idempotencyKey: {}", idempotencyKey);

            OrderRequestRecord dto = orderRequestAdapter.normalize(payload);
            OrderSyncResponse response = orderService.syncOrderIdempotent(idempotencyKey, dto);

            if (response.success()) {
                if ("CREATED".equals(response.status())) {
                    log.info("✅ [SYNC] Order created: ID {}", response.orderId());
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                } else {
                    log.info("✅ [SYNC] Order already exists: ID {}", response.orderId());
                    return ResponseEntity.ok(response);
                }
            } else {
                log.error("❌ [SYNC] Failed: {}", response.message());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (Exception e) {
            log.error("❌ [SYNC] Critical error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(OrderSyncResponse.error("Error interno: " + e.getMessage()));
        }
    }

    @PutMapping("/{orderId}")
    public ResponseEntity<Map<String, String>> updateOrder(
            @PathVariable Long orderId,
            @RequestBody Map<String, Object> payload) {
        OrderRequestRecord dto = orderRequestAdapter.normalize(payload);
        orderService.updateOrder(orderId, dto);
        return ResponseEntity.ok(Map.of("message", "Orden actualizada con éxito"));
    }

    /**
     * Obtiene órdenes de cocina con fallback a cache si AWS falla.
     * NUNCA debe retornar error 500 o lista vacía si hay cache disponible.
     */
    @GetMapping("/cocina")
    public List<OrderResponseRecord> getKitchenOrders() {
        try {
            return resilientOrderService.getKitchenOrders();
        } catch (Exception e) {
            log.error("Critical error getting kitchen orders: {}", e.getMessage(), e);
            // En caso de error crítico, retornar lista vacía (mejor que error 500)
            return List.of();
        }
    }

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    @GetMapping("/historial")
    public ResponseEntity<PageResponse<OrderResponseRecord>> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long afterId) {

        // Cap duro al size para evitar queries gigantes
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        // Keyset pagination (recomendado para scroll infinito)
        if (afterId != null) {
            List<OrderResponseRecord> orders = orderService.getAllOrdersKeyset(afterId, safeSize);
            // Wrap en PageResponse para consistencia
            return ResponseEntity.ok(new PageResponse<>(
                orders,
                orders.size(),  // No sabemos el total exacto en keyset
                -1,             // totalPages desconocido en keyset
                safeSize,
                0,
                orders.size() < safeSize  // last = true si retornó menos del size pedido
            ));
        }

        // Siempre usar paginación - NO hay backward compatibility sin page
        Page<OrderResponseRecord> ordersPage = orderService.getAllOrdersPaginated(page, safeSize);
        return ResponseEntity.ok(PageResponse.from(ordersPage));
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<Map<String, String>> updateStatus(
            @PathVariable Long orderId,
            @RequestParam String status) {
        try {
            orderService.updateStatus(orderId, status);
            return ResponseEntity.ok(Map.of("message", "Estado actualizado correctamente"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{orderId}/payment-method")
    public ResponseEntity<Map<String, String>> updatePaymentMethod(
            @PathVariable Long orderId,
            @RequestParam String paymentMethod) {
        try {
            orderService.updatePaymentMethod(orderId, paymentMethod);
            return ResponseEntity.ok(Map.of("message", "Método de pago actualizado correctamente"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponseRecord> getOrderById(@PathVariable Long orderId) {
        OrderResponseRecord order = orderService.getOrderById(orderId);
        return ResponseEntity.ok(order);
    }

    /**
     * Obtiene el historial de ediciones de órdenes con paginación.
     * Requiere contraseña de administrador para acceder.
     * Si se proporciona orderId, filtra por esa orden específica.
     */
    @GetMapping("/edit-history")
    public ResponseEntity<PageResponse<OrderEditHistory>> getOrderEditHistory(
            @RequestParam(required = false) Long orderId,
            @RequestParam String adminPassword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        Page<OrderEditHistory> history = orderService.getOrderEditHistory(orderId, adminPassword, page, safeSize);
        return ResponseEntity.ok(PageResponse.from(history));
    }

    @GetMapping("/report")
    public ResponseEntity<?> getSalesReport(@RequestParam(defaultValue = "json") String format) throws Exception {
        List<OrderResponseRecord> report = orderService.getSalesReport();

        if ("excel".equalsIgnoreCase(format)) {
            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("Ventas");
                int rowIdx = 0;

                Row header = sheet.createRow(rowIdx++);
                header.createCell(0).setCellValue("ID Orden");
                header.createCell(1).setCellValue("Pager");
                header.createCell(2).setCellValue("Fecha");
                header.createCell(3).setCellValue("Subtotal");
                header.createCell(4).setCellValue("Impuesto");
                header.createCell(5).setCellValue("Total");
                header.createCell(6).setCellValue("Estado");
                header.createCell(7).setCellValue("Productos");

                for (OrderResponseRecord order : report) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(order.idOrder());
                    row.createCell(1).setCellValue(order.pagerColor() + " #" + order.pagerNumber());
                    row.createCell(2).setCellValue(order.createdAt().toString());
                    row.createCell(3).setCellValue(order.subtotal());
                    row.createCell(5).setCellValue(order.total());
                    row.createCell(6).setCellValue(order.status());

                    StringBuilder productos = new StringBuilder();
                    for (OrderItemResponseRecord item : order.items()) {
                        productos.append(item.nameProduct())
                                .append(" x").append(item.quantity())
                                .append(" ($").append(item.unitPrice()).append(") | ");
                    }
                    row.createCell(7).setCellValue(productos.toString());
                }

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                workbook.write(out);
                byte[] bytes = out.toByteArray();

                return ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=ventas.xlsx")
                        .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .body(bytes);
            }
        }

        return ResponseEntity.ok(report);
    }

    @PatchMapping("/{orderId}/apply-discount")
    public ResponseEntity<?> applyDiscountToOrder(
            @PathVariable Long orderId,
            @RequestParam String discountCode) {
        try {
            OrderResponseRecord updatedOrder = orderService.applyDiscountToOrder(orderId, discountCode);
            return ResponseEntity.ok(updatedOrder);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{orderId}/deliver")
    public ResponseEntity<Map<String, String>> markAsDelivered(
            @PathVariable Long orderId,
            @RequestBody Map<String, Integer> requestBody) {
        try {
            Integer elapsedSeconds = requestBody.get("elapsedSeconds");
            if (elapsedSeconds == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "El campo 'elapsedSeconds' es requerido en el cuerpo de la solicitud."));
            }
            orderService.markAsDelivered(orderId, elapsedSeconds);
            return ResponseEntity.ok(Map.of("message", "Orden marcada como entregada y pager liberado"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
