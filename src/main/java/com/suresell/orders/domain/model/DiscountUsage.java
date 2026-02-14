package com.suresell.orders.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "discount_usage")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscountUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private DiscountCoupon coupon;

    @Column(name = "discount_code")
    private String discountCode;
    @Column(name = "subtotal_before_discount")
    private BigDecimal subtotalBeforeDiscount;
    @Column(name = "discount_amount")
    private BigDecimal discountAmount;
    @Column(name = "total_after_discount")
    private BigDecimal totalAfterDiscount;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
}
