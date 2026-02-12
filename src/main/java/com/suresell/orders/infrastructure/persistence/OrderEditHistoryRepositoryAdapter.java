package com.suresell.orders.infrastructure.persistence;

import com.suresell.orders.domain.model.OrderEditHistory;
import com.suresell.orders.domain.port.out.OrderEditHistoryRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrderEditHistoryRepositoryAdapter implements OrderEditHistoryRepositoryPort {

    private final OrderEditHistoryRepository orderEditHistoryRepository;

    @Override
    public OrderEditHistory save(OrderEditHistory orderEditHistory) {
        return orderEditHistoryRepository.save(orderEditHistory);
    }

    @Override
    public Optional<OrderEditHistory> findById(Long id) {
        return orderEditHistoryRepository.findById(id);
    }

    @Override
    public List<OrderEditHistory> findAll() {
        return orderEditHistoryRepository.findAll();
    }

    @Override
    public Page<OrderEditHistory> findByOrderIdOrderByEditedAtDesc(Long orderId, Pageable pageable) {
        return orderEditHistoryRepository.findByOrderIdOrderByEditedAtDesc(orderId, pageable);
    }

    @Override
    public Page<OrderEditHistory> findAllByOrderByEditedAtDesc(Pageable pageable) {
        return orderEditHistoryRepository.findAllByOrderByEditedAtDesc(pageable);
    }
}
