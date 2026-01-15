package com.suresell.order.repository;

import com.suresell.order.model.entity.OrderEditHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderEditHistoryRepository extends JpaRepository<OrderEditHistory, Long> {

    /**
     * Obtiene el historial de ediciones de una orden específica con paginación.
     */
    Page<OrderEditHistory> findByOrderIdOrderByEditedAtDesc(Long orderId, Pageable pageable);

    /**
     * Obtiene todo el historial de ediciones con paginación.
     */
    Page<OrderEditHistory> findAllByOrderByEditedAtDesc(Pageable pageable);
}
