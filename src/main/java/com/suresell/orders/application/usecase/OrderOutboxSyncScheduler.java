package com.suresell.orders.application.usecase;

import com.suresell.orders.domain.model.OrderSyncOutbox;
import com.suresell.orders.domain.port.out.OrderCloudSyncPort;
import com.suresell.orders.domain.port.out.OrderSyncOutboxRepositoryPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderOutboxSyncScheduler {

    private static final int MAX_ERROR_LENGTH = 1500;

    private final OrderSyncOutboxRepositoryPort orderSyncOutboxRepositoryPort;
    private final OrderCloudSyncPort orderCloudSyncPort;

    @Value("${sync.cloud.enabled:false}")
    private boolean cloudSyncEnabled;

    @Value("${sync.scheduler.batch-size:20}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${sync.scheduler.fixed-delay-ms:5000}")
    public void syncPendingOrders() {
        if (!cloudSyncEnabled) {
            return;
        }

        long now = System.currentTimeMillis();
        List<OrderSyncOutbox> pending = orderSyncOutboxRepositoryPort.findReadyForSync(now, batchSize);
        if (pending.isEmpty()) {
            return;
        }

        for (OrderSyncOutbox outbox : pending) {
            boolean success = processOutboxRecord(outbox);
            if (!success) {
                // FIFO estricto: no procesar órdenes más nuevas si una más vieja falla.
                break;
            }
        }
    }

    private boolean processOutboxRecord(OrderSyncOutbox outbox) {
        long processingTime = System.currentTimeMillis();
        boolean claimed = orderSyncOutboxRepositoryPort.markInProgress(outbox.getId(), processingTime);
        if (!claimed) {
            return true;
        }

        try {
            orderCloudSyncPort.syncOrderCreatedPayload(outbox.getPayloadJson());
            long syncedAt = System.currentTimeMillis();
            orderSyncOutboxRepositoryPort.markSynced(outbox.getId(), syncedAt, syncedAt);
            return true;
        } catch (Exception ex) {
            int attempts = (outbox.getAttempts() == null ? 0 : outbox.getAttempts()) + 1;
            long backoffSeconds = calculateBackoffSeconds(attempts);
            long now = System.currentTimeMillis();
            long nextRetryAt = now + (backoffSeconds * 1000);
            orderSyncOutboxRepositoryPort.markFailed(
                    outbox.getId(),
                    shortenError(ex),
                    attempts,
                    nextRetryAt,
                    now);
            log.warn("Fallo sincronización cloud para outboxId={} orderId={} attempts={} nextRetryAt={} error={}",
                    outbox.getId(),
                    outbox.getAggregateId(),
                    attempts,
                    nextRetryAt,
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
