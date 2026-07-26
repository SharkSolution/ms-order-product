package com.suresell.orders.application.usecase;

import com.suresell.orders.application.dto.KitchenOrderDto;
import com.suresell.orders.application.dto.KitchenOrderDto.DeliverRequest;
import com.suresell.orders.application.dto.KitchenOrderDto.KitchenOrderItemDto;
import com.suresell.orders.application.dto.KitchenOrderDto.KitchenPageDto;
import com.suresell.orders.application.dto.KitchenOrderDto.KitchenTrackingDto;
import com.suresell.orders.domain.model.MenuProduct;
import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderDeliveryTracking;
import com.suresell.orders.domain.model.OrderItem;
import com.suresell.orders.infrastructure.persistence.MenuProductRepository;
import com.suresell.orders.infrastructure.persistence.OrderDeliveryTrackingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Módulo cocina del backend multi-tenant (F4 Inc.1, docs/200). Mismo comportamiento
 * que el ActiveOrderQueryService/DeliverOrderService del ms-kitchen legacy, pero
 * scopeado por tenant vía RLS (TenantAwareDataSource): cada cocina ve SOLO las
 * órdenes de su negocio.
 */
@Service
public class KitchenQueryService {

    private static final java.time.ZoneId BOGOTA_ZONE = java.time.ZoneId.of("America/Bogota");

    private final OrderDeliveryTrackingRepository trackingRepository;
    private final MenuProductRepository menuProductRepository;
    // N3/#1: marcar ítems como preparados.
    private final com.suresell.orders.infrastructure.persistence.OrderItemRepository orderItemRepository;
    // N3/#1: resolver el número de mesa de las comandas de Restaurante.
    private final com.suresell.orders.infrastructure.persistence.TableSessionRepository tableSessionRepository;

    public KitchenQueryService(OrderDeliveryTrackingRepository trackingRepository,
                               MenuProductRepository menuProductRepository,
                               com.suresell.orders.infrastructure.persistence.OrderItemRepository orderItemRepository,
                               com.suresell.orders.infrastructure.persistence.TableSessionRepository tableSessionRepository) {
        this.orderItemRepository = orderItemRepository;
        this.tableSessionRepository = tableSessionRepository;
        this.trackingRepository = trackingRepository;
        this.menuProductRepository = menuProductRepository;
    }

    @Transactional(readOnly = true)
    public List<KitchenOrderDto> getActiveOrdersFifo() {
        List<OrderDeliveryTracking> active = trackingRepository.findActiveKitchenOrders();
        Map<String, String> names = productNames(active);
        Map<UUID, Mesa> mesas = mesasDe(active);
        return active.stream().map(t -> toDto(t, names, mesas)).toList();
    }

    @Transactional(readOnly = true)
    public KitchenPageDto getDeliveredOrders(int page, int size) {
        Page<OrderDeliveryTracking> result =
                trackingRepository.findDeliveredKitchenOrders(PageRequest.of(page, size));
        Map<String, String> names = productNames(result.getContent());
        Map<UUID, Mesa> mesas = mesasDe(result.getContent());
        List<KitchenOrderDto> content = result.getContent().stream()
                .map(t -> toDto(t, names, mesas))
                .toList();
        return new KitchenPageDto(content, result.getTotalElements(), result.getTotalPages(),
                result.getSize(), result.getNumber(), result.isLast());
    }

    @Transactional
    public void markDelivered(UUID orderUuid, DeliverRequest request) {
        OrderDeliveryTracking tracking = trackingRepository.findById(orderUuid)
                .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada: " + orderUuid));
        tracking.setDelivered(true);
        tracking.setPreparationDurationSeconds(request == null ? null : request.preparationDurationSeconds());
        Order order = tracking.getOrder();
        // N3/#2 — Entregar la orden implica que TODO lo que tenía ya se preparó.
        // Sin esto, cuando la mesa suma una ronda y la comanda vuelve a la cola,
        // los ítems viejos reaparecen como "NUEVO" junto a los recién pedidos y
        // la cocina no distingue qué falta por hacer.
        if (order != null && order.getIdOrder() != null) {
            orderItemRepository.marcarPreparados(order.getIdOrder(), LocalDateTime.now(BOGOTA_ZONE));
        }
        if (order != null && order.getDeliveredAt() == null) {
            order.setDeliveredAt(LocalDateTime.now());
        }
        trackingRepository.save(tracking);
    }

    private Map<String, String> productNames(List<OrderDeliveryTracking> trackings) {
        Set<String> ids = trackings.stream()
                .map(OrderDeliveryTracking::getOrder)
                .filter(Objects::nonNull)
                .flatMap(o -> o.getItems() == null ? java.util.stream.Stream.<OrderItem>empty() : o.getItems().stream())
                .map(OrderItem::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return menuProductRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(MenuProduct::getIdProduct, MenuProduct::getNameProduct,
                        (a, b) -> a, java.util.HashMap::new));
    }

    private KitchenOrderDto toDto(OrderDeliveryTracking tracking, Map<String, String> productNames,
                                  Map<UUID, Mesa> mesas) {
        Order order = tracking.getOrder();
        List<KitchenOrderItemDto> items = order.getItems() == null ? List.of()
                : order.getItems().stream().map(i -> toItemDto(i, productNames)).toList();
        Mesa mesa = order.getTableSessionId() == null ? null : mesas.get(order.getTableSessionId());
        return new KitchenOrderDto(
                order.getIdOrder(),
                order.getUuidId() == null ? null : order.getUuidId().toString(),
                order.getPagerColor(),
                order.getPagerNumber(),
                order.getCreatedAt(),
                order.getSynced(),
                order.getTotal(),
                order.getStatus() == null ? null : order.getStatus().name(),
                mesa == null ? null : mesa.numero(),
                mesa == null ? null : mesa.etiqueta(),
                null,
                null,
                new KitchenTrackingDto(
                        tracking.getOrderId(),
                        Boolean.TRUE.equals(tracking.getDelivered()),
                        Boolean.TRUE.equals(tracking.getPagerReturned()),
                        tracking.getPreparationDurationSeconds()),
                items
        );
    }

    /** N3/#1 — Mesa resuelta para una cuenta. */
    private record Mesa(Integer numero, String etiqueta) {
    }

    /**
     * Resuelve en UNA consulta la mesa de todas las comandas del lote que vengan
     * de una cuenta abierta. En Plazoleta el mapa sale vacío y no se consulta.
     */
    private Map<UUID, Mesa> mesasDe(List<OrderDeliveryTracking> trackings) {
        Set<UUID> sesiones = trackings.stream()
                .map(OrderDeliveryTracking::getOrder)
                .filter(Objects::nonNull)
                .map(Order::getTableSessionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (sesiones.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Mesa> resultado = new java.util.HashMap<>();
        for (Object[] fila : tableSessionRepository.findMesaPorSesion(sesiones)) {
            resultado.put((UUID) fila[0], new Mesa((Integer) fila[1], (String) fila[2]));
        }
        return resultado;
    }

    private KitchenOrderItemDto toItemDto(OrderItem item, Map<String, String> productNames) {
        return new KitchenOrderItemDto(
                item.getProductId(),
                productNames.getOrDefault(item.getProductId(), item.getProductId()),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getInstructions(),
                item.getComboGroup(),
                item.getPreparedAt() != null
        );
    }

    /** N3/#1 — Marca como preparado lo pendiente de la orden. */
    @jakarta.transaction.Transactional
    public int marcarItemsPreparados(Long idOrder) {
        return orderItemRepository.marcarPreparados(idOrder, java.time.LocalDateTime.now(BOGOTA_ZONE));
    }
}
