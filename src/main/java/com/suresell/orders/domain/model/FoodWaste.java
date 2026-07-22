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
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Baja de comida (F5 A15): producto caído/dañado/vencido, con motivo y costo estimado. */
@Entity
@Table(name = "food_waste")
@Data
@NoArgsConstructor
@AllArgsConstructor
@jakarta.persistence.EntityListeners(com.suresell.orders.multitenant.TenantEntityListener.class)
public class FoodWaste implements com.suresell.orders.multitenant.TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "waste_date", nullable = false)
    private LocalDate wasteDate;

    @Column(name = "product_id")
    private String productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(nullable = false)
    private String reason;

    @Column(name = "est_cost", precision = 15, scale = 2)
    private BigDecimal estCost;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
