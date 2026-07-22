package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.Waiter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WaiterRepository extends JpaRepository<Waiter, Long> {
    List<Waiter> findByActiveTrueOrderByNameAsc();
}
