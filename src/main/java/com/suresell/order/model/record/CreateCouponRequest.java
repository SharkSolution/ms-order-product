/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.record.CreateCouponRequest
 *  com.suresell.order.model.record.ProductDiscountDto
 */
package com.suresell.order.model.record;
import com.suresell.order.model.record.ProductDiscountDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
public record CreateCouponRequest(String adminPassword, String code, String name, String description, BigDecimal discountPercentage, List<ProductDiscountDto> products, LocalDate validFrom, LocalDate validTo, String validWeekdays, Boolean isActive) {

    public CreateCouponRequest(String adminPassword, String code, String name, String description, BigDecimal discountPercentage, List<ProductDiscountDto> products, LocalDate validFrom, LocalDate validTo, String validWeekdays, Boolean isActive) {
        this.adminPassword = adminPassword;
        this.code = code;
        this.name = name;
        this.description = description;
        this.discountPercentage = discountPercentage;
        this.products = products;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.validWeekdays = validWeekdays;
        this.isActive = isActive;
    }
    public String adminPassword() {
        return this.adminPassword;
    }
    public String code() {
        return this.code;
    }
    public String name() {
        return this.name;
    }
    public String description() {
        return this.description;
    }
    public BigDecimal discountPercentage() {
        return this.discountPercentage;
    }
    public List<ProductDiscountDto> products() {
        return this.products;
    }
    public LocalDate validFrom() {
        return this.validFrom;
    }
    public LocalDate validTo() {
        return this.validTo;
    }
    public String validWeekdays() {
        return this.validWeekdays;
    }
    public Boolean isActive() {
        return this.isActive;
    }
}
