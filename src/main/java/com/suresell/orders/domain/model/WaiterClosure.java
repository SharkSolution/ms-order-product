package com.suresell.orders.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "waiter_closures",
        uniqueConstraints = @UniqueConstraint(columnNames = {"waiter_id", "closure_date"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WaiterClosure implements Persistable<UUID> {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "waiter_id", nullable = false, length = 100)
    private String waiterId;

    @Column(name = "waiter_name", nullable = false, length = 255)
    private String waiterName;

    @Column(name = "closure_date", nullable = false)
    private LocalDate closureDate;

    @Column(name = "base_cash", precision = 15, scale = 2, nullable = false)
    private BigDecimal baseCash;

    // Expected values
    @Column(name = "total_expected_cash", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalExpectedCash;

    @Column(name = "total_expected_card", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalExpectedCard;

    @Column(name = "total_expected_qr", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalExpectedQr;

    // Counted values
    @Column(name = "total_counted_cash", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalCountedCash;

    @Column(name = "total_counted_card", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalCountedCard;

    @Column(name = "total_counted_qr", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalCountedQr;

    // Differences
    @Column(name = "difference_cash", precision = 15, scale = 2, nullable = false)
    private BigDecimal differenceCash;

    @Column(name = "difference_card", precision = 15, scale = 2, nullable = false)
    private BigDecimal differenceCard;

    @Column(name = "difference_qr", precision = 15, scale = 2, nullable = false)
    private BigDecimal differenceQr;

    @Column(name = "total_difference", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalDifference;

    @Column(name = "status", length = 50, nullable = false)
    private String status;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "closed_at", nullable = false)
    private LocalDateTime closedAt;

    @Transient
    private boolean isNewRecord = true;

    @Override
    public UUID getId() {
        return id;
    }

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
