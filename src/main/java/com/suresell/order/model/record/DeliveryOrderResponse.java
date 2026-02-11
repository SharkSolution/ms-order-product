package com.suresell.orders.application.dto;

import com.suresell.orders.domain.model.DeliveryStatus;
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
}
