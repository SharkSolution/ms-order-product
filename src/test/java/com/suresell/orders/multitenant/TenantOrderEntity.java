package com.suresell.orders.multitenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entidad JPA mínima mapeada a `orders`, solo para el test de RLS a nivel JPA.
 * (La entidad de producción se define al empaquetar el servicio cloud.)
 */
@Entity
@Table(name = "orders")
public class TenantOrderEntity {

    @Id
    @Column(name = "uuid_id")
    private UUID uuidId;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "id_order")
    private Long idOrder;

    @Column(name = "total")
    private BigDecimal total;

    protected TenantOrderEntity() {}

    public TenantOrderEntity(UUID uuidId, String tenantId, Long idOrder, BigDecimal total) {
        this.uuidId = uuidId;
        this.tenantId = tenantId;
        this.idOrder = idOrder;
        this.total = total;
    }

    public UUID getUuidId() {
        return uuidId;
    }

    public String getTenantId() {
        return tenantId;
    }
}
