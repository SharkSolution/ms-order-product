package com.suresell.orders.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyClosure {
    private UUID id;
    private String userName;
    private LocalDateTime openingTime;
    private LocalDateTime closingTime;
    private BigDecimal totalExpectedCash;
    private BigDecimal totalExpectedCard;
    private BigDecimal totalExpectedNequi;
    private BigDecimal totalExpectedQr;
    private BigDecimal totalCountedCash;
    private BigDecimal totalCountedCard;
    private BigDecimal totalCountedNequi;
    private BigDecimal totalCountedQr;
    private BigDecimal differenceAmount;
    private String status;
    private String notes;
    private BigDecimal baseBalanceForNextDay;
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
}
