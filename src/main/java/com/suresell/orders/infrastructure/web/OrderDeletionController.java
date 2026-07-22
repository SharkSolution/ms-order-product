package com.suresell.orders.infrastructure.web;

import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderDeletion;
import com.suresell.orders.infrastructure.persistence.OrderDeletionRepository;
import com.suresell.orders.infrastructure.persistence.OrderRepository;
import com.suresell.orders.multitenant.JwtTenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Borrado de órdenes por ADMINISTRADOR (F5 A13, anti-robo): SOFT-DELETE con
 * auditoría — nunca borrado físico. La orden desaparece de historial, cocina,
 * pagers y cierres (filtro global de la entidad Order) y queda el rastro en
 * order_deletions (quién, cuándo, motivo, monto). Solo rol admin (JWT).
 * SOLO perfil cloud (multi-tenant).
 */
@RestController
@Profile("cloud")
@RequestMapping("/api/orders")
public class OrderDeletionController {

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    private final OrderRepository orderRepository;
    private final OrderDeletionRepository deletionRepository;
    private final JwtTenantResolver resolver;

    public OrderDeletionController(OrderRepository orderRepository,
                                   OrderDeletionRepository deletionRepository,
                                   JwtTenantResolver resolver) {
        this.orderRepository = orderRepository;
        this.deletionRepository = deletionRepository;
        this.resolver = resolver;
    }

    public record DeleteOrderRequest(String reason) {
    }

    @DeleteMapping("/{idOrder}")
    @Transactional
    public ResponseEntity<?> softDelete(@PathVariable Long idOrder,
                                        @RequestBody(required = false) DeleteOrderRequest request,
                                        HttpServletRequest http) {
        String role = resolver.resolveRole(http.getHeader("Authorization")).orElse("");
        if (!"admin".equals(role)) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Solo un administrador puede borrar órdenes"));
        }
        String reason = request != null && request.reason() != null ? request.reason().trim() : "";
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("El motivo del borrado es obligatorio");
        }
        Order order = orderRepository.findByIdOrder(idOrder)
                .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada: #" + idOrder));

        String deletedBy = resolver.resolveSubject(http.getHeader("Authorization")).orElse("admin");
        LocalDateTime now = LocalDateTime.now(BOGOTA_ZONE);
        int updated = orderRepository.softDelete(order.getUuidId(), now, deletedBy);
        if (updated == 0) {
            throw new IllegalArgumentException("La orden ya estaba borrada: #" + idOrder);
        }

        OrderDeletion audit = new OrderDeletion();
        audit.setOrderUuidId(order.getUuidId());
        audit.setIdOrder(order.getIdOrder());
        audit.setTotal(order.getTotal());
        audit.setPaymentMethod(order.getPaymentMethod());
        audit.setReason(reason);
        audit.setDeletedBy(deletedBy);
        audit.setCreatedAt(now);
        deletionRepository.save(audit);

        return ResponseEntity.ok(Map.of(
                "message", "Orden #" + idOrder + " borrada (soft-delete, con auditoría)",
                "idOrder", idOrder));
    }

    /** Auditoría de borrados del negocio (admin). */
    @GetMapping("/deletions")
    public ResponseEntity<?> listDeletions(HttpServletRequest http) {
        String role = resolver.resolveRole(http.getHeader("Authorization")).orElse("");
        if (!"admin".equals(role)) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Solo un administrador puede ver la auditoría"));
        }
        List<OrderDeletion> deletions = deletionRepository.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(deletions);
    }
}
