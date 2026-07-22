package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.OrderDeletion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderDeletionRepository extends JpaRepository<OrderDeletion, Long> {
    java.util.List<OrderDeletion> findAllByOrderByCreatedAtDesc();
}
