package com.suresell.orders.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "Solicitud de cierre de caja")
public record ClosureRequest(
    @NotBlank(message="El nombre del cajero es obligatorio") 
    @Size(min=2, max=100, message="El nombre debe tener entre 2 y 100 caracteres") 
    @Schema(description = "Nombre del cajero", example = "Andrés Ramírez")
    String userName, 
    @NotNull(message="El total contado en efectivo es obligatorio") 
    @DecimalMin(value="0.0", inclusive=true, message="El total en efectivo debe ser mayor o igual a 0") 
    @Schema(description = "Total en efectivo contado físicamente", example = "500000")
    BigDecimal totalCountedCash, 
    @NotNull(message="El total contado en tarjeta es obligatorio") 
    @DecimalMin(value="0.0", inclusive=true, message="El total en tarjeta debe ser mayor o igual a 0") 
    @Schema(description = "Total en tarjeta contado físicamente", example = "150000")
    BigDecimal totalCountedCard, 
    @NotNull(message="El total contado en Nequi es obligatorio") 
    @DecimalMin(value="0.0", inclusive=true, message="El total en Nequi debe ser mayor o igual a 0") 
    @Schema(description = "Total en Nequi contado físicamente", example = "75000")
    BigDecimal totalCountedNequi, 
    @NotNull(message="El total contado en QR es obligatorio") 
    @DecimalMin(value="0.0", inclusive=true, message="El total en QR debe ser mayor o igual a 0") 
    @Schema(description = "Total en QR contado físicamente", example = "30000")
    BigDecimal totalCountedQr, 
    @Schema(description = "Notas adicionales sobre el cierre", example = "Sin novedades")
    String notes, 
    @Schema(description = "Base de caja para el próximo día", example = "100000")
    BigDecimal baseBalanceForNextDay
) {}
