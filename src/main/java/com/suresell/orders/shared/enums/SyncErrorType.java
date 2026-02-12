package com.suresell.orders.shared.enums;

public enum SyncErrorType {
    NETWORK_TIMEOUT(true, "Timeout de red - reintentando"),
    PAGER_OCCUPIED(false, "Pager ocupado - orden posiblemente ya existe"),
    VALIDATION_ERROR(false, "Error de validación de datos"),
    DATABASE_ERROR(true, "Error de base de datos - reintentando"),
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
