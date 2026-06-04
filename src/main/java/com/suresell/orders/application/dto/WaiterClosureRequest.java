package com.suresell.orders.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Schema(description = "Solicitud de cierre de caja para mesero")
public record WaiterClosureRequest(
    @NotBlank(message = "El ID del mesero es obligatorio")
    String waiterId,

    @NotBlank(message = "El nombre del mesero es obligatorio")
    String waiterName,

    @NotNull(message = "La base en efectivo es obligatoria")
    @DecimalMin(value = "0.0", inclusive = true, message = "La base debe ser mayor o igual a 0")
    BigDecimal baseCash,

    @NotNull(message = "El total contado en efectivo es obligatorio")
    @DecimalMin(value = "0.0", inclusive = true, message = "El total en efectivo debe ser mayor o igual a 0")
    BigDecimal totalCountedCash,

    @NotNull(message = "El total contado en tarjeta es obligatorio")
    @DecimalMin(value = "0.0", inclusive = true, message = "El total en tarjeta debe ser mayor o igual a 0")
    BigDecimal totalCountedCard,

    @NotNull(message = "El total contado en QR es obligatorio")
    @DecimalMin(value = "0.0", inclusive = true, message = "El total en QR debe ser mayor o igual a 0")
    BigDecimal totalCountedQr,

    String notes
) {}
