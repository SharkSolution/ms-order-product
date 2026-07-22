package com.suresell.orders.infrastructure.web;

import com.suresell.orders.domain.model.RegisterExpense;
import com.suresell.orders.infrastructure.persistence.RegisterExpenseRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Gastos de caja durante el turno (F5, cluster caja): registrables en cualquier
 * momento, no solo al cerrar. El cierre del día los precarga. Tenant-scoped por
 * RLS; JWT obligatorio (TenantContextFilter).
 */
@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    private final RegisterExpenseRepository repository;

    public ExpenseController(RegisterExpenseRepository repository) {
        this.repository = repository;
    }

    public record CreateExpenseRequest(String concept, BigDecimal amount, String createdBy) {
    }

    @GetMapping
    public List<RegisterExpense> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now(BOGOTA_ZONE);
        return repository.findByExpenseDateOrderByCreatedAtAsc(day);
    }

    @PostMapping
    public ResponseEntity<RegisterExpense> create(@RequestBody CreateExpenseRequest request) {
        if (request == null || request.concept() == null || request.concept().trim().isEmpty()) {
            throw new IllegalArgumentException("El concepto del gasto es obligatorio");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto del gasto debe ser mayor a cero");
        }
        RegisterExpense expense = new RegisterExpense();
        expense.setExpenseDate(LocalDate.now(BOGOTA_ZONE));
        expense.setConcept(request.concept().trim());
        expense.setAmount(request.amount());
        expense.setCreatedBy(request.createdBy());
        expense.setCreatedAt(LocalDateTime.now(BOGOTA_ZONE));
        return ResponseEntity.status(201).body(repository.save(expense));
    }

    /** Borrar un gasto del MISMO día (corrección de digitación). RLS acota al tenant. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        RegisterExpense expense = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Gasto no encontrado: " + id));
        if (!LocalDate.now(BOGOTA_ZONE).equals(expense.getExpenseDate())) {
            throw new IllegalStateException("Solo se pueden borrar gastos del día en curso");
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
