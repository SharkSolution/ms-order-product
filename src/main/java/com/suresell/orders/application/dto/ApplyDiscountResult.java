package com.suresell.orders.application.dto;
import java.math.BigDecimal;
import java.util.List;
public record ApplyDiscountResult(Boolean valid, String discountCode, BigDecimal discountPercentage, BigDecimal discountAmount, BigDecimal newSubtotal, String message, List<String> appliedProductIds) {
}
