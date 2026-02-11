package com.suresell.orders.application.dto;

public record OrderSyncResponse(
        boolean success,
        String status,
        Long orderId,
        String message
) {
    public static OrderSyncResponse created(Long orderId) {
        return new OrderSyncResponse(
                true,
                "CREATED",
                orderId,
                "Orden creada exitosamente"
        );
    }

    public static OrderSyncResponse alreadyExists(Long orderId) {
        return new OrderSyncResponse(
                true,
                "ALREADY_EXISTS",
                orderId,
                "Orden ya fue sincronizada previamente"
        );
    }

    public static OrderSyncResponse error(String message) {
        return new OrderSyncResponse(
                false,
                "ERROR",
                null,
                message
        );
    }
}
