package com.suresell.order.serivices;

import com.suresell.order.model.entity.Order;
import com.suresell.order.model.record.OfflineOrderRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio de sincronización automática de órdenes offline con AWS.
 * Se ejecuta en background cada X segundos para sincronizar órdenes pendientes.
 * Usa cache en disco (JSON) para persistencia sin base de datos adicional.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderSyncService {

    private final ResilientOrderService resilientOrderService;
    private final OrderService orderService;
    private final ConnectivityService connectivityService;

    @Value("${sync.enabled:true}")
    private boolean syncEnabled;

    @Value("${sync.batch-size:10}")
    private int batchSize;

    @Value("${sync.max-retries:5}")
    private int maxRetries;

    private LocalDateTime lastSuccessfulSync;
    private LocalDateTime lastSyncAttempt;

    /**
     * Sincroniza órdenes offline con AWS cada 30 segundos
     */
    @Scheduled(fixedDelayString = "${sync.interval-seconds:30}000")
    public void syncPendingOrders() {
        if (!syncEnabled) {
            log.debug("Sync disabled, skipping");
            return;
        }

        lastSyncAttempt = LocalDateTime.now();

        // Verificar conectividad
        if (!connectivityService.isAWSRdsAvailable()) {
            log.debug("AWS not available, skipping sync");
            return;
        }

        // Obtener órdenes pendientes desde índice JSON
        List<OfflineOrderRecord> pendingOrders = resilientOrderService.getOfflineOrdersIndex().stream()
                .filter(o -> !o.synced())
                .limit(batchSize)
                .toList();

        if (pendingOrders.isEmpty()) {
            log.debug("No pending orders to sync");
            return;
        }

        log.info("Starting sync of {} pending orders", pendingOrders.size());

        int syncedCount = 0;
        int failedCount = 0;

        // Procesar en lotes
        for (OfflineOrderRecord offlineOrder : pendingOrders) {
            try {
                syncOrder(offlineOrder);
                syncedCount++;
                lastSuccessfulSync = LocalDateTime.now();
            } catch (Exception e) {
                log.error("Failed to sync order {}: {}", offlineOrder.localOrderId(), e.getMessage());
                failedCount++;
                updateSyncError(offlineOrder, e.getMessage());
            }
        }

        log.info("Sync completed: {} synced, {} failed", syncedCount, failedCount);
    }

    /**
     * Sincroniza una orden individual con AWS
     */
    private void syncOrder(OfflineOrderRecord offlineOrder) {
        // Verificar intentos máximos
        if (offlineOrder.syncAttempts() >= maxRetries) {
            log.error("Max retries exceeded for order {}, marking as failed", offlineOrder.localOrderId());
            // Crear nueva versión marcada como fallida
            var failedOrder = OfflineOrderRecord.builder()
                    .localOrderId(offlineOrder.localOrderId())
                    .idempotencyKey(offlineOrder.idempotencyKey())
                    .orderData(offlineOrder.orderData())
                    .synced(false)
                    .externalOrderId(null)
                    .createdAt(offlineOrder.createdAt())
                    .syncedAt(null)
                    .syncAttempts(offlineOrder.syncAttempts())
                    .lastError("Max retries exceeded")
                    .build();
            resilientOrderService.updateOfflineOrderInIndex(failedOrder);
            return;
        }

        try {
            // Crear orden en AWS (retorna la orden con ID)
            Order syncedAwsOrder = orderService.createOrUpdateOrder(offlineOrder.orderData());

            // Marcar como sincronizada
            var syncedOrder = OfflineOrderRecord.builder()
                    .localOrderId(offlineOrder.localOrderId())
                    .idempotencyKey(offlineOrder.idempotencyKey())
                    .orderData(offlineOrder.orderData())
                    .synced(true)
                    .externalOrderId(syncedAwsOrder.getIdOrder())
                    .createdAt(offlineOrder.createdAt())
                    .syncedAt(LocalDateTime.now())
                    .syncAttempts(offlineOrder.syncAttempts() + 1)
                    .lastError(null)
                    .build();

            resilientOrderService.updateOfflineOrderInIndex(syncedOrder);

            log.info("Order synced successfully: {} -> AWS ID: {}",
                    offlineOrder.localOrderId(), syncedAwsOrder.getIdOrder());

        } catch (Exception e) {
            log.error("Error syncing order {}: {}", offlineOrder.localOrderId(), e.getMessage());

            // Incrementar contador de intentos
            var retryOrder = OfflineOrderRecord.builder()
                    .localOrderId(offlineOrder.localOrderId())
                    .idempotencyKey(offlineOrder.idempotencyKey())
                    .orderData(offlineOrder.orderData())
                    .synced(false)
                    .externalOrderId(null)
                    .createdAt(offlineOrder.createdAt())
                    .syncedAt(null)
                    .syncAttempts(offlineOrder.syncAttempts() + 1)
                    .lastError(e.getMessage())
                    .build();

            resilientOrderService.updateOfflineOrderInIndex(retryOrder);

            throw new RuntimeException("Sync failed", e);
        }
    }

    /**
     * Actualiza el error de sincronización
     */
    private void updateSyncError(OfflineOrderRecord offlineOrder, String errorMessage) {
        var errorOrder = OfflineOrderRecord.builder()
                .localOrderId(offlineOrder.localOrderId())
                .idempotencyKey(offlineOrder.idempotencyKey())
                .orderData(offlineOrder.orderData())
                .synced(false)
                .externalOrderId(null)
                .createdAt(offlineOrder.createdAt())
                .syncedAt(null)
                .syncAttempts(offlineOrder.syncAttempts() + 1)
                .lastError(errorMessage)
                .build();

        resilientOrderService.updateOfflineOrderInIndex(errorOrder);
    }

    /**
     * Obtiene estadísticas de sincronización
     */
    public SyncStats getStats() {
        long pendingCount = resilientOrderService.countPendingSyncOrders();

        return new SyncStats(
                syncEnabled,
                pendingCount,
                lastSuccessfulSync,
                lastSyncAttempt
        );
    }

    public record SyncStats(
            boolean enabled,
            long pendingOrders,
            LocalDateTime lastSuccessfulSync,
            LocalDateTime lastSyncAttempt
    ) {}
}


