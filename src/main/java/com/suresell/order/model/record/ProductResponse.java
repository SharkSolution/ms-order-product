/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.record.ProductResponse
 */
package com.suresell.order.model.record;

public record ProductResponse(String idProduct, String nameProduct, String categoryName) {
    private final String idProduct;
    private final String nameProduct;
    private final String categoryName;

    public ProductResponse(String idProduct, String nameProduct, String categoryName) {
        this.idProduct = idProduct;
        this.nameProduct = nameProduct;
        this.categoryName = categoryName;
    }

    public String idProduct() {
        return this.idProduct;
    }

    public String nameProduct() {
        return this.nameProduct;
    }

    public String categoryName() {
        return this.categoryName;
    }
}

