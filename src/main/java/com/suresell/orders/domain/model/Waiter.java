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

/**
 * Mesero del negocio (F4 Inc.3, docs/200). Espejo multi-tenant del `waiters`
 * del ms-order-waiter legacy; RLS acota por tenant.
 */
@Entity
@Table(name = "waiters")
@Data
@NoArgsConstructor
@AllArgsConstructor
@jakarta.persistence.EntityListeners(com.suresell.orders.multitenant.TenantEntityListener.class)
public class Waiter implements com.suresell.orders.multitenant.TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "tenant_id")
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "daily_sale_goal", precision = 12, scale = 2)
    private BigDecimal dailySaleGoal;

    /** Base de caja por defecto asignada por el admin (V11); la app la sugiere al abrir turno. */
    @Column(name = "default_cash_base", precision = 15, scale = 2)
    private BigDecimal defaultCashBase;

    /**
     * PIN del mesero, con BCrypt. NUNCA sale al cliente.
     *
     * <p>Hasta ahora se tocaba un nombre en una lista y se operaba como esa
     * persona: tomar pedidos, abrir turno con una base y cerrarlo declarando
     * cuánto efectivo hay. Cualquiera con el teléfono podía cerrar el turno de
     * otro y dejarle un faltante.
     *
     * <p><b>Nulo = todavía no lo configuró</b>, y entonces se entra sin PIN,
     * como siempre. Lo pone el propio mesero, no el administrador: una clave
     * que otro conoce no protege al mesero de nada, que es justo de lo que se
     * trata.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "pin_hash")
    private String pinHash;

    /**
     * Si el mesero ya tiene PIN. Es lo ÚNICO que la app necesita saber para
     * decidir si pedirlo o si ofrecer configurarlo — el hash no sale nunca.
     */
    @jakarta.persistence.Transient
    @com.fasterxml.jackson.annotation.JsonProperty("tienePin")
    public boolean tienePin() {
        return pinHash != null && !pinHash.isBlank();
    }
}
