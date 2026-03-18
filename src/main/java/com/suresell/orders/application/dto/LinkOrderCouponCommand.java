package com.suresell.orders.application.dto;
import java.math.BigDecimal;
public record LinkOrderCouponCommand(Long orderId, String code, BigDecimal subtotalBeforeDiscount, BigDecimal discountAmount, BigDecimal totalAfterDiscount) {
}
