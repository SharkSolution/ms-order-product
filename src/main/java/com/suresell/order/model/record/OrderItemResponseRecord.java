package com.suresell.order.model.record;

public record OrderItemResponseRecord(
    String productId,
    String nameProduct,
    int quantity,
    int unitPrice,
    int totalPrice,
    String instructions) {}
