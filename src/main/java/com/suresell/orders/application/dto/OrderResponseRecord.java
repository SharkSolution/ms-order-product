package com.suresell.orders.application.dto;

import com.suresell.orders.application.dto.OrderItemResponseRecord;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponseRecord(Long idOrder, String pagerColor, String pagerNumber, LocalDateTime createdAt, BigDecimal subtotal, BigDecimal total, String status, String paymentMethod, String discountCode, BigDecimal discountPercentage, BigDecimal discountAmount, LocalDateTime deliveredAt, Boolean delivered, Integer elapsedSecondsToDeliver, List<OrderItemResponseRecord> items) {
}
