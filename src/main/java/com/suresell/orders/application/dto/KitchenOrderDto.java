package com.suresell.orders.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Contrato del módulo cocina (F4 Inc.1, docs/200). Réplica del JSON que la app
 * `app_mobile_kitchen` ya consume del ms-kitchen legacy (ActiveOrderDto), para que
 * el repunte al backend multi-tenant sea solo de URL. `waiterId`/`waiterName`
 * viajan null hasta F4 Inc.3 (meseros multi-tenant).
 */
public record KitchenOrderDto(
        Long orderId,
        String orderUuid,
        String pagerColor,
        String pagerNumber,
        LocalDateTime createdAt,
        Boolean synced,
        BigDecimal total,
        String status,
        Long waiterId,
        String waiterName,
        KitchenTrackingDto tracking,
        List<KitchenOrderItemDto> items
) {

    public record KitchenTrackingDto(
            Long orderId,
            boolean delivered,
            boolean pagerReturned,
            Integer preparationDurationSeconds
    ) {
    }

    public record KitchenOrderItemDto(
            String productId,
            String productName,
            Integer quantity,
            BigDecimal unitPrice,
            String instructions,
            Integer comboGroup,
            /** N3/#1 — `false` = recién agregado a la mesa; la cocina lo resalta. */
            Boolean preparado
    ) {
    }

    /** Página con la misma forma que el `Page` de Spring que la app ya parsea. */
    public record KitchenPageDto(
            List<KitchenOrderDto> content,
            long totalElements,
            int totalPages,
            int size,
            int number,
            boolean last
    ) {
    }

    public record DeliverRequest(Integer preparationDurationSeconds) {
    }
}
