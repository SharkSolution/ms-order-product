/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.record.OrderItemRequestRecord
 */
package com.suresell.order.model.record;
public record OrderItemRequestRecord(String productId, int quantity, int unitPrice, String instructions) {

    public OrderItemRequestRecord(String productId, int quantity, int unitPrice, String instructions) {
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.instructions = instructions;
    }
    public String productId() {
        return this.productId;
    }
    public int quantity() {
        return this.quantity;
    }
    public int unitPrice() {
        return this.unitPrice;
    }
    public String instructions() {
        return this.instructions;
    }
}
