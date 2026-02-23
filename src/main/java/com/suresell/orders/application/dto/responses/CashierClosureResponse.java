package com.suresell.orders.application.dto.responses;

import java.math.BigDecimal;
import java.util.Map;

public record CashierClosureResponse(
        String status,
        String message,
        Map<String, BigDecimal> shortages
) {}