/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.record.OrderItemResponseRecord
 */
package com.suresell.order.model.record;
public record OrderItemResponseRecord(String productId, String nameProduct, int quantity, int unitPrice, int totalPrice, String instructions) {

    public OrderItemResponseRecord(String productId, String nameProduct, int quantity, int unitPrice, int totalPrice, String instructions) {
        this.productId = productId;
        this.nameProduct = nameProduct;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.instructions = instructions;
    }
    public String productId() {
        return this.productId;
    }
    public String nameProduct() {
        return this.nameProduct;
    }
    public int quantity() {
        return this.quantity;
    }
    public int unitPrice() {
        return this.unitPrice;
    }
    public int totalPrice() {
        return this.totalPrice;
    }
    public String instructions() {
        return this.instructions;
    }
}
