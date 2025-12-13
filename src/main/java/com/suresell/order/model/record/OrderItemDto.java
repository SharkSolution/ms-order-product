/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.record.OrderItemDto
 */
package com.suresell.order.model.record;

import java.math.BigDecimal;

public record OrderItemDto(String productId, Long productIdLong, String productName, String categoryName, Integer quantity, BigDecimal unitPrice) {
    private final String productId;
    private final Long productIdLong;
    private final String productName;
    private final String categoryName;
    private final Integer quantity;
    private final BigDecimal unitPrice;

    public OrderItemDto(String productId, Long productIdLong, String productName, String categoryName, Integer quantity, BigDecimal unitPrice) {
        this.productId = productId;
        this.productIdLong = productIdLong;
        this.productName = productName;
        this.categoryName = categoryName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public String productId() {
        return this.productId;
    }

    public Long productIdLong() {
        return this.productIdLong;
    }

    public String productName() {
        return this.productName;
    }

    public String categoryName() {
        return this.categoryName;
    }

    public Integer quantity() {
        return this.quantity;
    }

    public BigDecimal unitPrice() {
        return this.unitPrice;
    }
}

