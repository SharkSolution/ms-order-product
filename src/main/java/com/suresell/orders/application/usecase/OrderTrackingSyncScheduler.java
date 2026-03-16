package com.suresell.orders.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderTrackingSyncScheduler {

    private final CatalogSyncService catalogSyncService;

    /**
     * Sincronización de ALTA FRECUENCIA para el estado de los Pagers.
     * Consulta Postgres cada 7 segundos para liberar pagers en SQLite.
     */
    @Scheduled(fixedDelayString = "${sync.tracking.fixed-delay-ms:7000}")
    public void syncOrderTracking() {
        // 1. Jalamos órdenes nuevas (App Móvil)
        catalogSyncService.syncOrdersFromCloud();
        
        // 2. Actualizamos tracking de las activas (Liberación de Pagers)
        catalogSyncService.syncActiveOrdersTrackingFromCloud();
    }
}
