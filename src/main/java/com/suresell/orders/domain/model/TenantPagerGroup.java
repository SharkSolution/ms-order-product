package com.suresell.orders.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Grupo de rastreadores configurable por negocio (N2/6.7).
 *
 * `code` es INMUTABLE porque es lo que se guarda en `orders.pager_color` (hay
 * historial con esos valores); lo que el negocio edita es `label`, `color` y
 * `quantity`. Ver V20 y docs/migraciones/V20-rastreadores.md.
 */
@Entity
@Table(name = "tenant_pager_groups")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(com.suresell.orders.multitenant.TenantEntityListener.class)
public class TenantPagerGroup implements com.suresell.orders.multitenant.TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private String tenantId;

    /** Código estable del grupo (AMARILLO, AZUL, …). No se edita. */
    @Column(nullable = false)
    private String code;

    /** Nombre visible, editable por el negocio. */
    @Column(nullable = false)
    private String label;

    /** Color hex para la UI. */
    @Column(nullable = false)
    private String color;

    /** Cantidad de rastreadores del grupo. */
    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
