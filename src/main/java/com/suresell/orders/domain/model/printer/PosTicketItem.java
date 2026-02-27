package com.suresell.orders.domain.model.printer;

import java.math.BigDecimal;

public record PosTicketItem(
        String name,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal total
) {}
