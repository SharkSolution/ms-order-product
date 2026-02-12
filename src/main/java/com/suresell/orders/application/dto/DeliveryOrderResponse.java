package com.suresell.orders.application.dto;

import com.suresell.orders.domain.model.DeliveryStatus;
import com.suresell.orders.domain.model.DeliveryOrder;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryOrderResponse {
    private Long id;
    private Integer orderId;
    private String customerName;
    private String building;
    private String deliveryNotes;
    private String orderSummary;
    private String phone;
    private String paymentMethod;
    private DeliveryStatus deliveryStatus;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DeliveryOrderResponse fromEntity(DeliveryOrder deliveryOrder) {
        return DeliveryOrderResponse.builder()
                .id(deliveryOrder.getId())
                .orderId(deliveryOrder.getOrderId())
                .customerName(deliveryOrder.getCustomerName())
                .building(deliveryOrder.getBuilding())
                .deliveryNotes(deliveryOrder.getDeliveryNotes())
                .orderSummary(deliveryOrder.getOrderSummary())
                .phone(deliveryOrder.getPhone())
                .paymentMethod(deliveryOrder.getPaymentMethod())
                .deliveryStatus(deliveryOrder.getDeliveryStatus())
                .deliveredAt(deliveryOrder.getDeliveredAt())
                .createdAt(deliveryOrder.getCreatedAt())
                .updatedAt(deliveryOrder.getUpdatedAt()) // Added updatedAt here
                .build();
    }
}
