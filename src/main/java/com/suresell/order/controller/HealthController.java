package com.suresell.order.controller;

import com.suresell.order.serivices.ConnectivityService;
import com.suresell.order.serivices.DiskCacheService;
import com.suresell.order.serivices.OrderSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Endpoint de health check para monitorear estado del sistema offline-first
 */
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final ConnectivityService connectivityService;
    private final OrderSyncService orderSyncService;
    private final DiskCacheService diskCacheService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        var connectivity = connectivityService.forceCheck();
        var syncStats = orderSyncService.getStats();
        var cacheStats = diskCacheService.getStats();

        Map<String, Object> health = new HashMap<>();

        // Status general
        String mode = connectivity.awsRdsAvailable() ? "ONLINE" : "OFFLINE";
        String status = connectivity.awsRdsAvailable() ? "UP" : "DEGRADED";

        health.put("status", status);
        health.put("mode", mode);
        health.put("timestamp", LocalDateTime.now());

        // Conectividad
        Map<String, Boolean> connectivityMap = new HashMap<>();
        connectivityMap.put("aws_rds", connectivity.awsRdsAvailable());
        connectivityMap.put("aws_products_ms", connectivity.productsServiceAvailable());
        health.put("connectivity", connectivityMap);

        // Sincronización
        Map<String, Object> syncMap = new HashMap<>();
        syncMap.put("enabled", syncStats.enabled());
        syncMap.put("pending_orders", syncStats.pendingOrders());
        syncMap.put("last_successful_sync", syncStats.lastSuccessfulSync());
        syncMap.put("last_sync_attempt", syncStats.lastSyncAttempt());
        health.put("sync", syncMap);

        // Cache
        Map<String, Object> cacheMap = new HashMap<>();
        cacheMap.put("enabled", cacheStats.enabled());
        cacheMap.put("total_files", cacheStats.totalFiles());
        cacheMap.put("total_size", cacheStats.formatSize());
        cacheMap.put("cache_path", cacheStats.cachePath());
        health.put("cache", cacheMap);

        return ResponseEntity.ok(health);
    }
}
