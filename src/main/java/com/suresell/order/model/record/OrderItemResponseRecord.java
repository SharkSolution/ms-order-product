package com.suresell.orders.application.dto;

public record OrderItemResponseRecord(
    String productId,
    String nameProduct,
    int quantity,
    int unitPrice,
    int totalPrice,
    String instructions,
    Integer comboGroup
) {}
