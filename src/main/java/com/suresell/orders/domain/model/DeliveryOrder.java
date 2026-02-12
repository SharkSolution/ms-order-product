package com.suresell.orders.domain.model;

import com.suresell.orders.domain.model.DeliveryStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryOrder {
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
}
