/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.record.LinkOrderCouponCommand
 */
package com.suresell.order.model.record;
import java.math.BigDecimal;
public record LinkOrderCouponCommand(Long orderId, String code, BigDecimal subtotalBeforeDiscount, BigDecimal discountAmount, BigDecimal totalAfterDiscount) {

    public LinkOrderCouponCommand(Long orderId, String code, BigDecimal subtotalBeforeDiscount, BigDecimal discountAmount, BigDecimal totalAfterDiscount) {
        this.orderId = orderId;
        this.code = code;
        this.subtotalBeforeDiscount = subtotalBeforeDiscount;
        this.discountAmount = discountAmount;
        this.totalAfterDiscount = totalAfterDiscount;
    }
    public Long orderId() {
        return this.orderId;
    }
    public String code() {
        return this.code;
    }
    public BigDecimal subtotalBeforeDiscount() {
        return this.subtotalBeforeDiscount;
    }
    public BigDecimal discountAmount() {
        return this.discountAmount;
    }
    public BigDecimal totalAfterDiscount() {
        return this.totalAfterDiscount;
    }
}
