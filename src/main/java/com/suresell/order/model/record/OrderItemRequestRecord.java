package com.suresell.order.model.record;

public record OrderItemRequestRecord(
    String productId, int quantity, int unitPrice, String instructions) {}