package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.FoodWaste;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface FoodWasteRepository extends JpaRepository<FoodWaste, Long> {
    List<FoodWaste> findByWasteDateBetweenOrderByCreatedAtDesc(LocalDate from, LocalDate to);
}
