package com.suresell.orders.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WaiterClosurePreviewResponse(
    String waiterId,
    LocalDateTime previewTime,
    BigDecimal totalExpectedCash,
    BigDecimal totalExpectedCard,
    BigDecimal totalExpectedQr,
    BigDecimal totalExpected,
    BigDecimal lastBaseCash,
    String message
) {}
