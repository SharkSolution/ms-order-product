package com.suresell.orders.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contratos del módulo meseros (F4 Inc.3, docs/200). Espejo del JSON que la app
 * `app_mobile_tables` ya consume del ms-order-waiter legacy, para que el repunte
 * (Inc.4) sea principalmente URL + Bearer.
 */
public final class WaiterDtos {

    private WaiterDtos() {}

    /** Pedido desde la app de meseros. Mirror del OrderRequest legacy. */
    public record WaiterOrderRequest(
            String pagerColor,
            String pagerNumber,
            String paymentMethod,
            List<OrderItemRequestRecord> items,
            String discountCode,
            String idempotencyKey,
            String waiterSessionId
    ) {
    }

    /** Respuesta compacta de la orden creada (o la ya existente por idempotencia). */
    public record WaiterOrderResponse(
            Long idOrder,
            String uuidId,
            String pagerColor,
            String pagerNumber,
            LocalDateTime createdAt,
            String status,
            String paymentMethod,
            BigDecimal subtotal,
            BigDecimal total,
            Long waiterId,
            String idempotencyKey,
            List<WaiterOrderItem> items,
            /**
             * N2 — Estado de entrega. SIN esto la app de meseros no tenía forma
             * de saber si la cocina ya despachó: pintaba "EN PREPARACIÓN" para
             * siempre porque `tracking` llegaba nulo.
             */
            WaiterOrderTracking tracking
    ) {
    }

    /** Seguimiento de entrega que la app necesita para pintar el estado. */
    public record WaiterOrderTracking(
            Boolean delivered,
            Boolean pagerReturned,
            Integer preparationDurationSeconds
    ) {
    }

    public record WaiterOrderItem(
            String productId,
            String productName,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            /**
             * Nota del item ("sin cebolla"). Se persistia bien desde siempre
             * -37 de 321 items la tenian- pero NO viajaba en la respuesta, asi
             * que el historial de la app la mostraba vacia. Era un problema de
             * presentacion, no de guardado.
             */
            String instructions
    ) {
        /**
         * Alias de `productName`.
         *
         * La app lee `product.name` o `name`, no `productName`, así que en el
         * detalle del historial salía la cantidad ("2x") con el nombre VACÍO.
         * Se agrega el alias en el backend en vez de cambiar solo la app para
         * que los APK ya instalados en el local se arreglen sin reinstalar.
         */
        @com.fasterxml.jackson.annotation.JsonProperty("name")
        public String name() {
            return productName;
        }
    }

    /** Menú anidado con los MISMOS nombres de campo del legacy (id/name/products). */
    public record MenuCategoryDto(String id, String name, List<MenuProductDto> products) {
    }

    public record MenuProductDto(String id, String name, Integer price, Boolean active) {
    }

    public record CreateWaiterRequest(String name, BigDecimal dailySaleGoal, BigDecimal defaultCashBase) {
    }

    /** Edición de mesero desde el admin (F5): campos null = sin cambio. */
    public record UpdateWaiterRequest(String name, Boolean active, BigDecimal dailySaleGoal,
                                      BigDecimal defaultCashBase) {
    }

    public record OpenShiftRequest(Long waiterId, BigDecimal openingCashBase) {
    }

    public record CloseShiftRequest(BigDecimal declaredCash) {
    }

    /** Mirror del ShiftSummaryResponse legacy. */
    public record ShiftSummaryResponse(
            UUID sessionId,
            Long waiterId,
            String waiterName,
            String status,
            LocalDateTime openedAt,
            LocalDateTime closedAt,
            BigDecimal openingCashBase,
            BigDecimal cashSales,
            BigDecimal expectedCash,
            BigDecimal declaredCash,
            BigDecimal difference,
            Map<String, BigDecimal> salesByMethod,
            Map<String, Long> ordersByMethod,
            BigDecimal totalSales,
            long totalOrders,
            BigDecimal dailySaleGoal
    ) {
    }
}
