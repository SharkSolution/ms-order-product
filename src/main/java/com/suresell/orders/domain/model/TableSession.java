package com.suresell.orders.domain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuenta abierta de una mesa (Inc. 3 del modo Restaurante).
 *
 * Es la unidad de COBRO: se cobra la sesión completa, no cada orden. Todas las
 * órdenes de la sesión pasan a `pagado` en un solo movimiento, así el cierre de
 * caja sigue funcionando sin tocar su lógica.
 */
@Entity
@Table(name = "table_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(com.suresell.orders.multitenant.TenantEntityListener.class)
public class TableSession implements com.suresell.orders.multitenant.TenantOwned {

    public static final String ABIERTA = "ABIERTA";
    public static final String COBRANDO = "COBRANDO";
    public static final String CERRADA = "CERRADA";

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(nullable = false)
    private String status = ABIERTA;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "opened_by")
    private String openedBy;

    /** Caja que está cobrando. Lock suave: avisa, no bloquea. */
    @Column(name = "claimed_by")
    private String claimedBy;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    public boolean estaViva() {
        return !CERRADA.equals(status);
    }
}
