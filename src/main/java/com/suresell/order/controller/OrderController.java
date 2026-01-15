package com.suresell.order.controller;

import com.suresell.order.adapter.OrderRequestAdapter;
import com.suresell.order.model.entity.OrderEditHistory;
import com.suresell.order.model.record.OrderItemResponseRecord;
import com.suresell.order.model.record.OrderRequestRecord;
import com.suresell.order.model.record.OrderResponseRecord;
import com.suresell.order.serivices.OrderService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderRequestAdapter orderRequestAdapter;

    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> createOrder(@RequestBody Map<String, Object> payload) {
        OrderRequestRecord dto = orderRequestAdapter.normalize(payload);
        orderService.createOrUpdateOrder(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Orden creada con éxito"));
    }

    @PutMapping("/{orderId}")
    public ResponseEntity<Map<String, String>> updateOrder(
            @PathVariable Long orderId,
            @RequestBody Map<String, Object> payload) {
        OrderRequestRecord dto = orderRequestAdapter.normalize(payload);
        orderService.updateOrder(orderId, dto);
        return ResponseEntity.ok(Map.of("message", "Orden actualizada con éxito"));
    }

    @GetMapping("/cocina")
    public List<OrderResponseRecord> getKitchenOrders() {
        return orderService.getKitchenOrders();
    }

    @GetMapping("/historial")
    public List<OrderResponseRecord> getAllOrders() {
        return orderService.getAllOrders();
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
    public ResponseEntity<Page<OrderEditHistory>> getOrderEditHistory(
            @RequestParam(required = false) Long orderId,
            @RequestParam String adminPassword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<OrderEditHistory> history = orderService.getOrderEditHistory(orderId, adminPassword, page, size);
        return ResponseEntity.ok(history);
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
