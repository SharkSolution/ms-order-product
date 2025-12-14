/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.record.ApplyDiscountResult
 */
package com.suresell.order.model.record;
import java.math.BigDecimal;
import java.util.List;
public record ApplyDiscountResult(Boolean valid, String discountCode, BigDecimal discountPercentage, BigDecimal discountAmount, BigDecimal newSubtotal, String message, List<String> appliedProductIds) {

    public ApplyDiscountResult(Boolean valid, String discountCode, BigDecimal discountPercentage, BigDecimal discountAmount, BigDecimal newSubtotal, String message, List<String> appliedProductIds) {
        this.valid = valid;
        this.discountCode = discountCode;
        this.discountPercentage = discountPercentage;
        this.discountAmount = discountAmount;
        this.newSubtotal = newSubtotal;
        this.message = message;
        this.appliedProductIds = appliedProductIds;
    }
    public Boolean valid() {
        return this.valid;
    }
    public String discountCode() {
        return this.discountCode;
    }
    public BigDecimal discountPercentage() {
        return this.discountPercentage;
    }
    public BigDecimal discountAmount() {
        return this.discountAmount;
    }
    public BigDecimal newSubtotal() {
        return this.newSubtotal;
    }
    public String message() {
        return this.message;
    }
    public List<String> appliedProductIds() {
        return this.appliedProductIds;
    }
}
