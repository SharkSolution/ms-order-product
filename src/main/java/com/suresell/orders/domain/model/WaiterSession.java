package com.suresell.orders.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sesión/turno de mesero (F4 Inc.3, docs/200). Espejo multi-tenant del
 * `active_sessions` del ms-order-waiter legacy: el login crea la sesión y el
 * turno (base de caja, cierre con efectivo declarado) vive sobre la misma fila.
 */
@Entity
@Table(name = "waiter_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@jakarta.persistence.EntityListeners(com.suresell.orders.multitenant.TenantEntityListener.class)
public class WaiterSession
        implements org.springframework.data.domain.Persistable<UUID>, com.suresell.orders.multitenant.TenantOwned {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CLOSED = "CLOSED";

    @Id
    @Column(nullable = false)
    private UUID id = UUID.randomUUID();

    @Transient
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean isNew = true;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "waiter_id", nullable = false)
    private Long waiterId;

    @Column(name = "waiter_name")
    private String waiterName;

    @Column(nullable = false)
    private String status = STATUS_ACTIVE;

    @Column(name = "login_time", nullable = false)
    private LocalDateTime loginTime;

    @Column(name = "logout_time")
    private LocalDateTime logoutTime;

    @Column(name = "opening_cash_base")
    private BigDecimal openingCashBase;

    @Column(name = "declared_cash")
    private BigDecimal declaredCash;

    @Column(name = "expected_cash")
    private BigDecimal expectedCash;

    @Column(name = "difference")
    private BigDecimal difference;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
