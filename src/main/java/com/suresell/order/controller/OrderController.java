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
}
