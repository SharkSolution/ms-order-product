/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.entity.CouponProduct
 *  com.suresell.order.model.entity.DiscountCoupon
 *  jakarta.persistence.CascadeType
 *  jakarta.persistence.Column
 *  jakarta.persistence.Entity
 *  jakarta.persistence.GeneratedValue
 *  jakarta.persistence.GenerationType
 *  jakarta.persistence.Id
 *  jakarta.persistence.Index
 *  jakarta.persistence.OneToMany
 *  jakarta.persistence.PrePersist
 *  jakarta.persistence.PreUpdate
 *  jakarta.persistence.Table
 *  lombok.Generated
 */
package com.suresell.order.model.entity;
import com.suresell.order.model.entity.CouponProduct;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Generated;
@Entity
@Table(name="discount_coupons", indexes={@Index(name="idx_discount_code", columnList="code", unique=true), @Index(name="idx_discount_active", columnList="is_active"), @Index(name="idx_discount_valid_dates", columnList="valid_from,valid_to")})
public class DiscountCoupon {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="code", nullable=false, unique=true, length=50)
    private String code;
    @Column(name="name", nullable=false, length=100)
    private String name;
    @Column(name="description", columnDefinition="TEXT")
    private String description;
    @Column(name="discount_percentage", precision=5, scale=2, nullable=false)
    private BigDecimal discountPercentage;
    @OneToMany(mappedBy="coupon", cascade={CascadeType.ALL}, orphanRemoval=true)
    private List<CouponProduct> products = new ArrayList();
    @Column(name="valid_from")
    private LocalDate validFrom;
    @Column(name="valid_to")
    private LocalDate validTo;
    @Column(name="valid_weekdays", length=50)
    private String validWeekdays;
    @Column(name="is_active", nullable=false)
    private Boolean isActive = true;
    @Column(name="created_at", nullable=false)
    private LocalDateTime createdAt;
    @Column(name="updated_at", nullable=false)
    private LocalDateTime updatedAt;
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.isActive == null) {
            this.isActive = true;
        }
    }
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    public void addProduct(CouponProduct product) {
        this.products.add(product);
        product.setCoupon(this);
    }
    public void removeProduct(CouponProduct product) {
        this.products.remove(product);
        product.setCoupon(null);
    }
    @Generated
    public Long getId() {
        return this.id;
    }
    @Generated
    public String getCode() {
        return this.code;
    }
    @Generated
    public String getName() {
        return this.name;
    }
    @Generated
    public String getDescription() {
        return this.description;
    }
    @Generated
    public BigDecimal getDiscountPercentage() {
        return this.discountPercentage;
    }
    @Generated
    public List<CouponProduct> getProducts() {
        return this.products;
    }
    @Generated
    public LocalDate getValidFrom() {
        return this.validFrom;
    }
    @Generated
    public LocalDate getValidTo() {
        return this.validTo;
    }
    @Generated
    public String getValidWeekdays() {
        return this.validWeekdays;
    }
    @Generated
    public Boolean getIsActive() {
        return this.isActive;
    }
    @Generated
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }
    @Generated
    public LocalDateTime getUpdatedAt() {
        return this.updatedAt;
    }
    @Generated
    public void setId(Long id) {
        this.id = id;
    }
    @Generated
    public void setCode(String code) {
        this.code = code;
    }
    @Generated
    public void setName(String name) {
        this.name = name;
    }
    @Generated
    public void setDescription(String description) {
        this.description = description;
    }
    @Generated
    public void setDiscountPercentage(BigDecimal discountPercentage) {
        this.discountPercentage = discountPercentage;
    }
    @Generated
    public void setProducts(List<CouponProduct> products) {
        this.products = products;
    }
    @Generated
    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }
    @Generated
    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
    }
    @Generated
    public void setValidWeekdays(String validWeekdays) {
        this.validWeekdays = validWeekdays;
    }
    @Generated
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
    @Generated
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    @Generated
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof DiscountCoupon)) {
            return false;
        }
        DiscountCoupon other = (DiscountCoupon)o;
        if (!other.canEqual((Object)this)) {
            return false;
        }
        Long this$id = this.getId();
        Long other$id = other.getId();
        if (this$id == null ? other$id != null : !((Object)this$id).equals(other$id)) {
            return false;
        }
        Boolean this$isActive = this.getIsActive();
        Boolean other$isActive = other.getIsActive();
        if (this$isActive == null ? other$isActive != null : !((Object)this$isActive).equals(other$isActive)) {
            return false;
        }
        String this$code = this.getCode();
        String other$code = other.getCode();
        if (this$code == null ? other$code != null : !this$code.equals(other$code)) {
            return false;
        }
        String this$name = this.getName();
        String other$name = other.getName();
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
            return false;
        }
        String this$description = this.getDescription();
        String other$description = other.getDescription();
        if (this$description == null ? other$description != null : !this$description.equals(other$description)) {
            return false;
        }
        BigDecimal this$discountPercentage = this.getDiscountPercentage();
        BigDecimal other$discountPercentage = other.getDiscountPercentage();
        if (this$discountPercentage == null ? other$discountPercentage != null : !((Object)this$discountPercentage).equals(other$discountPercentage)) {
            return false;
        }
        List this$products = this.getProducts();
        List other$products = other.getProducts();
        if (this$products == null ? other$products != null : !((Object)this$products).equals(other$products)) {
            return false;
        }
        LocalDate this$validFrom = this.getValidFrom();
        LocalDate other$validFrom = other.getValidFrom();
        if (this$validFrom == null ? other$validFrom != null : !((Object)this$validFrom).equals(other$validFrom)) {
            return false;
        }
        LocalDate this$validTo = this.getValidTo();
        LocalDate other$validTo = other.getValidTo();
        if (this$validTo == null ? other$validTo != null : !((Object)this$validTo).equals(other$validTo)) {
            return false;
        }
        String this$validWeekdays = this.getValidWeekdays();
        String other$validWeekdays = other.getValidWeekdays();
        if (this$validWeekdays == null ? other$validWeekdays != null : !this$validWeekdays.equals(other$validWeekdays)) {
            return false;
        }
        LocalDateTime this$createdAt = this.getCreatedAt();
        LocalDateTime other$createdAt = other.getCreatedAt();
        if (this$createdAt == null ? other$createdAt != null : !((Object)this$createdAt).equals(other$createdAt)) {
            return false;
        }
        LocalDateTime this$updatedAt = this.getUpdatedAt();
        LocalDateTime other$updatedAt = other.getUpdatedAt();
        return !(this$updatedAt == null ? other$updatedAt != null : !((Object)this$updatedAt).equals(other$updatedAt));
    }
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof DiscountCoupon;
    }
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        Boolean $isActive = this.getIsActive();
        result = result * 59 + ($isActive == null ? 43 : ((Object)$isActive).hashCode());
        String $code = this.getCode();
        result = result * 59 + ($code == null ? 43 : $code.hashCode());
        String $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        String $description = this.getDescription();
        result = result * 59 + ($description == null ? 43 : $description.hashCode());
        BigDecimal $discountPercentage = this.getDiscountPercentage();
        result = result * 59 + ($discountPercentage == null ? 43 : ((Object)$discountPercentage).hashCode());
        List $products = this.getProducts();
        result = result * 59 + ($products == null ? 43 : ((Object)$products).hashCode());
        LocalDate $validFrom = this.getValidFrom();
        result = result * 59 + ($validFrom == null ? 43 : ((Object)$validFrom).hashCode());
        LocalDate $validTo = this.getValidTo();
        result = result * 59 + ($validTo == null ? 43 : ((Object)$validTo).hashCode());
        String $validWeekdays = this.getValidWeekdays();
        result = result * 59 + ($validWeekdays == null ? 43 : $validWeekdays.hashCode());
        LocalDateTime $createdAt = this.getCreatedAt();
        result = result * 59 + ($createdAt == null ? 43 : ((Object)$createdAt).hashCode());
        LocalDateTime $updatedAt = this.getUpdatedAt();
        result = result * 59 + ($updatedAt == null ? 43 : ((Object)$updatedAt).hashCode());
        return result;
    }
    @Generated
    public String toString() {
        return "DiscountCoupon(id=" + this.getId() + ", code=" + this.getCode() + ", name=" + this.getName() + ", description=" + this.getDescription() + ", discountPercentage=" + String.valueOf(this.getDiscountPercentage()) + ", products=" + String.valueOf(this.getProducts()) + ", validFrom=" + String.valueOf(this.getValidFrom()) + ", validTo=" + String.valueOf(this.getValidTo()) + ", validWeekdays=" + this.getValidWeekdays() + ", isActive=" + this.getIsActive() + ", createdAt=" + String.valueOf(this.getCreatedAt()) + ", updatedAt=" + String.valueOf(this.getUpdatedAt()) + ")";
    }
    @Generated
    public DiscountCoupon() {
    }
    @Generated
    public DiscountCoupon(Long id, String code, String name, String description, BigDecimal discountPercentage, List<CouponProduct> products, LocalDate validFrom, LocalDate validTo, String validWeekdays, Boolean isActive, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.discountPercentage = discountPercentage;
        this.products = products;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.validWeekdays = validWeekdays;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
