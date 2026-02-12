package com.suresell.orders.application.dto;

import java.math.BigDecimal;

public record OrderItemDto(String productId, Long productIdLong, String productName, String categoryName, Integer quantity, BigDecimal unitPrice) {
}
