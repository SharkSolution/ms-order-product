package com.suresell.orders.application.usecase;
import com.suresell.orders.domain.model.SyncOutbox;
import com.suresell.orders.domain.port.out.OrderCloudSyncPort;
import com.suresell.orders.domain.port.out.SyncOutboxRepositoryPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
// Maquinaria local-first: inactiva en el perfil cloud (sync.cloud.enabled=false).
@ConditionalOnProperty(prefix = "sync.cloud", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SyncOutboxScheduler {
    private static final int MAX_ERROR_LENGTH = 250;
    private final SyncOutboxRepositoryPort syncOutboxRepositoryPort;
    private final OrderCloudSyncPort orderCloudSyncPort;
    @Value("${sync.cloud.enabled:false}")
    private boolean cloudSyncEnabled;
    @Value("${sync.scheduler.batch-size:20}")
    private int batchSize;
    @Scheduled(fixedDelayString = "${sync.scheduler.fixed-delay-ms:5000}")
    public void syncPendingData() {
        if (!cloudSyncEnabled) {
            return;
        }
        long now = System.currentTimeMillis();
        List<SyncOutbox> pending = syncOutboxRepositoryPort.findReadyForSync(now, batchSize);
        if (pending.isEmpty()) {
            return;
        }
        for (SyncOutbox outbox : pending) {
            processOutboxRecord(outbox);
        }
    }
    private boolean processOutboxRecord(SyncOutbox outbox) {
        long processingTime = System.currentTimeMillis();
        boolean claimed = syncOutboxRepositoryPort.markInProgress(outbox.getId(), processingTime);
        if (!claimed) {
            return true;
        }
        try {
            orderCloudSyncPort.syncOrderCreatedPayload(outbox.getPayloadJson());
            long syncedAt = System.currentTimeMillis();
            syncOutboxRepositoryPort.markSynced(outbox.getId(), syncedAt, syncedAt);
            if ("ORDER".equals(outbox.getAggregateType())) {
                syncOutboxRepositoryPort.markOrderAsSynced(outbox.getAggregateId());
            }
            return true;
        } catch (Exception ex) {
            int attempts = (outbox.getAttempts() == null ? 0 : outbox.getAttempts()) + 1;
            long backoffSeconds = calculateBackoffSeconds(attempts);
            long now = System.currentTimeMillis();
            long nextRetryAt = now + (backoffSeconds * 1000);
            syncOutboxRepositoryPort.markFailed(
                    outbox.getId(),
                    shortenError(ex),
                    attempts,
                    nextRetryAt,
                    now);
            log.warn("Fallo sincronización cloud para outboxId={} aggregateId={} attempts={} error={}",
                    outbox.getId(),
                    outbox.getAggregateId(),
                    attempts,
                    ex.getMessage());
            return false;
        }
    }
    private long calculateBackoffSeconds(int attempts) {
        long seconds = (long) (5 * Math.pow(2, Math.max(0, attempts - 1)));
        return Math.min(seconds, 300);
    }
    private String shortenError(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
