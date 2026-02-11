package com.suresell.orders.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscountUsage {
    private Long id;
    private Long orderId;
    private DiscountCoupon coupon;
    private String discountCode;
    private BigDecimal subtotalBeforeDiscount;
    private BigDecimal discountAmount;
    private BigDecimal totalAfterDiscount;
    private LocalDateTime createdAt;
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
}
