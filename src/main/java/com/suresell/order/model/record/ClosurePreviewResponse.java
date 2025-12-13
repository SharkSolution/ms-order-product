/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.record.ClosurePreviewResponse
 */
package com.suresell.order.model.record;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record ClosurePreviewResponse(LocalDateTime openingTime, LocalDateTime currentTime, BigDecimal totalExpectedCash, BigDecimal totalExpectedCard, BigDecimal totalExpectedNequi, BigDecimal totalExpectedQr, BigDecimal totalExpected, int totalOrders, String message) {

    public ClosurePreviewResponse(LocalDateTime openingTime, LocalDateTime currentTime, BigDecimal totalExpectedCash, BigDecimal totalExpectedCard, BigDecimal totalExpectedNequi, BigDecimal totalExpectedQr, BigDecimal totalExpected, int totalOrders, String message) {
        this.openingTime = openingTime;
        this.currentTime = currentTime;
        this.totalExpectedCash = totalExpectedCash;
        this.totalExpectedCard = totalExpectedCard;
        this.totalExpectedNequi = totalExpectedNequi;
        this.totalExpectedQr = totalExpectedQr;
        this.totalExpected = totalExpected;
        this.totalOrders = totalOrders;
        this.message = message;
    }
    public LocalDateTime openingTime() {
        return this.openingTime;
    }
    public LocalDateTime currentTime() {
        return this.currentTime;
    }
    public BigDecimal totalExpectedCash() {
        return this.totalExpectedCash;
    }
    public BigDecimal totalExpectedCard() {
        return this.totalExpectedCard;
    }
    public BigDecimal totalExpectedNequi() {
        return this.totalExpectedNequi;
    }
    public BigDecimal totalExpectedQr() {
        return this.totalExpectedQr;
    }
    public BigDecimal totalExpected() {
        return this.totalExpected;
    }
    public int totalOrders() {
        return this.totalOrders;
    }
    public String message() {
        return this.message;
    }
}
