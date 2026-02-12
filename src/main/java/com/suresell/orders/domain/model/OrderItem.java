package com.suresell.orders.domain.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    private Long idOrderItem;
    private Order order;
    private String productId;
    private int quantity;
    private int unitPrice;
    private int totalPrice;
    private String instructions;
    private Integer comboGroup;
}
