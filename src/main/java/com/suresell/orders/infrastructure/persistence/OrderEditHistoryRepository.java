package com.suresell.orders.infrastructure.persistence;

import com.suresell.orders.domain.model.OrderEditHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderEditHistoryRepository extends JpaRepository<OrderEditHistory, Long> {

    Page<OrderEditHistory> findByOrderIdOrderByEditedAtDesc(Long orderId, Pageable pageable);

    Page<OrderEditHistory> findAllByOrderByEditedAtDesc(Pageable pageable);
}
