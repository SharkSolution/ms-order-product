/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.record.ClosureResponse
 */
package com.suresell.order.model.record;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
public record ClosureResponse(UUID id, String userName, LocalDateTime openingTime, LocalDateTime closingTime, BigDecimal totalExpectedCash, BigDecimal totalExpectedCard, BigDecimal totalExpectedNequi, BigDecimal totalExpectedQr, BigDecimal totalExpected, BigDecimal totalCountedCash, BigDecimal totalCountedCard, BigDecimal totalCountedNequi, BigDecimal totalCountedQr, BigDecimal totalCounted, BigDecimal differenceAmount, String status, String notes, String message) {

    public ClosureResponse(UUID id, String userName, LocalDateTime openingTime, LocalDateTime closingTime, BigDecimal totalExpectedCash, BigDecimal totalExpectedCard, BigDecimal totalExpectedNequi, BigDecimal totalExpectedQr, BigDecimal totalExpected, BigDecimal totalCountedCash, BigDecimal totalCountedCard, BigDecimal totalCountedNequi, BigDecimal totalCountedQr, BigDecimal totalCounted, BigDecimal differenceAmount, String status, String notes, String message) {
        this.id = id;
        this.userName = userName;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.totalExpectedCash = totalExpectedCash;
        this.totalExpectedCard = totalExpectedCard;
        this.totalExpectedNequi = totalExpectedNequi;
        this.totalExpectedQr = totalExpectedQr;
        this.totalExpected = totalExpected;
        this.totalCountedCash = totalCountedCash;
        this.totalCountedCard = totalCountedCard;
        this.totalCountedNequi = totalCountedNequi;
        this.totalCountedQr = totalCountedQr;
        this.totalCounted = totalCounted;
        this.differenceAmount = differenceAmount;
        this.status = status;
        this.notes = notes;
        this.message = message;
    }
    public UUID id() {
        return this.id;
    }
    public String userName() {
        return this.userName;
    }
    public LocalDateTime openingTime() {
        return this.openingTime;
    }
    public LocalDateTime closingTime() {
        return this.closingTime;
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
    public BigDecimal totalCountedCash() {
        return this.totalCountedCash;
    }
    public BigDecimal totalCountedCard() {
        return this.totalCountedCard;
    }
    public BigDecimal totalCountedNequi() {
        return this.totalCountedNequi;
    }
    public BigDecimal totalCountedQr() {
        return this.totalCountedQr;
    }
    public BigDecimal totalCounted() {
        return this.totalCounted;
    }
    public BigDecimal differenceAmount() {
        return this.differenceAmount;
    }
    public String status() {
        return this.status;
    }
    public String notes() {
        return this.notes;
    }
    public String message() {
        return this.message;
    }
}
