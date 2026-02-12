package com.suresell.orders.application.dto;

import com.suresell.orders.application.dto.OrderItemResponseRecord;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponseRecord(Long idOrder, String pagerColor, String pagerNumber, LocalDateTime createdAt, int subtotal, int total, String status, String paymentMethod, String discountCode, Double discountPercentage, Integer discountAmount, String deliveredAt, Integer elapsedSecondsToDeliver, List<OrderItemResponseRecord> items) {
}
