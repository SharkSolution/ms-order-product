package com.suresell.orders.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.data.domain.Persistable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "daily_closures")
@Data
@NoArgsConstructor
@AllArgsConstructor
@jakarta.persistence.EntityListeners(com.suresell.orders.multitenant.TenantEntityListener.class)
public class DailyClosure implements Persistable<UUID>, com.suresell.orders.multitenant.TenantOwned {

    @Transient
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    @Column(name = "tenant_id")
    private String tenantId;

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_name", nullable = false, length = 100)
    private String userName;

    @Column(name = "opening_time", nullable = false)
    private LocalDateTime openingTime;

    @Column(name = "closing_time")
    private LocalDateTime closingTime;

    @Column(name = "closure_date", unique = true)
    private LocalDate closureDate;

    @Column(name = "total_expected_cash", precision = 15, scale = 2)
    private BigDecimal totalExpectedCash;

    @Column(name = "total_expected_card", precision = 15, scale = 2)
    private BigDecimal totalExpectedCard;


    @Column(name = "total_expected_qr", precision = 15, scale = 2)
    private BigDecimal totalExpectedQr;

    @Column(name = "total_counted_cash", precision = 15, scale = 2)
    private BigDecimal totalCountedCash;

    @Column(name = "total_counted_card", precision = 15, scale = 2)
    private BigDecimal totalCountedCard;


    @Column(name = "total_counted_qr", precision = 15, scale = 2)
    private BigDecimal totalCountedQr;

    @Column(name = "total_expected", precision = 15, scale = 2)
    private BigDecimal totalExpected;

    @Column(name = "total_counted", precision = 15, scale = 2)
    private BigDecimal totalCounted;

    @Column(name = "total_difference", precision = 15, scale = 2)
    private BigDecimal totalDifference;

    @Column(name = "difference_cash", precision = 15, scale = 2)
    private BigDecimal differenceCash;

    @Column(name = "difference_card", precision = 15, scale = 2)
    private BigDecimal differenceCard;


    @Column(name = "difference_qr", precision = 15, scale = 2)
    private BigDecimal differenceQr;

    @Column(name = "difference_amount", precision = 15, scale = 2)
    private BigDecimal differenceAmount;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "status_message", columnDefinition = "TEXT")
    private String statusMessage;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "base_balance_for_next_day", precision = 15, scale = 2)
    private BigDecimal baseBalanceForNextDay;

    @Column(name = "petty_cash_expenses", precision = 15, scale = 2)
    private BigDecimal pettyCashExpenses = BigDecimal.ZERO;

    @Column(name = "petty_cash_expenses_audit", columnDefinition = "TEXT")
    private String pettyCashExpensesAudit;

    @Column(name = "cash_count_audit", columnDefinition = "TEXT")
    private String cashCountAudit;

    @Column(name = "sales_of_day", precision = 15, scale = 2)
    private BigDecimal totalSales;

    // El ajuste por redondeo de las cuentas divididas NO se guarda acá: el
    // cierre lo SUMA AL VUELO desde `table_session_splits`, que es su única
    // fuente de verdad. Es determinista —una división ya cobrada no cambia—,
    // así que reabrir un cierre viejo da siempre el mismo número, y no hay un
    // total copiado que pueda quedar desincronizado del detalle que lo explica.

    //Campos para manter guardado offline

    @Transient
    private boolean isNewRecord = true;

    @Override
    public boolean isNew() {
        return isNewRecord;
    }

    @PostPersist
    @PostLoad
    protected void markNotNew() {
        this.isNewRecord = false;
    }
}