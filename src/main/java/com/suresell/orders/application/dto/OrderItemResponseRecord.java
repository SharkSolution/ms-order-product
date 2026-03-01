package com.suresell.orders.application.dto;
public record OrderItemResponseRecord(
    String productId,
    String nameProduct,
    int quantity,
    java.math.BigDecimal unitPrice,
    java.math.BigDecimal totalPrice,
    String instructions,
    Integer comboGroup
) {}
