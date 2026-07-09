package com.suresell.orders.domain.model;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Entity
@Table(name = "coupon_product")
@Data
@NoArgsConstructor
@AllArgsConstructor
@jakarta.persistence.EntityListeners(com.suresell.orders.multitenant.TenantEntityListener.class)
public class CouponProduct implements com.suresell.orders.multitenant.TenantOwned {
    @Column(name = "tenant_id")
    private String tenantId;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private DiscountCoupon coupon;
    @Column(name = "product_id")
    private String productId;
    @Column(name = "product_name")
    private String productName;
}
