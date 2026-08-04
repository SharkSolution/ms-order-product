package com.suresell.orders.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AUDITORÍA DE UNA CUENTA DIVIDIDA entre N comensales.
 *
 * <p>Es la única fuente de verdad del ajuste por redondeo. No hay copia del dato
 * en {@code table_sessions} ni en {@code daily_closures} a propósito: un campo
 * duplicado en dos tablas es un campo que algún día discrepa, y en dinero eso es
 * un incidente. El cierre de caja SUMA de acá, al vuelo, lo de su ventana.
 *
 * <p>Existe además porque la pregunta que hace un auditor no es "cuánto se
 * absorbió" sino <b>por qué</b>: qué mesa, cuántos comensales, quién cobró y con
 * qué pagó cada uno. Un número suelto en el cierre no responde nada de eso.
 *
 * <p>El invariante {@code cobrado + ajuste_redondeo == total} lo sostiene un
 * CHECK de la base, no solo este código.
 */
@Entity
@Table(name = "table_session_splits")
@Data
@NoArgsConstructor
@AllArgsConstructor
@jakarta.persistence.EntityListeners(com.suresell.orders.multitenant.TenantEntityListener.class)
public class TableSessionSplit implements com.suresell.orders.multitenant.TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "table_session_id", nullable = false)
    private UUID tableSessionId;

    @Column(nullable = false)
    private Integer personas;

    /** El total de la mesa, intacto. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    /** {@code floor(total / personas)} — lo que paga cada comensal. */
    @Column(name = "por_persona", nullable = false, precision = 15, scale = 2)
    private BigDecimal porPersona;

    /** {@code porPersona * personas} — lo que de verdad entra a caja. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal cobrado;

    /** {@code total - cobrado} — lo que asume el negocio. Nunca negativo. */
    @Column(name = "ajuste_redondeo", nullable = false, precision = 15, scale = 2)
    private BigDecimal ajusteRedondeo;

    /**
     * JSON con el detalle: {@code [{"persona":1,"metodo":"CASH","monto":3333}]}.
     * TEXT y no JSONB por consistencia con {@code daily_closures.cash_count_audit},
     * que es la convención de auditoría que ya usa el proyecto.
     */
    @Column(name = "detalle_por_persona", nullable = false, columnDefinition = "TEXT")
    private String detallePorPersona;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Quién cobró la mesa. */
    @Column(name = "created_by")
    private String createdBy;
}
