package com.suresell.orders.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ClosureResponse(UUID id, String userName, LocalDateTime openingTime, LocalDateTime closingTime, BigDecimal totalExpectedCash, BigDecimal totalExpectedCard, BigDecimal totalExpectedNequi, BigDecimal totalExpectedQr, BigDecimal totalExpected, BigDecimal totalCountedCash, BigDecimal totalCountedCard, BigDecimal totalCountedNequi, BigDecimal totalCountedQr, BigDecimal totalCounted, BigDecimal differenceAmount, String status, String notes, String message, BigDecimal previousBaseBalance) {
}
