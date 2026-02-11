package com.suresell.orders.domain.model;

import com.suresell.orders.domain.model.OrderItem;
import com.suresell.orders.domain.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private Long idOrder;
    private String pagerColor;
    private String pagerNumber;
    private LocalDateTime createdAt;
    private String deliveredAt;
    private int subtotal;
    private int total;
    private OrderStatus status;
    private String paymentMethod;
    private String discountCode;
    private Double discountPercentage;
    private Integer discountAmount;
    private Integer elapsedSecondsToDeliver;
    private String idempotencyKey;
    private List<OrderItem> items;
}
