package com.suresell.orders.infrastructure.persistence;

import com.suresell.orders.domain.model.OrderSyncOutbox;
import com.suresell.orders.domain.port.out.OrderSyncOutboxRepositoryPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OrderSyncOutboxRepositoryAdapter implements OrderSyncOutboxRepositoryPort {

    private final OrderSyncOutboxRepository orderSyncOutboxRepository;

    @Override
    public OrderSyncOutbox save(OrderSyncOutbox outbox) {
        return orderSyncOutboxRepository.save(outbox);
    }

    @Override
    public List<OrderSyncOutbox> findReadyForSync(Long now, int limit) {
        return orderSyncOutboxRepository.findReadyForSync(now, PageRequest.of(0, limit));
    }

    @Override
    @Transactional
    public boolean markInProgress(Long id, Long updatedAt) {
        return orderSyncOutboxRepository.markInProgress(id, updatedAt) > 0;
    }

    @Override
    @Transactional
    public void markSynced(Long id, Long updatedAt, Long syncedAt) {
        orderSyncOutboxRepository.markSynced(id, updatedAt, syncedAt);
    }

    @Override
    @Transactional
    public void markFailed(Long id, String error, int attempts, Long nextRetryAt, Long updatedAt) {
        orderSyncOutboxRepository.markFailed(id, error, attempts, nextRetryAt, updatedAt);
    }
}
