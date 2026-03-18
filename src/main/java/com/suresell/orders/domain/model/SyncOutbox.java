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
@Entity
@Table(name = "sync_outbox")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncOutbox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;
    @Column(name = "aggregate_uuid")
    private java.util.UUID aggregateUuid;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;
    @Column(name = "status", nullable = false)
    private String status;
    @Column(name = "attempts", nullable = false)
    private Integer attempts;
    @Column(name = "next_retry_at", nullable = false)
    private Long nextRetryAt;
    @Column(name = "last_error")
    private String lastError;
    @Column(name = "created_at", nullable = false)
    private Long createdAt;
    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;
    @Column(name = "synced_at")
    private Long syncedAt;
}
