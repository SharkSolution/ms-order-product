package com.suresell.orders.application.dto;

import com.suresell.orders.application.dto.ProductDiscountDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UpdateCouponRequest(String code, String name, String description, BigDecimal discountPercentage, List<ProductDiscountDto> products, LocalDate validFrom, LocalDate validTo, String validWeekdays, Boolean isActive) {
}
