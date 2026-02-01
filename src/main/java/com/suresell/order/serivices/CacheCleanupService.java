package com.suresell.order.serivices;

import com.suresell.order.model.record.OfflineOrderRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio de limpieza automática del cache.
 * Ejecuta limpieza diaria a las 19:00 hora Bogotá (cierre operacional).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CacheCleanupService {

    private final DiskCacheService diskCacheService;
    private final ResilientOrderService resilientOrderService;

    @Value("${cache.path:./cache}")
    private String cachePath;

    @Value("${cache.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    /**
     * Limpieza automática diaria a las 19:00 hora Bogotá (UTC-5).
     * Borra TODAS las órdenes sincronizadas, mantiene solo las pendientes.
     */
    @Scheduled(cron = "${cache.cleanup.cron:0 0 19 * * ?}", zone = "America/Bogota")
    public void dailyCleanup() {
        if (!cleanupEnabled) {
            log.debug("Cache cleanup deshabilitado");
            return;
        }

        log.info("=== INICIANDO LIMPIEZA DIARIA DEL CACHE (19:00) ===");

        try {
            CleanupStats stats = cleanupSyncedOrders();

            log.info("=== LIMPIEZA COMPLETADA ===");
            log.info("  Archivos eliminados: {}", stats.deletedFiles);
            log.info("  Órdenes pendientes: {}", stats.pendingOrders);
            log.info("  Espacio liberado: {}", formatBytes(stats.freedBytes));

        } catch (Exception e) {
            log.error("Error durante limpieza diaria del cache", e);
        }
    }

    /**
     * Ejecuta la limpieza de órdenes sincronizadas.
     * Retorna estadísticas del proceso.
     */
    public CleanupStats cleanupSyncedOrders() {
        List<OfflineOrderRecord> allOrders = resilientOrderService.getOfflineOrdersIndex();

        // Separar: pendientes vs sincronizadas
        List<OfflineOrderRecord> pendingOrders = allOrders.stream()
                .filter(o -> !o.synced())
                .collect(Collectors.toList());

        List<OfflineOrderRecord> syncedOrders = allOrders.stream()
                .filter(OfflineOrderRecord::synced)
                .toList();

        int deletedCount = 0;
        long freedBytes = 0;

        // Eliminar archivos de órdenes sincronizadas
        for (OfflineOrderRecord order : syncedOrders) {
            try {
                String filename = "offline-order-" + order.localOrderId() + ".json";
                Path filePath = Paths.get(cachePath, filename);

                if (Files.exists(filePath)) {
                    long fileSize = Files.size(filePath);
                    Files.delete(filePath);
                    deletedCount++;
                    freedBytes += fileSize;
                    log.debug("Eliminado: {}", filename);
                }

            } catch (IOException e) {
                log.error("Error al eliminar archivo de orden {}: {}",
                         order.localOrderId(), e.getMessage());
            }
        }

        // Actualizar índice: solo mantener órdenes pendientes
        diskCacheService.save("offline-orders-index", pendingOrders);

        return new CleanupStats(deletedCount, pendingOrders.size(), freedBytes);
    }

    /**
     * Limpieza TOTAL del cache (usar con precaución).
     * Borra TODO incluyendo órdenes pendientes.
     */
    public void clearAllCache() {
        log.warn("ADVERTENCIA: Eliminando TODO el cache (incluyendo pendientes)");
        diskCacheService.clearAllCache();
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    public record CleanupStats(int deletedFiles, int pendingOrders, long freedBytes) {}
}
