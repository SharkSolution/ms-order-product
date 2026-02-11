package com.suresell.order.model.enums;

/**
 * Tipos de errores durante la sincronización.
 * Determina si se debe reintentar o no.
 */
public enum SyncErrorType {
    /**
     * Error de red o timeout - REINTENTAR
     */
    NETWORK_TIMEOUT(true, "Timeout de red - reintentando"),

    /**
     * Pager ocupado - posible duplicado - VERIFICAR
     */
    PAGER_OCCUPIED(false, "Pager ocupado - orden posiblemente ya existe"),

    /**
     * Error de validación - NO REINTENTAR
     */
    VALIDATION_ERROR(false, "Error de validación de datos"),

    /**
     * Error de base de datos - REINTENTAR
     */
    DATABASE_ERROR(true, "Error de base de datos - reintentando"),

    /**
     * Error desconocido - REINTENTAR CON CAUTELA
     */
    UNKNOWN(true, "Error desconocido");

    private final boolean shouldRetry;
    private final String description;

    SyncErrorType(boolean shouldRetry, String description) {
        this.shouldRetry = shouldRetry;
        this.description = description;
    }

    public boolean shouldRetry() {
        return shouldRetry;
    }

    public String getDescription() {
        return description;
    }
}
