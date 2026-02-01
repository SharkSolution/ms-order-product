package com.suresell.order.model.record;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Record para órdenes creadas en modo offline.
 * Se persiste como JSON en disco para sincronización posterior.
 */
@Builder
public record OfflineOrderRecord(
        String localOrderId,
        String idempotencyKey,
        OrderRequestRecord orderData,
        boolean synced,
        Long externalOrderId,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime syncedAt,
        int syncAttempts,
        String lastError
) {
    public static OfflineOrderRecord createNew(String localOrderId, String idempotencyKey, OrderRequestRecord orderData) {
        return OfflineOrderRecord.builder()
                .localOrderId(localOrderId)
                .idempotencyKey(idempotencyKey)
                .orderData(orderData)
                .synced(false)
                .externalOrderId(null)
                .createdAt(LocalDateTime.now())
                .syncedAt(null)
                .syncAttempts(0)
                .lastError(null)
                .build();
    }
}
