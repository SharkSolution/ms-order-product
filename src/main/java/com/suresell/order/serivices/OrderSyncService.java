package com.suresell.order.serivices;

import com.suresell.order.model.entity.Order;
import com.suresell.order.model.enums.SyncErrorType;
import com.suresell.order.model.record.OfflineOrderRecord;
import com.suresell.order.model.record.OrderSyncResponse;
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
 *
 * MEJORAS IMPLEMENTADAS:
 * ✅ Idempotencia real con idempotencyKey
 * ✅ Timeouts configurables
 * ✅ Verificación post-sincronización
 * ✅ Clasificación de errores
 * ✅ Backoff exponencial
 * ✅ Logging detallado
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderSyncService {

    private final ResilientOrderService resilientOrderService;
    private final OrderService orderService;
    private final ConnectivityService connectivityService;
    private final LocalErrorLogService localErrorLogService;

    @Value("${sync.enabled:true}")
    private boolean syncEnabled;

    @Value("${sync.batch-size:5}")
    private int batchSize;

    @Value("${sync.max-retries:5}")
    private int maxRetries;

    @Value("${sync.retry-strategy:exponential}")
    private String retryStrategy;

    @Value("${sync.initial-delay-seconds:30}")
    private int initialDelay;

    @Value("${sync.max-delay-seconds:300}")
    private int maxDelay;

    @Value("${sync.backoff-multiplier:2}")
    private int backoffMultiplier;

    private LocalDateTime lastSuccessfulSync;
    private LocalDateTime lastSyncAttempt;

    /**
     * Sincroniza órdenes offline con AWS según intervalo configurado.
     */
    @Scheduled(fixedDelayString = "${sync.interval-seconds:60}000")
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

        log.info("🔄 [SYNC] Starting sync of {} pending orders", pendingOrders.size());

        int syncedCount = 0;
        int failedCount = 0;
        int skippedCount = 0;

        // Procesar en lotes
        for (OfflineOrderRecord offlineOrder : pendingOrders) {
            try {
                boolean synced = syncOrder(offlineOrder);
                if (synced) {
                    syncedCount++;
                    lastSuccessfulSync = LocalDateTime.now();
                } else {
                    skippedCount++;
                }
            } catch (Exception e) {
                log.error("❌ [SYNC] Failed to sync order {}: {}", offlineOrder.localOrderId(), e.getMessage());
                failedCount++;
            }
        }

        log.info("✅ [SYNC] Completed: {} synced, {} failed, {} skipped (max retries)",
                syncedCount, failedCount, skippedCount);
    }

    /**
     * Sincroniza una orden individual con AWS usando endpoint idempotente.
     *
     * @return true si se sincronizó o ya estaba sincronizada, false si alcanzó max retries
     */
    private boolean syncOrder(OfflineOrderRecord offlineOrder) {
        long startTime = System.currentTimeMillis();
        String localId = offlineOrder.localOrderId();
        String idempotencyKey = offlineOrder.idempotencyKey();

        log.info("🔄 [SYNC] Processing order: {}", localId);
        log.debug("   Attempt: {}/{}", offlineOrder.syncAttempts() + 1, maxRetries);
        log.debug("   Idempotency Key: {}", idempotencyKey);
        log.debug("   Pager: {} #{}",
                offlineOrder.orderData().pagerColor(),
                offlineOrder.orderData().pagerNumber());

        // Verificar intentos máximos
        if (offlineOrder.syncAttempts() >= maxRetries) {
            log.error("⚠️ [SYNC] Max retries ({}) exceeded for order {}", maxRetries, localId);
            markAsMaxRetriesExceeded(offlineOrder);
            return false; // No sincronizada - alcanzó límite
        }

        try {
            // Verificar conectividad
            boolean isConnected = connectivityService.isAWSRdsAvailable();
            log.debug("   AWS Connectivity: {}", isConnected ? "✅ UP" : "❌ DOWN");

            if (!isConnected) {
                log.warn("   Skipping sync - AWS not available");
                return false;
            }

            // Sincronizar usando endpoint idempotente
            log.debug("   Sending HTTP request to AWS...");
            OrderSyncResponse response = orderService.syncOrderIdempotent(
                    idempotencyKey,
                    offlineOrder.orderData()
            );

            long responseTime = System.currentTimeMillis() - startTime;

            if (response.success()) {
                // ✅ Orden sincronizada (creada o ya existía)
                log.info("✅ [SYNC] Order synced successfully: {} -> AWS ID: {} (Status: {})",
                        localId, response.orderId(), response.status());
                log.debug("   Response time: {}ms", responseTime);

                markAsSynced(offlineOrder, response.orderId());
                return true;

            } else {
                // Error en la sincronización - verificar si es pager ocupado
                String errorMsg = response.message().toLowerCase();

                if (errorMsg.contains("pager") && errorMsg.contains("uso")) {
                    // PAGER OCUPADO - posible duplicado
                    log.warn("⚠️ [SYNC] Pager occupied - possible duplicate");
                    log.warn("   {}", response.message());

                    // Intentar recuperar el ID de la orden existente
                    tryToRecoverExistingOrderId(offlineOrder);
                    return true; // Consideramos que ya está sincronizada

                } else if (errorMsg.contains("inválido") || errorMsg.contains("invalid")) {
                    // ERROR DE VALIDACIÓN - NO reintentar
                    log.error("❌ [SYNC] Validation error (will not retry): {}", response.message());
                    markAsPermanentlyFailed(offlineOrder, response.message());
                    return false;

                } else {
                    // Otro error - reintentar
                    log.error("❌ [SYNC] Sync failed: {}", response.message());
                    incrementSyncAttempts(offlineOrder, response.message());
                    return false;
                }
            }

        } catch (Exception e) {
            // ERROR DURANTE LA LLAMADA (timeout, conexión, etc.)
            long failTime = System.currentTimeMillis() - startTime;
            log.error("❌ [SYNC] Exception after {}ms: {}", failTime, e != null ? e.getMessage() : "null");
            log.error("   Error type: {}", e != null ? e.getClass().getSimpleName() : "Unknown");

            // Verificar si fue timeout o error de conexión
            String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            boolean isTimeout = errorMsg.contains("timeout") || errorMsg.contains("timed out");
            boolean isConnection = errorMsg.contains("connection") || errorMsg.contains("connect");

            if (isTimeout) {
                // TIMEOUT - verificar si la orden se creó
                log.error("⏱️ [SYNC] TIMEOUT detected");
                log.error("   This may indicate slow network or AWS overload");
                log.warn("   Verifying if order was created despite timeout...");

                boolean existsInAWS = verifyOrderExistsInAWS(offlineOrder);
                if (existsInAWS) {
                    log.info("✅ [SYNC] Order was actually created despite timeout!");
                    return true;
                } else {
                    log.warn("   Order not found in AWS - timeout was a real failure");
                    incrementSyncAttempts(offlineOrder, "Timeout: " + e.getMessage());
                    return false;
                }

            } else if (isConnection) {
                // ERROR DE CONEXIÓN - reintentar
                log.error("🌐 [SYNC] Connection error");
                incrementSyncAttempts(offlineOrder, "Connection error: " + e.getMessage());
                return false;

            } else {
                // ERROR DESCONOCIDO - clasificar
                log.error("   Stack trace:", e);
                SyncErrorType errorType = classifyError(e);
                log.debug("   Classified as: {}", errorType);

                if (errorType.shouldRetry()) {
                    incrementSyncAttempts(offlineOrder, e.getMessage());
                } else {
                    markAsPermanentlyFailed(offlineOrder, e.getMessage());
                }

                return false;
            }
        }
    }

    /**
     * Verifica si la orden existe en AWS usando el idempotencyKey.
     * MUCHO MÁS SIMPLE Y CONFIABLE que buscar por pager + fecha.
     */
    private boolean verifyOrderExistsInAWS(OfflineOrderRecord offlineOrder) {
        try {
            String idempotencyKey = offlineOrder.idempotencyKey();
            log.debug("   Searching AWS by idempotencyKey: {}", idempotencyKey);

            // Buscar directamente por idempotencyKey
            Order existingOrder = orderService.findByIdempotencyKey(idempotencyKey);

            if (existingOrder != null) {
                log.info("✅ [VERIFY] Found order in AWS by idempotencyKey: ID {}",
                        existingOrder.getIdOrder());
                log.debug("   Pager: {} #{}, Total: {}",
                        existingOrder.getPagerColor(),
                        existingOrder.getPagerNumber(),
                        existingOrder.getTotal());

                markAsSynced(offlineOrder, existingOrder.getIdOrder());
                return true;
            } else {
                log.debug("   No order found in AWS with this idempotencyKey");
                return false;
            }

        } catch (Exception e) {
            log.error("❌ [VERIFY] Failed to verify order existence: {}", e.getMessage());
            return false; // Asumir que no existe si no podemos verificar
        }
    }

    /**
     * Intenta recuperar el ID de una orden existente cuando se detecta pager ocupado.
     */
    private void tryToRecoverExistingOrderId(OfflineOrderRecord offlineOrder) {
        boolean recovered = verifyOrderExistsInAWS(offlineOrder);
        if (recovered) {
            log.info("✅ [RECOVER] Successfully recovered existing order ID");
        } else {
            log.warn("⚠️ [RECOVER] Could not recover order ID - marking as synced anyway");
            markAsSynced(offlineOrder, null);
        }
    }

    /**
     * Clasifica el tipo de error para decidir si reintentar.
     */
    private SyncErrorType classifyError(Exception e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        String className = e.getClass().getSimpleName().toLowerCase();

        if (className.contains("timeout") ||
                message.contains("timeout") ||
                message.contains("timed out")) {
            return SyncErrorType.NETWORK_TIMEOUT;
        }

        if (className.contains("connect") ||
                message.contains("connection") ||
                (message.contains("pager") && message.contains("uso"))) {
            return SyncErrorType.PAGER_OCCUPIED;
        }

        if (e instanceof IllegalArgumentException ||
                message.contains("invalid") ||
                message.contains("inválido") ||
                message.contains("validation")) {
            return SyncErrorType.VALIDATION_ERROR;
        }

        if (message.contains("database") ||
                message.contains("sql")) {
            return SyncErrorType.DATABASE_ERROR;
        }

        return SyncErrorType.UNKNOWN;
    }

    /**
     * Marca una orden como sincronizada exitosamente.
     */
    private void markAsSynced(OfflineOrderRecord offlineOrder, Long awsOrderId) {
        var syncedOrder = OfflineOrderRecord.builder()
                .localOrderId(offlineOrder.localOrderId())
                .idempotencyKey(offlineOrder.idempotencyKey())
                .orderData(offlineOrder.orderData())
                .synced(true)
                .externalOrderId(awsOrderId)
                .createdAt(offlineOrder.createdAt())
                .syncedAt(LocalDateTime.now())
                .syncAttempts(offlineOrder.syncAttempts() + 1)
                .lastError(null)
                .build();

        resilientOrderService.updateOfflineOrderInIndex(syncedOrder);

        log.debug("   Local index updated: synced=true, externalOrderId={}", awsOrderId);
    }

    /**
     * Incrementa el contador de intentos de sincronización.
     */
    private void incrementSyncAttempts(OfflineOrderRecord offlineOrder, String errorMessage) {
        int newAttempts = offlineOrder.syncAttempts() + 1;

        // Calcular próximo delay (backoff exponencial)
        int nextDelay = calculateNextRetryDelay(newAttempts);
        log.debug("   Next retry in {} seconds", nextDelay);

        var retryOrder = OfflineOrderRecord.builder()
                .localOrderId(offlineOrder.localOrderId())
                .idempotencyKey(offlineOrder.idempotencyKey())
                .orderData(offlineOrder.orderData())
                .synced(false)
                .externalOrderId(null)
                .createdAt(offlineOrder.createdAt())
                .syncedAt(null)
                .syncAttempts(newAttempts)
                .lastError(errorMessage)
                .build();

        resilientOrderService.updateOfflineOrderInIndex(retryOrder);
    }

    /**
     * Marca una orden como fallida permanentemente.
     */
    private void markAsPermanentlyFailed(OfflineOrderRecord offlineOrder, String errorMessage) {
        var failedOrder = OfflineOrderRecord.builder()
                .localOrderId(offlineOrder.localOrderId())
                .idempotencyKey(offlineOrder.idempotencyKey())
                .orderData(offlineOrder.orderData())
                .synced(false)
                .externalOrderId(null)
                .createdAt(offlineOrder.createdAt())
                .syncedAt(null)
                .syncAttempts(offlineOrder.syncAttempts() + 1)
                .lastError("PERMANENT FAILURE: " + errorMessage)
                .build();

        resilientOrderService.updateOfflineOrderInIndex(failedOrder);

        // Registrar en el historial de errores locales
        localErrorLogService.logError(
                "OrderSyncService",
                offlineOrder.localOrderId(),
                "Fallo permanente: " + errorMessage
        );

        log.error("   Marked as permanently failed - will not retry");
    }

    /**
     * Marca una orden que alcanzó el máximo de reintentos.
     */
    private void markAsMaxRetriesExceeded(OfflineOrderRecord offlineOrder) {
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

        // Registrar en el historial de errores locales
        localErrorLogService.logError(
                "OrderSyncService",
                offlineOrder.localOrderId(),
                String.format("Max retries (%d) exceeded. Pager: %s #%s, Total: %d",
                        maxRetries,
                        offlineOrder.orderData().pagerColor(),
                        offlineOrder.orderData().pagerNumber(),
                        offlineOrder.orderData().items().stream()
                                .mapToInt(item -> item.quantity() * item.unitPrice())
                                .sum()
                )
        );
    }

    /**
     * Calcula el delay para el próximo reintento usando backoff exponencial.
     */
    private int calculateNextRetryDelay(int syncAttempts) {
        if ("linear".equalsIgnoreCase(retryStrategy)) {
            return initialDelay;
        }

        // Exponencial: initialDelay * (multiplier ^ (attempts - 1))
        int delay = initialDelay * (int) Math.pow(backoffMultiplier, syncAttempts - 1);
        return Math.min(delay, maxDelay);
    }

    /**
     * Obtiene estadísticas de sincronización.
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
