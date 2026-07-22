package com.suresell.orders.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Auditoría de borrado de órdenes (F5 A13): quién, cuándo, por qué y cuánto. */
@Entity
@Table(name = "order_deletions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@jakarta.persistence.EntityListeners(com.suresell.orders.multitenant.TenantEntityListener.class)
public class OrderDeletion implements com.suresell.orders.multitenant.TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "order_uuid_id", nullable = false)
    private UUID orderUuidId;

    @Column(name = "id_order")
    private Long idOrder;

    @Column(precision = 15, scale = 2)
    private BigDecimal total;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(nullable = false)
    private String reason;

    @Column(name = "deleted_by", nullable = false)
    private String deletedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
