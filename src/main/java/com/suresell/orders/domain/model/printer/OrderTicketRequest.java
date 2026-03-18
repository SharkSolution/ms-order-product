package com.suresell.orders.domain.model.printer;

import java.util.List;

public record OrderTicketRequest(
        String orderNumber,
        String pagerColor,
        String pagerNumber,
        String dateTime,
        String generalNotes,
        List<OrderTicketItem> items
) {}

