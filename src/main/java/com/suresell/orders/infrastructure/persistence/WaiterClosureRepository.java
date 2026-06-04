package com.suresell.orders.infrastructure.persistence;

import com.suresell.orders.domain.model.WaiterClosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WaiterClosureRepository extends JpaRepository<WaiterClosure, UUID> {
    Optional<WaiterClosure> findTopByWaiterIdOrderByClosedAtDesc(String waiterId);
    List<WaiterClosure> findByWaiterIdOrderByClosedAtDesc(String waiterId);
}
