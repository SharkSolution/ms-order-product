package com.suresell.orders.infrastructure.web;

import com.suresell.orders.domain.model.FoodWaste;
import com.suresell.orders.infrastructure.persistence.FoodWasteRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
 * Bajas de comida (F5 A15): registro con motivo para control de pérdidas.
 * Tenant-scoped (RLS + JWT). El enganche dinámico con inventario queda para
 * cuando el dominio de insumos migre al multi-tenant (hoy vive en ms-core-app).
 */
@RestController
@RequestMapping("/api/food-waste")
public class FoodWasteController {

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private static final List<String> REASONS = List.of("CAIDA", "DANADA", "VENCIDA", "ERROR_PREPARACION", "OTRO");

    private final FoodWasteRepository repository;

    public FoodWasteController(FoodWasteRepository repository) {
        this.repository = repository;
    }

    public record CreateWasteRequest(String productId, String productName, BigDecimal quantity,
                                     String reason, BigDecimal estCost, String createdBy) {
    }

    @GetMapping
    public List<FoodWaste> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = LocalDate.now(BOGOTA_ZONE);
        return repository.findByWasteDateBetweenOrderByCreatedAtDesc(
                from != null ? from : today.minusDays(30), to != null ? to : today);
    }

    @PostMapping
    public ResponseEntity<FoodWaste> create(@RequestBody CreateWasteRequest request) {
        if (request == null || request.productName() == null || request.productName().trim().isEmpty()) {
            throw new IllegalArgumentException("El producto de la baja es obligatorio");
        }
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero");
        }
        String reason = request.reason() == null ? "" : request.reason().trim().toUpperCase();
        if (!REASONS.contains(reason)) {
            throw new IllegalArgumentException("Motivo inválido. Use: " + String.join(", ", REASONS));
        }
        FoodWaste waste = new FoodWaste();
        waste.setWasteDate(LocalDate.now(BOGOTA_ZONE));
        waste.setProductId(request.productId());
        waste.setProductName(request.productName().trim());
        waste.setQuantity(request.quantity());
        waste.setReason(reason);
        waste.setEstCost(request.estCost());
        waste.setCreatedBy(request.createdBy());
        waste.setCreatedAt(LocalDateTime.now(BOGOTA_ZONE));
        return ResponseEntity.status(201).body(repository.save(waste));
    }
}
