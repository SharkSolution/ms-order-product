package com.suresell.order.serivices;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servicio para verificar conectividad con servicios externos (AWS RDS, Products MS).
 * Cachea resultados para evitar checks costosos en cada request.
 */
@Service
@Slf4j
public class ConnectivityService {

    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;

    @Value("${products.service.url}")
    private String productsServiceUrl;

    @Value("${connectivity.check-interval-seconds:10}")
    private int checkIntervalSeconds;

    private final AtomicReference<Boolean> awsRdsAvailable = new AtomicReference<>(true);
    private final AtomicReference<Boolean> productsServiceAvailable = new AtomicReference<>(true);
    private final AtomicReference<LocalDateTime> lastCheck = new AtomicReference<>(LocalDateTime.now());

    public ConnectivityService(JdbcTemplate jdbcTemplate, RestTemplate restTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.restTemplate = restTemplate;
    }

    /**
     * Verifica si AWS RDS está disponible.
     * Cachea resultado para evitar checks constantes.
     */
    public boolean isAWSRdsAvailable() {
        if (shouldRefreshCheck()) {
            refreshConnectivity();
        }
        return awsRdsAvailable.get();
    }

    /**
     * Verifica si el servicio de productos está disponible
     */
    public boolean isProductsServiceAvailable() {
        if (shouldRefreshCheck()) {
            refreshConnectivity();
        }
        return productsServiceAvailable.get();
    }

    /**
     * Verifica si se debe refrescar el check de conectividad
     */
    private boolean shouldRefreshCheck() {
        long secondsSinceLastCheck = ChronoUnit.SECONDS.between(lastCheck.get(), LocalDateTime.now());
        return secondsSinceLastCheck >= checkIntervalSeconds;
    }

    /**
     * Refresca estado de conectividad de todos los servicios
     */
    private void refreshConnectivity() {
        lastCheck.set(LocalDateTime.now());

        // Check AWS RDS
        boolean rdsAvailable = checkAWSRds();
        awsRdsAvailable.set(rdsAvailable);

        // Check Products Service
        boolean productsAvailable = checkProductsService();
        productsServiceAvailable.set(productsAvailable);

        log.debug("Connectivity check: RDS={}, Products={}", rdsAvailable, productsAvailable);
    }

    /**
     * Verifica conectividad con AWS RDS mediante query simple
     */
    private boolean checkAWSRds() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            log.warn("AWS RDS not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifica conectividad con Products MS
     */
    private boolean checkProductsService() {
        try {
            // Intenta hacer un HEAD request o GET a un endpoint de health
            restTemplate.getForObject(productsServiceUrl + "/actuator/health", String.class);
            return true;
        } catch (Exception e) {
            log.warn("Products service not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Fuerza un refresh inmediato de conectividad (útil para health checks)
     */
    public ConnectivityStatus forceCheck() {
        refreshConnectivity();
        return new ConnectivityStatus(
                awsRdsAvailable.get(),
                productsServiceAvailable.get(),
                lastCheck.get()
        );
    }

    public record ConnectivityStatus(
            boolean awsRdsAvailable,
            boolean productsServiceAvailable,
            LocalDateTime lastCheck
    ) {}
}
