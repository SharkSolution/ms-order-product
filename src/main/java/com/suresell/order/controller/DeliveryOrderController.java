package com.suresell.order.controller;

import com.suresell.order.model.record.CreateDeliveryOrderRequest;
import com.suresell.order.model.record.DeliveryOrderResponse;
import com.suresell.order.serivices.DeliveryOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/delivery-orders")
@RequiredArgsConstructor
public class DeliveryOrderController {

    private final DeliveryOrderService deliveryOrderService;

    @PostMapping
    public ResponseEntity<DeliveryOrderResponse> createDeliveryOrder(@Valid @RequestBody CreateDeliveryOrderRequest request) {
        com.suresell.order.model.entity.DeliveryOrder createdOrder = deliveryOrderService.createDeliveryOrder(request);
        return new ResponseEntity<>(DeliveryOrderResponse.fromEntity(createdOrder), HttpStatus.CREATED);
    }

    @GetMapping("/pending")
    public ResponseEntity<List<DeliveryOrderResponse>> getPendingOrders() {
        List<com.suresell.order.model.entity.DeliveryOrder> pendingOrders = deliveryOrderService.findPendingOrders();
        List<DeliveryOrderResponse> response = pendingOrders.stream()
                .map(DeliveryOrderResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{orderId}/delivered")
    public ResponseEntity<DeliveryOrderResponse> markAsDelivered(@PathVariable Integer orderId) {
        com.suresell.order.model.entity.DeliveryOrder updatedOrder = deliveryOrderService.markAsDelivered(orderId);
        return ResponseEntity.ok(DeliveryOrderResponse.fromEntity(updatedOrder));
    }
}
