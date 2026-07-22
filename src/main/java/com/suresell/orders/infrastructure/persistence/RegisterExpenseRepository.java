package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.RegisterExpense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RegisterExpenseRepository extends JpaRepository<RegisterExpense, Long> {
    List<RegisterExpense> findByExpenseDateOrderByCreatedAtAsc(LocalDate expenseDate);
}
