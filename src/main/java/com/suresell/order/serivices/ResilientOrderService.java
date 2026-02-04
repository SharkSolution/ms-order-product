package com.suresell.order.serivices;

import com.fasterxml.jackson.core.type.TypeReference;
import com.suresell.order.mapper.OrderMapper;
import com.suresell.order.model.record.OfflineOrderRecord;
import com.suresell.order.model.record.OrderItemRequestRecord;
import com.suresell.order.model.record.OrderItemResponseRecord;
import com.suresell.order.model.record.OrderRequestRecord;
import com.suresell.order.model.record.OrderResponseRecord;
import com.suresell.order.serivices.impl.OrderServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio resiliente para órdenes con soporte offline-first.
 * Garantiza que las órdenes NUNCA se pierdan, incluso si AWS está caído.
 * Usa SOLO cache en disco (JSON) - sin base de datos adicional.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ResilientOrderService {

    private final OrderService standardOrderService;
    private final ConnectivityService connectivityService;
    private final DiskCacheService diskCacheService;
    private final OrderMapper orderMapper;

    private static final String OFFLINE_ORDERS_INDEX = "offline-orders-index";

    /**
     * Crea una orden con fallback offline automático.
     *
     * ONLINE: Crea en AWS
     * OFFLINE: Crea localmente y encola para sincronización
     */
    public void createOrder(OrderRequestRecord request) {
        // Intentar crear en AWS si está disponible
        if (connectivityService.isAWSRdsAvailable()) {
            try {
                standardOrderService.createOrUpdateOrder(request);
                log.info("Order created ONLINE");
                return;

            } catch (Exception e) {
                log.error("Failed to create order in AWS, falling back to OFFLINE mode: {}", e.getMessage());
                // Continúa al flujo offline
            }
        }

        // Flujo OFFLINE: crear orden localmente
        createOfflineOrder(request);
    }

    /**
     * Crea una orden en modo offline para sincronización posterior
     */
    private void createOfflineOrder(OrderRequestRecord request) {
        try {
            // Generar IDs únicos
            String localOrderId = "LOCAL-" + UUID.randomUUID().toString();
            String idempotencyKey = UUID.randomUUID().toString();

            // Crear record de orden offline
            OfflineOrderRecord offlineOrder = OfflineOrderRecord.createNew(localOrderId, idempotencyKey, request);

            // Guardar orden offline individual
            diskCacheService.save("offline-order-" + localOrderId, offlineOrder);

            // Agregar a índice de órdenes offline
            addToOfflineIndex(offlineOrder);

            log.warn("Order created OFFLINE: {} (will sync when AWS is available)", localOrderId);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to create offline order: {}", e.getMessage(), e);
            throw new RuntimeException("Cannot create order offline: " + e.getMessage());
        }
    }

    /**
     * Agrega una orden al índice de órdenes offline
     */
    private synchronized void addToOfflineIndex(OfflineOrderRecord offlineOrder) {
        List<OfflineOrderRecord> index = getOfflineOrdersIndex();
        index.add(offlineOrder);
        diskCacheService.save(OFFLINE_ORDERS_INDEX, index);
    }

    /**
     * Obtiene el índice de órdenes offline
     */
    public List<OfflineOrderRecord> getOfflineOrdersIndex() {
        return diskCacheService.read(OFFLINE_ORDERS_INDEX, new TypeReference<List<OfflineOrderRecord>>() {})
                .orElse(new ArrayList<>());
    }

    /**
     * Actualiza una orden en el índice
     */
    public synchronized void updateOfflineOrderInIndex(OfflineOrderRecord updatedOrder) {
        List<OfflineOrderRecord> index = getOfflineOrdersIndex();
        index.removeIf(o -> o.localOrderId().equals(updatedOrder.localOrderId()));
        index.add(updatedOrder);
        diskCacheService.save(OFFLINE_ORDERS_INDEX, index);
    }

    /**
     * Obtiene órdenes de cocina con fallback a cache si AWS falla
     */
    public List<OrderResponseRecord> getKitchenOrders() {
        // Intentar obtener desde AWS
        if (connectivityService.isAWSRdsAvailable()) {
            try {
                List<OrderResponseRecord> orders = standardOrderService.getKitchenOrders();

                // Merge con órdenes offline pendientes
                List<OrderResponseRecord> offlineOrders = getOfflineOrdersForKitchen();
                List<OrderResponseRecord> merged = new ArrayList<>(orders);
                merged.addAll(offlineOrders);

                // Guardar en cache
                diskCacheService.save("kitchen-orders", merged);

                log.debug("Kitchen orders fetched ONLINE: {} orders", merged.size());
                return merged;

            } catch (Exception e) {
                log.error("Failed to fetch kitchen orders from AWS, using cache: {}", e.getMessage());
                // Continúa al flujo offline
            }
        }

        // Flujo OFFLINE: responder desde cache
        return getKitchenOrdersFromCache();
    }

    /**
     * Obtiene órdenes offline que deben mostrarse en cocina
     */
    private List<OrderResponseRecord> getOfflineOrdersForKitchen() {
        List<OrderResponseRecord> offlineOrders = new ArrayList<>();

        List<OfflineOrderRecord> pendingOrders = getOfflineOrdersIndex().stream()
                .filter(o -> !o.synced())
                .toList();

        for (OfflineOrderRecord offlineOrder : pendingOrders) {
            try {
                OrderRequestRecord request = offlineOrder.orderData();

                // Calcular totales desde items
                int subtotal = calculateSubtotal(request.items());
                int total = subtotal; // Simplificado, sin descuentos aún

                // Convertir items de request a response
                List<OrderItemResponseRecord> responseItems = new ArrayList<>();
                for (OrderItemRequestRecord item : request.items()) {
                    // Obtener nombre real desde cache (servicio local, siempre disponible)
                    String productName = orderMapper.getProductNameCached(item.productId());

                    int itemTotalPrice = item.quantity() * item.unitPrice();
                    responseItems.add(new OrderItemResponseRecord(
                            item.productId(),
                            productName, // Nombre real desde ProductClient con cache
                            item.quantity(),
                            item.unitPrice(),
                            itemTotalPrice,
                            item.instructions(),
                            item.comboGroup()
                    ));
                }

                OrderResponseRecord response = new OrderResponseRecord(
                        null, // idOrder - no hay ID aún
                        request.pagerColor(),
                        request.pagerNumber(),
                        offlineOrder.createdAt(),
                        subtotal,
                        total,
                        "PENDING",
                        request.paymentMethod(),
                        request.discountCode(),
                        null, // discountPercentage
                        null, // discountAmount
                        "No", // deliveredAt
                        null, // elapsedSecondsToDeliver
                        responseItems
                );

                offlineOrders.add(response);
            } catch (Exception e) {
                log.error("Failed to parse offline order {}: {}", offlineOrder.localOrderId(), e.getMessage());
            }
        }

        return offlineOrders;
    }

    /**
     * Calcula subtotal desde items
     */
    private int calculateSubtotal(List<OrderItemRequestRecord> items) {
        return items.stream()
                .mapToInt(item -> item.quantity() * item.unitPrice())
                .sum();
    }

    /**
     * Obtiene órdenes desde cache cuando AWS no está disponible
     */
    private List<OrderResponseRecord> getKitchenOrdersFromCache() {
        return diskCacheService.read("kitchen-orders", new TypeReference<List<OrderResponseRecord>>() {})
                .orElseGet(() -> {
                    log.warn("No cached kitchen orders available, returning offline orders only");
                    return getOfflineOrdersForKitchen();
                });
    }

    /**
     * Cuenta órdenes pendientes de sincronización
     */
    public long countPendingSyncOrders() {
        return getOfflineOrdersIndex().stream()
                .filter(o -> !o.synced())
                .count();
    }
}
