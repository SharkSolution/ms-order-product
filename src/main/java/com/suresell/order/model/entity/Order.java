/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.entity.Order
 *  com.suresell.order.model.entity.OrderItem
 *  com.suresell.order.model.enums.OrderStatus
 *  com.suresell.order.model.enums.PagerColor
 *  jakarta.persistence.CascadeType
 *  jakarta.persistence.Column
 *  jakarta.persistence.Entity
 *  jakarta.persistence.EnumType
 *  jakarta.persistence.Enumerated
 *  jakarta.persistence.GeneratedValue
 *  jakarta.persistence.GenerationType
 *  jakarta.persistence.Id
 *  jakarta.persistence.OneToMany
 *  jakarta.persistence.PrePersist
 *  jakarta.persistence.PreUpdate
 *  jakarta.persistence.Table
 *  lombok.Generated
 */
package com.suresell.order.model.entity;
import com.suresell.order.model.entity.OrderItem;
import com.suresell.order.model.enums.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Generated;
@Entity
@Table(name="orders")
public class Order {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id_order")
    private Long idOrder;
    @Column(name="pager_color", nullable=false)
    private String pagerColor;
    @Column(name="pager_number", nullable=false)
    private String pagerNumber;
    @Column(name="created_at")
    private LocalDateTime createdAt;
    @Column(name="delivered_at")
    private String deliveredAt;
    private int subtotal;
    private int total;
    @Enumerated(value=EnumType.STRING)
    @Column(name="status", nullable=false)
    private OrderStatus status;
    @Column(name="payment_method")
    private String paymentMethod;
    @Column(name="discount_code")
    private String discountCode;
    @Column(name="discount_percentage")
    private Double discountPercentage;
    @Column(name="discount_amount")
    private Integer discountAmount;
    @Column(name="elapsed_seconds_to_deliver")
    private Integer elapsedSecondsToDeliver;
    @OneToMany(mappedBy="order", cascade={CascadeType.ALL}, orphanRemoval=true)
    private List<OrderItem> items;
    @Generated
    public Long getIdOrder() {
        return this.idOrder;
    }
    @Generated
    public String getPagerColor() {
        return this.pagerColor;
    }
    @Generated
    public String getPagerNumber() {
        return this.pagerNumber;
    }
    @Generated
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }
    @Generated
    public String getDeliveredAt() {
        return this.deliveredAt;
    }
    @Generated
    public int getSubtotal() {
        return this.subtotal;
    }
    @Generated
    public int getTotal() {
        return this.total;
    }
    @Generated
    public OrderStatus getStatus() {
        return this.status;
    }
    @Generated
    public String getPaymentMethod() {
        return this.paymentMethod;
    }
    @Generated
    public String getDiscountCode() {
        return this.discountCode;
    }
    @Generated
    public Double getDiscountPercentage() {
        return this.discountPercentage;
    }
    @Generated
    public Integer getDiscountAmount() {
        return this.discountAmount;
    }
    @Generated
    public Integer getElapsedSecondsToDeliver() {
        return this.elapsedSecondsToDeliver;
    }
    @Generated
    public List<OrderItem> getItems() {
        return this.items;
    }
    @Generated
    public void setIdOrder(Long idOrder) {
        this.idOrder = idOrder;
    }
    @Generated
    public void setPagerColor(String pagerColor) {
        this.pagerColor = pagerColor;
    }
    @Generated
    public void setPagerNumber(String pagerNumber) {
        this.pagerNumber = pagerNumber;
    }
    @Generated
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    @Generated
    public void setDeliveredAt(String deliveredAt) {
        this.deliveredAt = deliveredAt;
    }
    @Generated
    public void setSubtotal(int subtotal) {
        this.subtotal = subtotal;
    }
    @Generated
    public void setTotal(int total) {
        this.total = total;
    }
    @Generated
    public void setStatus(OrderStatus status) {
        this.status = status;
    }
    @Generated
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
    @Generated
    public void setDiscountCode(String discountCode) {
        this.discountCode = discountCode;
    }
    @Generated
    public void setDiscountPercentage(Double discountPercentage) {
        this.discountPercentage = discountPercentage;
    }
    @Generated
    public void setDiscountAmount(Integer discountAmount) {
        this.discountAmount = discountAmount;
    }
    @Generated
    public void setElapsedSecondsToDeliver(Integer elapsedSecondsToDeliver) {
        this.elapsedSecondsToDeliver = elapsedSecondsToDeliver;
    }
    @Generated
    public void setItems(List<OrderItem> items) {
        this.items = items;
    }
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Order)) {
            return false;
        }
        Order other = (Order)o;
        if (!other.canEqual((Object)this)) {
            return false;
        }
        if (this.getSubtotal() != other.getSubtotal()) {
            return false;
        }
        if (this.getTotal() != other.getTotal()) {
            return false;
        }
        Long this$idOrder = this.getIdOrder();
        Long other$idOrder = other.getIdOrder();
        if (this$idOrder == null ? other$idOrder != null : !((Object)this$idOrder).equals(other$idOrder)) {
            return false;
        }
        String this$pagerNumber = this.getPagerNumber();
        String other$pagerNumber = other.getPagerNumber();
        if (this$pagerNumber == null ? other$pagerNumber != null : !((Object)this$pagerNumber).equals(other$pagerNumber)) {
            return false;
        }
        Double this$discountPercentage = this.getDiscountPercentage();
        Double other$discountPercentage = other.getDiscountPercentage();
        if (this$discountPercentage == null ? other$discountPercentage != null : !((Object)this$discountPercentage).equals(other$discountPercentage)) {
            return false;
        }
        Integer this$discountAmount = this.getDiscountAmount();
        Integer other$discountAmount = other.getDiscountAmount();
        if (this$discountAmount == null ? other$discountAmount != null : !((Object)this$discountAmount).equals(other$discountAmount)) {
            return false;
        }
        Integer this$elapsedSecondsToDeliver = this.getElapsedSecondsToDeliver();
        Integer other$elapsedSecondsToDeliver = other.getElapsedSecondsToDeliver();
        if (this$elapsedSecondsToDeliver == null ? other$elapsedSecondsToDeliver != null : !((Object)this$elapsedSecondsToDeliver).equals(other$elapsedSecondsToDeliver)) {
            return false;
        }
        String this$pagerColor = this.getPagerColor();
        String other$pagerColor = other.getPagerColor();
        if (this$pagerColor == null ? other$pagerColor != null : !this$pagerColor.equals(other$pagerColor)) {
            return false;
        }
        LocalDateTime this$createdAt = this.getCreatedAt();
        LocalDateTime other$createdAt = other.getCreatedAt();
        if (this$createdAt == null ? other$createdAt != null : !((Object)this$createdAt).equals(other$createdAt)) {
            return false;
        }
        String this$deliveredAt = this.getDeliveredAt();
        String other$deliveredAt = other.getDeliveredAt();
        if (this$deliveredAt == null ? other$deliveredAt != null : !this$deliveredAt.equals(other$deliveredAt)) {
            return false;
        }
        OrderStatus this$status = this.getStatus();
        OrderStatus other$status = other.getStatus();
        if (this$status == null ? other$status != null : !this$status.equals(other$status)) {
            return false;
        }
        String this$paymentMethod = this.getPaymentMethod();
        String other$paymentMethod = other.getPaymentMethod();
        if (this$paymentMethod == null ? other$paymentMethod != null : !this$paymentMethod.equals(other$paymentMethod)) {
            return false;
        }
        String this$discountCode = this.getDiscountCode();
        String other$discountCode = other.getDiscountCode();
        if (this$discountCode == null ? other$discountCode != null : !this$discountCode.equals(other$discountCode)) {
            return false;
        }
        List this$items = this.getItems();
        List other$items = other.getItems();
        return !(this$items == null ? other$items != null : !((Object)this$items).equals(other$items));
    }
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof Order;
    }
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        result = result * 59 + this.getSubtotal();
        result = result * 59 + this.getTotal();
        Long $idOrder = this.getIdOrder();
        result = result * 59 + ($idOrder == null ? 43 : ((Object)$idOrder).hashCode());
        String $pagerNumber = this.getPagerNumber();
        result = result * 59 + ($pagerNumber == null ? 43 : ((Object)$pagerNumber).hashCode());
        Double $discountPercentage = this.getDiscountPercentage();
        result = result * 59 + ($discountPercentage == null ? 43 : ((Object)$discountPercentage).hashCode());
        Integer $discountAmount = this.getDiscountAmount();
        result = result * 59 + ($discountAmount == null ? 43 : ((Object)$discountAmount).hashCode());
        Integer $elapsedSecondsToDeliver = this.getElapsedSecondsToDeliver();
        result = result * 59 + ($elapsedSecondsToDeliver == null ? 43 : ((Object)$elapsedSecondsToDeliver).hashCode());
        String $pagerColor = this.getPagerColor();
        result = result * 59 + ($pagerColor == null ? 43 : $pagerColor.hashCode());
        LocalDateTime $createdAt = this.getCreatedAt();
        result = result * 59 + ($createdAt == null ? 43 : ((Object)$createdAt).hashCode());
        String $deliveredAt = this.getDeliveredAt();
        result = result * 59 + ($deliveredAt == null ? 43 : $deliveredAt.hashCode());
        OrderStatus $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        String $paymentMethod = this.getPaymentMethod();
        result = result * 59 + ($paymentMethod == null ? 43 : $paymentMethod.hashCode());
        String $discountCode = this.getDiscountCode();
        result = result * 59 + ($discountCode == null ? 43 : $discountCode.hashCode());
        List $items = this.getItems();
        result = result * 59 + ($items == null ? 43 : ((Object)$items).hashCode());
        return result;
    }
    @Generated
    public String toString() {
        return "Order(idOrder=" + this.getIdOrder() + ", pagerColor=" + String.valueOf(this.getPagerColor()) + ", pagerNumber=" + this.getPagerNumber() + ", createdAt=" + String.valueOf(this.getCreatedAt()) + ", deliveredAt=" + this.getDeliveredAt() + ", subtotal=" + this.getSubtotal() + ", total=" + this.getTotal() + ", status=" + String.valueOf(this.getStatus()) + ", paymentMethod=" + this.getPaymentMethod() + ", discountCode=" + this.getDiscountCode() + ", discountPercentage=" + this.getDiscountPercentage() + ", discountAmount=" + this.getDiscountAmount() + ", elapsedSecondsToDeliver=" + this.getElapsedSecondsToDeliver() + ", items=" + String.valueOf(this.getItems()) + ")";
    }
    @Generated
    public Order() {
    }
    @Generated
    public Order(Long idOrder, String pagerColor, String pagerNumber, LocalDateTime createdAt, String deliveredAt, int subtotal, int total, OrderStatus status, String paymentMethod, String discountCode, Double discountPercentage, Integer discountAmount, Integer elapsedSecondsToDeliver, List<OrderItem> items) {
        this.idOrder = idOrder;
        this.pagerColor = pagerColor;
        this.pagerNumber = pagerNumber;
        this.createdAt = createdAt;
        this.deliveredAt = deliveredAt;
        this.subtotal = subtotal;
        this.total = total;
        this.status = status;
        this.paymentMethod = paymentMethod;
        this.discountCode = discountCode;
        this.discountPercentage = discountPercentage;
        this.discountAmount = discountAmount;
        this.elapsedSecondsToDeliver = elapsedSecondsToDeliver;
        this.items = items;
    }
}
