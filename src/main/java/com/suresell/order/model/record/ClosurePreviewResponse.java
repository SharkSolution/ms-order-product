package com.suresell.orders.application.dto;

import java.time.LocalDateTime;

public record ClosurePreviewResponse(LocalDateTime openingTime, LocalDateTime currentTime, int totalOrders, String message) {
}
