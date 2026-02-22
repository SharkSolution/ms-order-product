package com.suresell.orders.application.dto;

public record OrderItemRequestRecord(
    String productId,
    int quantity,
    java.math.BigDecimal unitPrice,
    String instructions,
    Integer comboGroup
) {}
