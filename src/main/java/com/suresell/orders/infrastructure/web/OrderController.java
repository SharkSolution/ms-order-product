package com.suresell.orders.infrastructure.web;

import com.suresell.orders.infrastructure.web.adapter.OrderRequestWebAdapter;
import com.suresell.orders.domain.model.OrderEditHistory;
import com.suresell.orders.application.dto.OrderItemResponseRecord;
import com.suresell.orders.application.dto.OrderRequestRecord;
import com.suresell.orders.application.dto.OrderResponseRecord;
import com.suresell.orders.application.dto.OrderSyncResponse;
import com.suresell.orders.application.dto.PageResponse;
import com.suresell.orders.domain.port.in.OrderPort; // Renamed from OrderService
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


@RestController
@RequestMapping("/orders")
@Slf4j
public class OrderController {

    private final OrderPort orderPort;
    private final OrderRequestWebAdapter orderRequestWebAdapter;

    public OrderController(OrderPort orderPort, OrderRequestWebAdapter orderRequestWebAdapter) {
        this.orderPort = orderPort;
        this.orderRequestWebAdapter = orderRequestWebAdapter;
    }


    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> createOrder(@RequestBody Map<String, Object> payload) {
        try {
            OrderRequestRecord dto = orderRequestWebAdapter.normalize(payload);
            orderPort.createOrUpdateOrder(dto);

            log.info("Order created successfully");

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Orden creada con éxito"));
        } catch (Exception e) {
            log.error("Critical error creating order: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al crear orden. Por favor intente nuevamente."));
        }
    }


    @PostMapping("/sync")
    public ResponseEntity<OrderSyncResponse> syncOrder(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody Map<String, Object> payload) {
        try {
            log.info("🔄 [SYNC] Received sync request with idempotencyKey: {}", idempotencyKey);

            OrderRequestRecord dto = orderRequestWebAdapter.normalize(payload);
            OrderSyncResponse response = orderPort.syncOrderIdempotent(idempotencyKey, dto);

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
        OrderRequestRecord dto = orderRequestWebAdapter.normalize(payload);
        orderPort.updateOrder(orderId, dto);
        return ResponseEntity.ok(Map.of("message", "Orden actualizada con éxito"));
    }


    @GetMapping("/cocina")
    public List<OrderResponseRecord> getKitchenOrders() {
        try {
            return orderPort.getKitchenOrders();
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

        
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        
        if (afterId != null) {
            List<OrderResponseRecord> orders = orderPort.getAllOrdersKeyset(afterId, safeSize);
            
            return ResponseEntity.ok(new PageResponse<>(
                orders,
                orders.size(),
                -1,
                safeSize,
                0,
                orders.size() < safeSize
            ));
        }

        
        Page<OrderResponseRecord> ordersPage = orderPort.getAllOrdersPaginated(page, safeSize);
        return ResponseEntity.ok(PageResponse.from(ordersPage));
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<Map<String, String>> updateStatus(
            @PathVariable Long orderId,
            @RequestParam String status) {
        try {
            orderPort.updateStatus(orderId, status);
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
            orderPort.updatePaymentMethod(orderId, paymentMethod);
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
        OrderResponseRecord order = orderPort.getOrderById(orderId);
        return ResponseEntity.ok(order);
    }


    @GetMapping("/edit-history")
    public ResponseEntity<PageResponse<OrderEditHistory>> getOrderEditHistory(
            @RequestParam(required = false) Long orderId,
            @RequestParam String adminPassword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        Page<OrderEditHistory> history = orderPort.getOrderEditHistory(orderId, adminPassword, page, safeSize);
        return ResponseEntity.ok(PageResponse.from(history));
    }

    @GetMapping("/report")
    public ResponseEntity<?> getSalesReport(@RequestParam(defaultValue = "json") String format) throws Exception {
        List<OrderResponseRecord> report = orderPort.getSalesReport();

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
            OrderResponseRecord updatedOrder = orderPort.applyDiscountToOrder(orderId, discountCode);
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
            orderPort.markAsDelivered(orderId, elapsedSeconds);
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
