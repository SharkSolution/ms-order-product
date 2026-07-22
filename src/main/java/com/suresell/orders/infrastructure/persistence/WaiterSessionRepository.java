package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.WaiterSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WaiterSessionRepository extends JpaRepository<WaiterSession, UUID> {
    Optional<WaiterSession> findFirstByWaiterIdAndStatusOrderByLoginTimeDesc(Long waiterId, String status);
}
