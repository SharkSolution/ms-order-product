package com.suresell.order.model.record;

import java.util.List;

public record OrderRequestRecord(int tableNumber, List<OrderItemRequestRecord> items) {}