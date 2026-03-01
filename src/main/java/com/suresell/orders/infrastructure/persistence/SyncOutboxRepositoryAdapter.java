package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.SyncOutbox;
import com.suresell.orders.domain.port.out.SyncOutboxRepositoryPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
@RequiredArgsConstructor
public class SyncOutboxRepositoryAdapter implements SyncOutboxRepositoryPort {
    private final SyncOutboxRepository syncOutboxRepository;
    private final OrderRepository orderRepository;
    @Override
    public SyncOutbox save(SyncOutbox outbox) {
        return syncOutboxRepository.save(outbox);
    }
    @Override
    public List<SyncOutbox> findReadyForSync(Long now, int limit) {
        return syncOutboxRepository.findReadyForSync(now, PageRequest.of(0, limit));
    }
    @Override
    @Transactional
    public boolean markInProgress(Long id, Long updatedAt) {
        return syncOutboxRepository.markInProgress(id, updatedAt) > 0;
    }
    @Override
    @Transactional
    public void markSynced(Long id, Long updatedAt, Long syncedAt) {
        syncOutboxRepository.markSynced(id, updatedAt, syncedAt);
    }
    @Override
    @Transactional
    public void markFailed(Long id, String error, int attempts, Long nextRetryAt, Long updatedAt) {
        syncOutboxRepository.markFailed(id, error, attempts, nextRetryAt, updatedAt);
    }
    @Override
    @Transactional
    public void markOrderAsSynced(Long orderId) {
        orderRepository.markOrderAsSynced(orderId);
    }
}
