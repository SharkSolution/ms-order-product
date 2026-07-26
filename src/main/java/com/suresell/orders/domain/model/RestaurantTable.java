package com.suresell.orders.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Mesa de una sede en modo Restaurante (Inc. 2). */
@Entity
@Table(name = "restaurant_tables")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(com.suresell.orders.multitenant.TenantEntityListener.class)
public class RestaurantTable implements com.suresell.orders.multitenant.TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "site_id")
    private Long siteId;

    /** Número visible de la mesa. */
    @Column(nullable = false)
    private Integer number;

    /** Nombre opcional ("Terraza 3"). */
    private String label;

    private Integer seats;

    @Column(nullable = false)
    private Boolean active = true;
}
