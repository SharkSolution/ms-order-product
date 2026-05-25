package com.suresell.orders.application.dto.request;

import java.math.BigDecimal;

public record PettyCashExpenseRequest(
        String concept,
        BigDecimal amount
) {
}
