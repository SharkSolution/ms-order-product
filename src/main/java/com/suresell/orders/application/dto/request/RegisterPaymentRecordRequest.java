package com.suresell.orders.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Solicitud para registrar un pago diario (QR, etc.) desde admin")
public record RegisterPaymentRecordRequest(
        @NotNull(message = "La fecha es obligatoria")
        @Schema(description = "Fecha del registro", example = "2025-01-15")
        LocalDate recordDate,

        @NotBlank(message = "El método de pago es obligatorio")
        @Schema(description = "Método de pago (QR, NEQUI, etc.)", example = "QR")
        String paymentMethod,

        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.0", inclusive = true, message = "El monto debe ser mayor o igual a 0")
        @Schema(description = "Monto total del día para este método de pago", example = "150000")
        BigDecimal amount,

        @Schema(description = "Notas adicionales", example = "Reporte de Bancolombia QR")
        String notes
) {}
