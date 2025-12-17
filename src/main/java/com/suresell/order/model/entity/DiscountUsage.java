/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.entity.DiscountCoupon
 *  com.suresell.order.model.entity.DiscountUsage
 *  jakarta.persistence.Column
 *  jakarta.persistence.Entity
 *  jakarta.persistence.FetchType
 *  jakarta.persistence.GeneratedValue
 *  jakarta.persistence.GenerationType
 *  jakarta.persistence.Id
 *  jakarta.persistence.Index
 *  jakarta.persistence.JoinColumn
 *  jakarta.persistence.ManyToOne
 *  jakarta.persistence.PrePersist
 *  jakarta.persistence.Table
 *  jakarta.persistence.UniqueConstraint
 *  lombok.Generated
 */
package com.suresell.order.model.entity;
import com.suresell.order.model.entity.DiscountCoupon;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.Generated;
@Entity
@Table(name="discount_usages", uniqueConstraints={@UniqueConstraint(name="uk_order_coupon", columnNames={"order_id", "coupon_id"})}, indexes={@Index(name="idx_usage_order", columnList="order_id"), @Index(name="idx_usage_coupon", columnList="coupon_id"), @Index(name="idx_usage_created", columnList="created_at")})
public class DiscountUsage {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="order_id", nullable=false)
    private Long orderId;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="coupon_id", nullable=false)
    private DiscountCoupon coupon;
    @Column(name="discount_code", nullable=false, length=50)
    private String discountCode;
    @Column(name="subtotal_before_discount", precision=10, scale=2, nullable=false)
    private BigDecimal subtotalBeforeDiscount;
    @Column(name="discount_amount", precision=10, scale=2, nullable=false)
    private BigDecimal discountAmount;
    @Column(name="total_after_discount", precision=10, scale=2, nullable=false)
    private BigDecimal totalAfterDiscount;
    @Column(name="created_at", nullable=false)
    private LocalDateTime createdAt;
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    
    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now(BOGOTA_ZONE);
        }
    }
    @Generated
    public Long getId() {
        return this.id;
    }
    @Generated
    public Long getOrderId() {
        return this.orderId;
    }
    @Generated
    public DiscountCoupon getCoupon() {
        return this.coupon;
    }
    @Generated
    public String getDiscountCode() {
        return this.discountCode;
    }
    @Generated
    public BigDecimal getSubtotalBeforeDiscount() {
        return this.subtotalBeforeDiscount;
    }
    @Generated
    public BigDecimal getDiscountAmount() {
        return this.discountAmount;
    }
    @Generated
    public BigDecimal getTotalAfterDiscount() {
        return this.totalAfterDiscount;
    }
    @Generated
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }
    @Generated
    public void setId(Long id) {
        this.id = id;
    }
    @Generated
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    @Generated
    public void setCoupon(DiscountCoupon coupon) {
        this.coupon = coupon;
    }
    @Generated
    public void setDiscountCode(String discountCode) {
        this.discountCode = discountCode;
    }
    @Generated
    public void setSubtotalBeforeDiscount(BigDecimal subtotalBeforeDiscount) {
        this.subtotalBeforeDiscount = subtotalBeforeDiscount;
    }
    @Generated
    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }
    @Generated
    public void setTotalAfterDiscount(BigDecimal totalAfterDiscount) {
        this.totalAfterDiscount = totalAfterDiscount;
    }
    @Generated
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof DiscountUsage)) {
            return false;
        }
        DiscountUsage other = (DiscountUsage)o;
        if (!other.canEqual((Object)this)) {
            return false;
        }
        Long this$id = this.getId();
        Long other$id = other.getId();
        if (this$id == null ? other$id != null : !((Object)this$id).equals(other$id)) {
            return false;
        }
        Long this$orderId = this.getOrderId();
        Long other$orderId = other.getOrderId();
        if (this$orderId == null ? other$orderId != null : !((Object)this$orderId).equals(other$orderId)) {
            return false;
        }
        DiscountCoupon this$coupon = this.getCoupon();
        DiscountCoupon other$coupon = other.getCoupon();
        if (this$coupon == null ? other$coupon != null : !this$coupon.equals(other$coupon)) {
            return false;
        }
        String this$discountCode = this.getDiscountCode();
        String other$discountCode = other.getDiscountCode();
        if (this$discountCode == null ? other$discountCode != null : !this$discountCode.equals(other$discountCode)) {
            return false;
        }
        BigDecimal this$subtotalBeforeDiscount = this.getSubtotalBeforeDiscount();
        BigDecimal other$subtotalBeforeDiscount = other.getSubtotalBeforeDiscount();
        if (this$subtotalBeforeDiscount == null ? other$subtotalBeforeDiscount != null : !((Object)this$subtotalBeforeDiscount).equals(other$subtotalBeforeDiscount)) {
            return false;
        }
        BigDecimal this$discountAmount = this.getDiscountAmount();
        BigDecimal other$discountAmount = other.getDiscountAmount();
        if (this$discountAmount == null ? other$discountAmount != null : !((Object)this$discountAmount).equals(other$discountAmount)) {
            return false;
        }
        BigDecimal this$totalAfterDiscount = this.getTotalAfterDiscount();
        BigDecimal other$totalAfterDiscount = other.getTotalAfterDiscount();
        if (this$totalAfterDiscount == null ? other$totalAfterDiscount != null : !((Object)this$totalAfterDiscount).equals(other$totalAfterDiscount)) {
            return false;
        }
        LocalDateTime this$createdAt = this.getCreatedAt();
        LocalDateTime other$createdAt = other.getCreatedAt();
        return !(this$createdAt == null ? other$createdAt != null : !((Object)this$createdAt).equals(other$createdAt));
    }
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof DiscountUsage;
    }
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        Long $orderId = this.getOrderId();
        result = result * 59 + ($orderId == null ? 43 : ((Object)$orderId).hashCode());
        DiscountCoupon $coupon = this.getCoupon();
        result = result * 59 + ($coupon == null ? 43 : $coupon.hashCode());
        String $discountCode = this.getDiscountCode();
        result = result * 59 + ($discountCode == null ? 43 : $discountCode.hashCode());
        BigDecimal $subtotalBeforeDiscount = this.getSubtotalBeforeDiscount();
        result = result * 59 + ($subtotalBeforeDiscount == null ? 43 : ((Object)$subtotalBeforeDiscount).hashCode());
        BigDecimal $discountAmount = this.getDiscountAmount();
        result = result * 59 + ($discountAmount == null ? 43 : ((Object)$discountAmount).hashCode());
        BigDecimal $totalAfterDiscount = this.getTotalAfterDiscount();
        result = result * 59 + ($totalAfterDiscount == null ? 43 : ((Object)$totalAfterDiscount).hashCode());
        LocalDateTime $createdAt = this.getCreatedAt();
        result = result * 59 + ($createdAt == null ? 43 : ((Object)$createdAt).hashCode());
        return result;
    }
    @Generated
    public String toString() {
        return "DiscountUsage(id=" + this.getId() + ", orderId=" + this.getOrderId() + ", coupon=" + String.valueOf(this.getCoupon()) + ", discountCode=" + this.getDiscountCode() + ", subtotalBeforeDiscount=" + String.valueOf(this.getSubtotalBeforeDiscount()) + ", discountAmount=" + String.valueOf(this.getDiscountAmount()) + ", totalAfterDiscount=" + String.valueOf(this.getTotalAfterDiscount()) + ", createdAt=" + String.valueOf(this.getCreatedAt()) + ")";
    }
    @Generated
    public DiscountUsage() {
    }
    @Generated
    public DiscountUsage(Long id, Long orderId, DiscountCoupon coupon, String discountCode, BigDecimal subtotalBeforeDiscount, BigDecimal discountAmount, BigDecimal totalAfterDiscount, LocalDateTime createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.coupon = coupon;
        this.discountCode = discountCode;
        this.subtotalBeforeDiscount = subtotalBeforeDiscount;
        this.discountAmount = discountAmount;
        this.totalAfterDiscount = totalAfterDiscount;
        this.createdAt = createdAt;
    }
}
