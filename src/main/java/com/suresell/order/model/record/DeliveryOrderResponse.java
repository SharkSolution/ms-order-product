package com.suresell.order.model.record;

import com.suresell.order.model.enums.DeliveryStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
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

    // Método estático para facilitar la conversión desde la entidad
    public static DeliveryOrderResponse fromEntity(com.suresell.order.model.entity.DeliveryOrder entity) {
        return DeliveryOrderResponse.builder()
                .id(entity.getId())
                .orderId(entity.getOrderId())
                .customerName(entity.getCustomerName())
                .building(entity.getBuilding())
                .deliveryNotes(entity.getDeliveryNotes())
                .orderSummary(entity.getOrderSummary())
                .phone(entity.getPhone())
                .paymentMethod(entity.getPaymentMethod())
                .deliveryStatus(entity.getDeliveryStatus())
                .deliveredAt(entity.getDeliveredAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
