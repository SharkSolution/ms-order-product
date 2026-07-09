package com.suresell.orders.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")  
@Data
@NoArgsConstructor
@AllArgsConstructor
@jakarta.persistence.EntityListeners(com.suresell.orders.multitenant.TenantEntityListener.class)
public class Order implements org.springframework.data.domain.Persistable<java.util.UUID>, com.suresell.orders.multitenant.TenantOwned {
    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "id_order", insertable = true, updatable = true)
    private Long idOrder;

    @Id
    @Column(name = "uuid_id", unique = true, nullable = false)
    private java.util.UUID uuidId = java.util.UUID.randomUUID();

    @Transient
    private boolean isNew = true;

    @Override
    public java.util.UUID getId() {
        return uuidId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }

    @PostPersist
    @PostLoad
    protected void markNotNew() {
        this.isNew = false;
    }

    public Long getIdOrder() { return idOrder; }
    public void setIdOrder(Long idOrder) { this.idOrder = idOrder; }
    public java.util.UUID getUuidId() { return uuidId; }
    public void setUuidId(java.util.UUID uuidId) { this.uuidId = uuidId; }
    public String getPagerColor() { return pagerColor; }
    public void setPagerColor(String pagerColor) { this.pagerColor = pagerColor; }
    public String getPagerNumber() { return pagerNumber; }
    public void setPagerNumber(String pagerNumber) { this.pagerNumber = pagerNumber; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
    public OrderDeliveryTracking getDeliveryTracking() { return deliveryTracking; }
    public void setDeliveryTracking(OrderDeliveryTracking deliveryTracking) { this.deliveryTracking = deliveryTracking; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public String getDiscountCode() { return discountCode; }
    public void setDiscountCode(String discountCode) { this.discountCode = discountCode; }
    public BigDecimal getDiscountPercentage() { return discountPercentage; }
    public void setDiscountPercentage(BigDecimal discountPercentage) { this.discountPercentage = discountPercentage; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public Boolean getSynced() { return synced; }
    public void setSynced(Boolean synced) { this.synced = synced; }
    public Boolean getIsPrinted() { return isPrinted; }
    public void setIsPrinted(Boolean isPrinted) { this.isPrinted = isPrinted; }

    @Column(name = "pager_color")
    private String pagerColor;
    @Column(name = "pager_number")
    private String pagerNumber;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
    private BigDecimal subtotal;
    private BigDecimal total;
    @Enumerated(EnumType.STRING)  
    private OrderStatus status;
    @Column(name = "payment_method")
    private String paymentMethod;
    @Column(name = "discount_code")
    private String discountCode;
    @Column(name = "discount_percentage")
    private BigDecimal discountPercentage;
    @Column(name = "discount_amount")
    private BigDecimal discountAmount;
        @Column(name = "synced", nullable = false)
        private Boolean synced = false;
    
        @Column(name = "is_printed", nullable = false)
    private Boolean isPrinted = false;

    @OneToMany(mappedBy = "order", orphanRemoval = true)    
    private List<OrderItem> items;
    @OneToOne(mappedBy = "order", orphanRemoval = true, fetch = FetchType.EAGER)
    private OrderDeliveryTracking deliveryTracking;
}
