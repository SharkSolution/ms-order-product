package com.suresell.orders.application.usecase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogSyncScheduler {
    private final CatalogSyncService catalogSyncService;
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        log.info("App lista: Iniciando sincronización de catálogo inicial...");
        catalogSyncService.syncCatalogFromCloud();
    }
    @Scheduled(fixedDelayString = "${sync.catalog.fixed-delay-ms:900000}")
    public void scheduledSync() {
        log.info("Intervalo alcanzado: Sincronizando catálogo con la nube...");
        catalogSyncService.syncCatalogFromCloud();
    }
}
