/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.record.ApplyDiscountCommand
 *  com.suresell.order.model.record.OrderItemDto
 */
package com.suresell.order.model.record;
import com.suresell.order.model.record.OrderItemDto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
public record ApplyDiscountCommand(String code, LocalDateTime orderDateTime, List<OrderItemDto> items, BigDecimal subtotal) {

    public ApplyDiscountCommand(String code, LocalDateTime orderDateTime, List<OrderItemDto> items, BigDecimal subtotal) {
        this.code = code;
        this.orderDateTime = orderDateTime;
        this.items = items;
        this.subtotal = subtotal;
    }
    public String code() {
        return this.code;
    }
    public LocalDateTime orderDateTime() {
        return this.orderDateTime;
    }
    public List<OrderItemDto> items() {
        return this.items;
    }
    public BigDecimal subtotal() {
        return this.subtotal;
    }
}
