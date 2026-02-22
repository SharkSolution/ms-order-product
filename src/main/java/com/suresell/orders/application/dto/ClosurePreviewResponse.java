package com.suresell.orders.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ClosurePreviewResponse(
        LocalDateTime openingTime,
        LocalDateTime currentTime,
        int totalOrders,
        BigDecimal totalExpectedCash,
        BigDecimal totalExpectedCard,
        BigDecimal totalExpectedNequi,
        BigDecimal totalExpectedQr,
        BigDecimal totalExpected,
        BigDecimal previousBaseBalance,
        String message) {
}
