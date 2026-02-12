package com.suresell.orders.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscountCoupon {
    private Long id;
    private String code;
    private String name;
    private String description;
    private BigDecimal discountPercentage;
    private List<CouponProduct> products = new ArrayList<>();
    private LocalDate validFrom;
    private LocalDate validTo;
    private String validWeekdays;
    private Boolean isActive = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
}
