/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.enums.PagerColor
 *  com.suresell.order.model.record.OrderItemResponseRecord
 *  com.suresell.order.model.record.OrderResponseRecord
 */
package com.suresell.order.model.record;
import com.suresell.order.model.enums.PagerColor;
import com.suresell.order.model.record.OrderItemResponseRecord;
import java.time.LocalDateTime;
import java.util.List;
public record OrderResponseRecord(Long idOrder, PagerColor pagerColor, Integer pagerNumber, LocalDateTime createdAt, int subtotal, int total, String status, String paymentMethod, String discountCode, Double discountPercentage, Integer discountAmount, String deliveredAt, List<OrderItemResponseRecord> items) {

    public OrderResponseRecord(Long idOrder, PagerColor pagerColor, Integer pagerNumber, LocalDateTime createdAt, int subtotal, int total, String status, String paymentMethod, String discountCode, Double discountPercentage, Integer discountAmount, String deliveredAt, List<OrderItemResponseRecord> items) {
        this.idOrder = idOrder;
        this.pagerColor = pagerColor;
        this.pagerNumber = pagerNumber;
        this.createdAt = createdAt;
        this.subtotal = subtotal;
        this.total = total;
        this.status = status;
        this.paymentMethod = paymentMethod;
        this.discountCode = discountCode;
        this.discountPercentage = discountPercentage;
        this.discountAmount = discountAmount;
        this.deliveredAt = deliveredAt;
        this.items = items;
    }
    public Long idOrder() {
        return this.idOrder;
    }
    public PagerColor pagerColor() {
        return this.pagerColor;
    }
    public Integer pagerNumber() {
        return this.pagerNumber;
    }
    public LocalDateTime createdAt() {
        return this.createdAt;
    }
    public int subtotal() {
        return this.subtotal;
    }
    public int total() {
        return this.total;
    }
    public String status() {
        return this.status;
    }
    public String paymentMethod() {
        return this.paymentMethod;
    }
    public String discountCode() {
        return this.discountCode;
    }
    public Double discountPercentage() {
        return this.discountPercentage;
    }
    public Integer discountAmount() {
        return this.discountAmount;
    }
    public String deliveredAt() {
        return this.deliveredAt;
    }
    public List<OrderItemResponseRecord> items() {
        return this.items;
    }
}
