package com.suresell.orders.application.dto.responses;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Respuesta con información del registro de pago diario")
public record PaymentRecordResponse(
        @Schema(description = "ID del registro")
        UUID id,

        @Schema(description = "Fecha del registro")
        LocalDate recordDate,

        @Schema(description = "Método de pago")
        String paymentMethod,

        @Schema(description = "Monto registrado")
        BigDecimal amount,

        @Schema(description = "Usuario que registró")
        String registeredBy,

        @Schema(description = "Notas")
        String notes,

        @Schema(description = "Fecha de creación")
        LocalDateTime createdAt,

        @Schema(description = "Última actualización")
        LocalDateTime updatedAt
) {}
