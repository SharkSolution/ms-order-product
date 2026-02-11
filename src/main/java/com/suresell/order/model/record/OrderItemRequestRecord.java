package com.suresell.orders.application.dto;

public record OrderItemRequestRecord(
    String productId,
    int quantity,
    int unitPrice,
    String instructions,
    Integer comboGroup
) {}
