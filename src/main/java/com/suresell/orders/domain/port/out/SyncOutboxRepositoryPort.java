package com.suresell.orders.domain.port.out;
import com.suresell.orders.domain.model.SyncOutbox;
import java.util.List;
public interface SyncOutboxRepositoryPort {
    SyncOutbox save(SyncOutbox outbox);
    List<SyncOutbox> findReadyForSync(Long now, int limit);
    boolean markInProgress(Long id, Long updatedAt);
    void markSynced(Long id, Long updatedAt, Long syncedAt);
    void markFailed(Long id, String error, int attempts, Long nextRetryAt, Long updatedAt);
    void markOrderAsSynced(Long orderId);
}
