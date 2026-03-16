package com.suresell.orders.domain.model.printer;

public record OrderTicketItem(
        String name,
        int quantity,
        String notes
) {}
