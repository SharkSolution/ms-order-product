package com.suresell.orders.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sede de un negocio (Inc. 1 del modo Restaurante).
 *
 * Es la unidad que decide el MODO DE POS. Va por sede y no por tenant porque una
 * cadena puede tener una sede en plazoleta y otra en restaurante; el modo lo
 * cambia únicamente el KAM.
 *
 * Alcance deliberado: esto NO es el sistema multisede completo. Es la unidad
 * mínima para que el modo y el consecutivo de facturación tengan de dónde
 * colgar sin rehacerlo después.
 */
@Entity
@Table(name = "sites")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(com.suresell.orders.multitenant.TenantEntityListener.class)
public class Site implements com.suresell.orders.multitenant.TenantOwned {

    public static final String MODO_PLAZOLETA = "PLAZOLETA";
    public static final String MODO_RESTAURANTE = "RESTAURANTE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(nullable = false)
    private String name;

    /** Código corto, base de la numeración por sede. */
    @Column(nullable = false)
    private String code;

    @Column(name = "pos_mode", nullable = false)
    private String posMode = MODO_PLAZOLETA;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    public boolean esRestaurante() {
        return MODO_RESTAURANTE.equalsIgnoreCase(posMode);
    }
}
