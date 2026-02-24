package com.suresell.orders.application.dto.request;

import java.math.BigDecimal;

public record ExecuteClosureRequest(
        BigDecimal countedCash,
        BigDecimal countedCard,
        BigDecimal countedNequi,
        BigDecimal countedQr,
        BigDecimal baseBalanceForNextDay,
        String notes,
        String sellerId
) {}
