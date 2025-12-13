/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.controller.OrderController
 *  com.suresell.order.model.record.OrderItemResponseRecord
 *  com.suresell.order.model.record.OrderRequestRecord
 *  com.suresell.order.model.record.OrderResponseRecord
 *  com.suresell.order.serivices.OrderService
 *  jakarta.validation.Valid
 *  lombok.Generated
 *  org.apache.poi.ss.usermodel.Row
 *  org.apache.poi.ss.usermodel.Sheet
 *  org.apache.poi.xssf.usermodel.XSSFWorkbook
 *  org.springframework.http.HttpStatus
 *  org.springframework.http.HttpStatusCode
 *  org.springframework.http.ResponseEntity
 *  org.springframework.http.ResponseEntity$BodyBuilder
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PatchMapping
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.PutMapping
 *  org.springframework.web.bind.annotation.RequestBody
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RequestParam
 *  org.springframework.web.bind.annotation.RestController
 */
package com.suresell.order.controller;

import com.suresell.order.model.record.OrderItemResponseRecord;
import com.suresell.order.model.record.OrderRequestRecord;
import com.suresell.order.model.record.OrderResponseRecord;
import com.suresell.order.serivices.OrderService;
import jakarta.validation.Valid;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import lombok.Generated;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/orders"})
public class OrderController {
    private final OrderService orderService;

    @PostMapping(value={"/create"})
    public ResponseEntity<Map<String, String>> createOrder(@RequestBody @Valid OrderRequestRecord dto) {
        this.orderService.createOrUpdateOrder(dto);
        return ResponseEntity.status((HttpStatusCode)HttpStatus.CREATED).body(Map.of("message", "Orden creada con \u00e9xito"));
    }

    @PutMapping(value={"/{orderId}"})
    public ResponseEntity<Map<String, String>> updateOrder(@PathVariable Long orderId, @RequestBody @Valid OrderRequestRecord dto) {
        this.orderService.updateOrder(orderId, dto);
        return ResponseEntity.ok(Map.of("message", "Orden actualizada con \u00e9xito"));
    }

    @GetMapping(value={"/cocina"})
    public List<OrderResponseRecord> getKitchenOrders() {
        return this.orderService.getKitchenOrders();
    }

    @GetMapping(value={"/historial"})
    public List<OrderResponseRecord> getAllOrders() {
        return this.orderService.getAllOrders();
    }

    @PatchMapping(value={"/{orderId}/status"})
    public ResponseEntity<Map<String, String>> updateStatus(@PathVariable Long orderId, @RequestParam String status) {
        try {
            this.orderService.updateStatus(orderId, status);
            return ResponseEntity.ok(Map.of("message", "Estado actualizado correctamente"));
        }
        catch (IllegalArgumentException e) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
        catch (RuntimeException e) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping(value={"/{orderId}/payment-method"})
    public ResponseEntity<Map<String, String>> updatePaymentMethod(@PathVariable Long orderId, @RequestParam String paymentMethod) {
        try {
            this.orderService.updatePaymentMethod(orderId, paymentMethod);
            return ResponseEntity.ok(Map.of("message", "M\u00e9todo de pago actualizado correctamente"));
        }
        catch (IllegalArgumentException e) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
        catch (RuntimeException e) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping(value={"/{orderId}"})
    public ResponseEntity<OrderResponseRecord> getOrderById(@PathVariable Long orderId) {
        OrderResponseRecord order = this.orderService.getOrderById(orderId);
        return ResponseEntity.ok((Object)order);
    }

    @GetMapping(value={"/report"})
    public ResponseEntity<?> getSalesReport(@RequestParam(defaultValue="json") String format) throws Exception {
        List report = this.orderService.getSalesReport();
        if ("excel".equalsIgnoreCase(format)) {
            try (XSSFWorkbook workbook = new XSSFWorkbook();){
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
                    row.createCell(0).setCellValue((double)order.idOrder().longValue());
                    row.createCell(1).setCellValue(String.valueOf(order.pagerColor()) + " #" + order.pagerNumber());
                    row.createCell(2).setCellValue(order.createdAt().toString());
                    row.createCell(3).setCellValue((double)order.subtotal());
                    row.createCell(5).setCellValue((double)order.total());
                    row.createCell(6).setCellValue(order.status());
                    StringBuilder productos = new StringBuilder();
                    for (OrderItemResponseRecord item : order.items()) {
                        productos.append(item.nameProduct()).append(" x").append(item.quantity()).append(" ($").append(item.unitPrice()).append(") | ");
                    }
                    row.createCell(7).setCellValue(productos.toString());
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                workbook.write((OutputStream)out);
                byte[] bytes = out.toByteArray();
                ResponseEntity responseEntity = ((ResponseEntity.BodyBuilder)((ResponseEntity.BodyBuilder)ResponseEntity.ok().header("Content-Disposition", new String[]{"attachment; filename=ventas.xlsx"})).header("Content-Type", new String[]{"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"})).body((Object)bytes);
                return responseEntity;
            }
        }
        return ResponseEntity.ok((Object)report);
    }

    @PatchMapping(value={"/{orderId}/apply-discount"})
    public ResponseEntity<?> applyDiscountToOrder(@PathVariable Long orderId, @RequestParam String discountCode) {
        try {
            OrderResponseRecord updatedOrder = this.orderService.applyDiscountToOrder(orderId, discountCode);
            return ResponseEntity.ok((Object)updatedOrder);
        }
        catch (IllegalArgumentException e) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
        catch (IllegalStateException e) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
        catch (RuntimeException e) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping(value={"/{orderId}/deliver"})
    public ResponseEntity<Map<String, String>> markAsDelivered(@PathVariable Long orderId) {
        try {
            this.orderService.markAsDelivered(orderId);
            return ResponseEntity.ok(Map.of("message", "Orden marcada como entregada y pager liberado"));
        }
        catch (IllegalStateException e) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
        catch (RuntimeException e) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @Generated
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }
}

