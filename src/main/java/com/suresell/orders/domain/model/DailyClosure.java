package com.suresell.orders.domain.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "daily_closure")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyClosure {
    @Transient
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    @Column(name = "user_name")
    private String userName;
    @Column(name = "opening_time")
    private LocalDateTime openingTime;
    @Column(name = "closing_time")
    private LocalDateTime closingTime;
    @Column(name = "total_expected_cash")
    private BigDecimal totalExpectedCash;
    @Column(name = "total_expected_card")
    private BigDecimal totalExpectedCard;
    @Column(name = "total_expected_nequi")
    private BigDecimal totalExpectedNequi;
    @Column(name = "total_expected_qr")
    private BigDecimal totalExpectedQr;
    @Column(name = "total_counted_cash")
    private BigDecimal totalCountedCash;
    @Column(name = "total_counted_card")
    private BigDecimal totalCountedCard;
    @Column(name = "total_counted_nequi")
    private BigDecimal totalCountedNequi;
    @Column(name = "total_counted_qr")
    private BigDecimal totalCountedQr;
    @Column(name = "cash_count_audit", columnDefinition = "TEXT")
    private String cashCountAudit;
    @Column(name = "difference_amount")
    private BigDecimal differenceAmount;
    private String status;
    private String notes;
    @Column(name = "base_balance_for_next_day")
    private BigDecimal baseBalanceForNextDay;
}
