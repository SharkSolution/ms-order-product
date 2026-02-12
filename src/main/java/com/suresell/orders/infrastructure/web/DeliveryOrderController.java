package com.suresell.orders.infrastructure.web;

import com.suresell.orders.application.dto.CreateDeliveryOrderRequest;
import com.suresell.orders.application.dto.DeliveryOrderResponse;
import com.suresell.orders.domain.port.in.DeliveryPort; // Renamed from DeliveryOrderService
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/delivery-orders")
public class DeliveryOrderController {

    private final DeliveryPort deliveryPort;

    public DeliveryOrderController(DeliveryPort deliveryPort) {
        this.deliveryPort = deliveryPort;
    }

    @PostMapping
    public ResponseEntity<DeliveryOrderResponse> createDeliveryOrder(@Valid @RequestBody CreateDeliveryOrderRequest request) {
        com.suresell.orders.domain.model.DeliveryOrder createdOrder = deliveryPort.createDeliveryOrder(request);
        return new ResponseEntity<>(DeliveryOrderResponse.fromEntity(createdOrder), HttpStatus.CREATED);
    }

    @GetMapping("/pending")
    public ResponseEntity<List<DeliveryOrderResponse>> getPendingOrders() {
        List<com.suresell.orders.domain.model.DeliveryOrder> pendingOrders = deliveryPort.findPendingOrders();
        List<DeliveryOrderResponse> response = pendingOrders.stream()
                .map(DeliveryOrderResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{orderId}/delivered")
    public ResponseEntity<DeliveryOrderResponse> markAsDelivered(@PathVariable Integer orderId) {
        com.suresell.orders.domain.model.DeliveryOrder updatedOrder = deliveryPort.markAsDelivered(orderId);
        return ResponseEntity.ok(DeliveryOrderResponse.fromEntity(updatedOrder));
    }
}
