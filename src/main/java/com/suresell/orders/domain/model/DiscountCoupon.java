package com.suresell.orders.domain.model;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
@Entity
@Table(name = "discount_coupon")
@Data
@NoArgsConstructor
@AllArgsConstructor
@jakarta.persistence.EntityListeners(com.suresell.orders.multitenant.TenantEntityListener.class)
public class DiscountCoupon implements com.suresell.orders.multitenant.TenantOwned {
    @Column(name = "tenant_id")
    private String tenantId;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String code;
    private String name;
    private String description;
    @Column(name = "discount_percentage")
    private BigDecimal discountPercentage;
    @OneToMany(mappedBy = "coupon", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CouponProduct> products = new ArrayList<>();
    @Column(name = "valid_from")
    private LocalDate validFrom;
    @Column(name = "valid_to")
    private LocalDate validTo;
    @Column(name = "valid_weekdays")
    private String validWeekdays;
    @Column(name = "is_active")
    private Boolean isActive = true;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
}
