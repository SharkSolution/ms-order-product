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
            String waiterSessionId,
            /**
             * Mesa REAL del pedido (modo Restaurante).
             *
             * <p>Hasta ahora la app mandaba siempre el mismo rastreador quemado y
             * la cocina veía todas las comandas iguales. Con el número de mesa el
             * pedido se liga a la cuenta de esa mesa: se acumula con las rondas
             * anteriores y se cobra todo junto al final.
             *
             * <p>Si la mesa no tiene cuenta abierta, se abre. El mesero no debería
             * tener que acordarse de "abrir la mesa" antes de tomar el pedido.
             *
             * <p>Ausente = comportamiento anterior (rastreador). En Plazoleta se
             * ignora: ahí no hay mesas.
             */
            Integer mesaNumero,
            /**
             * Multipago: porciones por medio de pago. La suma debe dar el total.
             * Null o vacío = pago simple con {@code paymentMethod}.
             */
            List<OrderRequestRecord.PaymentSplitRecord> payments,
            /**
             * Si el rastreador enviado debe darse por bueno SIN comprobar que
             * esté libre.
             *
             * <p>Existía como constante {@code true} adentro del servicio: la app
             * mandaba siempre el mismo rastreador quemado (`Azul #1`) y, si se
             * validaba, la SEGUNDA orden del turno moría con 409 "ya está en
             * uso". Se parcheó el backend para tolerar al cliente.
             *
             * <p>Ahora la app puede elegir un rastreador REAL, y pedir que se
             * valide —que es lo que hace que la tablet y el POS de PC no puedan
             * entregarle el mismo rastreador a dos clientes—.
             *
             * <p><b>Ausente = {@code true}</b>, el comportamiento de siempre. Un
             * APK viejo, que no conoce este campo, sigue funcionando igual: es la
             * regla 1 del contrato de compatibilidad —los campos nuevos son
             * opcionales y su ausencia conserva la conducta anterior—.
             *
             * <p>En modo Restaurante da lo mismo lo que llegue: una orden con
             * cuenta de mesa nunca ocupa rastreador.
             */
            Boolean skipPagerCheck
    ) {

        /**
         * Qué hacer cuando el campo no viene: no validar, como siempre.
         *
         * <p>Está acá y no en el servicio para que la regla se lea una sola vez
         * y no se pueda invertir por descuido en un solo lugar.
         */
        public boolean omitirChequeoDeRastreador() {
            return skipPagerCheck == null || skipPagerCheck;
        }
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
