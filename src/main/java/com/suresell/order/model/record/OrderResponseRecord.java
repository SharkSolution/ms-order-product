package com.suresell.order.model.record;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponseRecord(
    Long idOrder,
    int tableNumber,
    LocalDateTime createdAt,
    int subtotal,
    int tax,
    int total,
    String status,
    List<OrderItemResponseRecord> items) {}
