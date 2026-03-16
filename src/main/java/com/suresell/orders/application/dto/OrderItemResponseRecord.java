package com.suresell.orders.application.dto;

import java.math.BigDecimal;

public record OrderItemResponseRecord(
    String productId,
    String nameProduct,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal totalPrice,
    String instructions,
    Integer comboGroup
) {}
