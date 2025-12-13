/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.record.ProductDiscountDto
 */
package com.suresell.order.model.record;

public record ProductDiscountDto(String productId, String productName, Double discountPercentage) {
    private final String productId;
    private final String productName;
    private final Double discountPercentage;

    public ProductDiscountDto(String productId, String productName, Double discountPercentage) {
        this.productId = productId;
        this.productName = productName;
        this.discountPercentage = discountPercentage;
    }

    public String productId() {
        return this.productId;
    }

    public String productName() {
        return this.productName;
    }

    public Double discountPercentage() {
        return this.discountPercentage;
    }
}

