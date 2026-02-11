package com.suresell.orders.application.dto;

import com.suresell.orders.application.dto.OrderItemDto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ApplyDiscountCommand(String code, LocalDateTime orderDateTime, List<OrderItemDto> items, BigDecimal subtotal) {
}
