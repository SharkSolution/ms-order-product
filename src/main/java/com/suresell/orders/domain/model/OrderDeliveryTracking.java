package com.suresell.orders.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "order_delivery_tracking")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = "order")
@jakarta.persistence.EntityListeners(com.suresell.orders.multitenant.TenantEntityListener.class)
public class OrderDeliveryTracking implements org.springframework.data.domain.Persistable<java.util.UUID>, com.suresell.orders.multitenant.TenantOwned {
    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "order_id", insertable = true, updatable = true)
    private Long orderId;
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "order_id_uuid")
    @ToString.Exclude
    private Order order;

    @Id
    @Column(name = "order_id_uuid", nullable = false)
    private java.util.UUID orderIdUuid;

    @Transient
    private boolean isNew = true;

    @Override
    public java.util.UUID getId() {
        return orderIdUuid;
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

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public java.util.UUID getOrderIdUuid() { return orderIdUuid; }
    public void setOrderIdUuid(java.util.UUID orderIdUuid) { this.orderIdUuid = orderIdUuid; }
    public Boolean getDelivered() { return delivered; }
    public void setDelivered(Boolean delivered) { this.delivered = delivered; }
    public Boolean getPagerReturned() { return pagerReturned; }
    public void setPagerReturned(Boolean pagerReturned) { this.pagerReturned = pagerReturned; }
    public Integer getPreparationDurationSeconds() { return preparationDurationSeconds; }
    public void setPreparationDurationSeconds(Integer preparationDurationSeconds) { this.preparationDurationSeconds = preparationDurationSeconds; }


    @Column(name = "delivered", nullable = false)
        private Boolean delivered = false;
    
        @Column(name = "pager_returned", nullable = false)
        private Boolean pagerReturned = false;
    
        @Column(name = "preparation_duration_seconds")
        private Integer preparationDurationSeconds;
    }
