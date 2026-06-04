package com.suresell.orders.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record WaiterClosureResponse(
    UUID id,
    String waiterId,
    String waiterName,
    LocalDateTime closedAt,
    BigDecimal baseCash,
    BigDecimal totalExpectedCash,
    BigDecimal totalExpectedCard,
    BigDecimal totalExpectedQr,
    BigDecimal totalCountedCash,
    BigDecimal totalCountedCard,
    BigDecimal totalCountedQr,
    BigDecimal differenceCash,
    BigDecimal differenceCard,
    BigDecimal differenceQr,
    BigDecimal totalDifference,
    String status,
    String notes,
    String message
) {}
