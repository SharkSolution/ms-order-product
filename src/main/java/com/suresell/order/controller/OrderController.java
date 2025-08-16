package com.suresell.order.controller;

import com.suresell.order.model.record.OrderRequestRecord;
import com.suresell.order.model.entity.Order;
import com.suresell.order.model.record.OrderResponseRecord;
import com.suresell.order.serivices.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderService orderService;

  @PostMapping("/create")
  public ResponseEntity<Map<String, String>> createOrder(@RequestBody OrderRequestRecord dto) {
    orderService.createOrUpdateOrder(dto);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("message", "Orden creada con éxito"));
  }

  @PutMapping("/{orderId}")
  public ResponseEntity<Map<String, String>> updateOrder(
          @PathVariable Long orderId,
          @RequestBody OrderRequestRecord dto) {
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
      @PathVariable Long orderId, @RequestParam String status) {
    orderService.updateStatus(orderId, status);
    return ResponseEntity.ok(Map.of("message", "Estado actualizado correctamente"));
  }

  @GetMapping("/{orderId}")
  public ResponseEntity<OrderResponseRecord> getOrderById(@PathVariable Long orderId) {
    OrderResponseRecord order = orderService.getOrderById(orderId);
    return ResponseEntity.ok(order);
  }

  @GetMapping("/report")
  public ResponseEntity<?> getSalesReport(@RequestParam(defaultValue = "json") String format) throws Exception {
    List<OrderResponseRecord> report = orderService.getSalesReport();
    if ("excel".equalsIgnoreCase(format)) {
      try (org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Ventas");
        int rowIdx = 0;
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(rowIdx++);
        header.createCell(0).setCellValue("ID Orden");
        header.createCell(1).setCellValue("Mesa");
        header.createCell(2).setCellValue("Fecha");
        header.createCell(3).setCellValue("Subtotal");
        header.createCell(4).setCellValue("Impuesto");
        header.createCell(5).setCellValue("Total");
        header.createCell(6).setCellValue("Estado");
        header.createCell(7).setCellValue("Productos");
        for (OrderResponseRecord order : report) {
          org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
          row.createCell(0).setCellValue(order.idOrder());
          row.createCell(1).setCellValue(order.tableNumber());
          row.createCell(2).setCellValue(order.createdAt().toString());
          row.createCell(3).setCellValue(order.subtotal());
          row.createCell(4).setCellValue(order.tax());
          row.createCell(5).setCellValue(order.total());
          row.createCell(6).setCellValue(order.status());
          StringBuilder productos = new StringBuilder();
          for (var item : order.items()) {
            productos.append(item.nameProduct())
              .append(" x")
              .append(item.quantity())
              .append(" ($")
              .append(item.unitPrice())
              .append(") | ");
          }
          row.createCell(7).setCellValue(productos.toString());
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        workbook.write(out);
        byte[] bytes = out.toByteArray();
        return ResponseEntity.ok()
          .header("Content-Disposition", "attachment; filename=ventas.xlsx")
          .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
          .body(bytes);
      }
    } else {
      return ResponseEntity.ok(report);
    }
  }
}
