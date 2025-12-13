/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.entity.CouponProduct
 *  com.suresell.order.model.entity.DiscountCoupon
 *  jakarta.persistence.Entity
 *  jakarta.persistence.GeneratedValue
 *  jakarta.persistence.GenerationType
 *  jakarta.persistence.Id
 *  jakarta.persistence.JoinColumn
 *  jakarta.persistence.ManyToOne
 *  lombok.Generated
 */
package com.suresell.order.model.entity;

import com.suresell.order.model.entity.DiscountCoupon;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Generated;

@Entity
public class CouponProduct {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name="discount_coupon_id", nullable=false)
    private DiscountCoupon coupon;
    private String productId;
    private String productName;

    @Generated
    public Long getId() {
        return this.id;
    }

    @Generated
    public DiscountCoupon getCoupon() {
        return this.coupon;
    }

    @Generated
    public String getProductId() {
        return this.productId;
    }

    @Generated
    public String getProductName() {
        return this.productName;
    }

    @Generated
    public void setId(Long id) {
        this.id = id;
    }

    @Generated
    public void setCoupon(DiscountCoupon coupon) {
        this.coupon = coupon;
    }

    @Generated
    public void setProductId(String productId) {
        this.productId = productId;
    }

    @Generated
    public void setProductName(String productName) {
        this.productName = productName;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof CouponProduct)) {
            return false;
        }
        CouponProduct other = (CouponProduct)o;
        if (!other.canEqual((Object)this)) {
            return false;
        }
        Long this$id = this.getId();
        Long other$id = other.getId();
        if (this$id == null ? other$id != null : !((Object)this$id).equals(other$id)) {
            return false;
        }
        DiscountCoupon this$coupon = this.getCoupon();
        DiscountCoupon other$coupon = other.getCoupon();
        if (this$coupon == null ? other$coupon != null : !this$coupon.equals(other$coupon)) {
            return false;
        }
        String this$productId = this.getProductId();
        String other$productId = other.getProductId();
        if (this$productId == null ? other$productId != null : !this$productId.equals(other$productId)) {
            return false;
        }
        String this$productName = this.getProductName();
        String other$productName = other.getProductName();
        return !(this$productName == null ? other$productName != null : !this$productName.equals(other$productName));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof CouponProduct;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        DiscountCoupon $coupon = this.getCoupon();
        result = result * 59 + ($coupon == null ? 43 : $coupon.hashCode());
        String $productId = this.getProductId();
        result = result * 59 + ($productId == null ? 43 : $productId.hashCode());
        String $productName = this.getProductName();
        result = result * 59 + ($productName == null ? 43 : $productName.hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "CouponProduct(id=" + this.getId() + ", coupon=" + String.valueOf(this.getCoupon()) + ", productId=" + this.getProductId() + ", productName=" + this.getProductName() + ")";
    }

    @Generated
    public CouponProduct() {
    }

    @Generated
    public CouponProduct(Long id, DiscountCoupon coupon, String productId, String productName) {
        this.id = id;
        this.coupon = coupon;
        this.productId = productId;
        this.productName = productName;
    }
}

