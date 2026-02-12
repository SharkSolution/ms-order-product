package com.suresell.orders.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ClosureRequest(@NotBlank(message="El nombre del cajero es obligatorio") @Size(min=2, max=100, message="El nombre debe tener entre 2 y 100 caracteres") String userName, @NotNull(message="El total contado en efectivo es obligatorio") @DecimalMin(value="0.0", inclusive=true, message="El total en efectivo debe ser mayor o igual a 0") BigDecimal totalCountedCash, @NotNull(message="El total contado en tarjeta es obligatorio") @DecimalMin(value="0.0", inclusive=true, message="El total en tarjeta debe ser mayor o igual a 0") BigDecimal totalCountedCard, @NotNull(message="El total contado en Nequi es obligatorio") @DecimalMin(value="0.0", inclusive=true, message="El total en Nequi debe ser mayor o igual a 0") BigDecimal totalCountedNequi, @NotNull(message="El total contado en QR es obligatorio") @DecimalMin(value="0.0", inclusive=true, message="El total en QR debe ser mayor o igual a 0") BigDecimal totalCountedQr, String notes, BigDecimal baseBalanceForNextDay) {}
