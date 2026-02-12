package com.suresell.orders.domain.port.out;

import com.suresell.orders.domain.model.OrderEditHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.List;

public interface OrderEditHistoryRepositoryPort {
    OrderEditHistory save(OrderEditHistory orderEditHistory);
    Optional<OrderEditHistory> findById(Long id);
    List<OrderEditHistory> findAll();
    Page<OrderEditHistory> findByOrderIdOrderByEditedAtDesc(Long orderId, Pageable pageable);
    Page<OrderEditHistory> findAllByOrderByEditedAtDesc(Pageable pageable);
}
