package com.suresell.orders.application.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
@Schema(description = "Respuesta detallada del resultado del cierre de caja")
public record ClosureResponse(
    @Schema(description = "ID único del cierre", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID id, 
    @Schema(description = "Nombre del cajero", example = "Andrés Ramírez")
    String userName, 
    @Schema(description = "Fecha y hora de apertura")
    LocalDateTime openingTime, 
    @Schema(description = "Fecha y hora de cierre")
    LocalDateTime closingTime,
    @Schema(description = "Total esperado en efectivo", example = "450000")
    BigDecimal totalExpectedCash, 
    @Schema(description = "Total esperado en tarjeta", example = "150000")
    BigDecimal totalExpectedCard, 
    @Schema(description = "Total esperado en Nequi", example = "80000")
    BigDecimal totalExpectedNequi,
    @Schema(description = "Total esperado en QR", example = "30000")
    BigDecimal totalExpectedQr, 
    @Schema(description = "Total total esperado sumando métodos", example = "710000")
    BigDecimal totalExpected, 
    @Schema(description = "Total contado en efectivo", example = "450000")
    BigDecimal totalCountedCash,
    @Schema(description = "Total contado en tarjeta", example = "150000")
    BigDecimal totalCountedCard, 
    @Schema(description = "Total contado en Nequi", example = "80000")
    BigDecimal totalCountedNequi, 
    @Schema(description = "Total contado en QR", example = "30000")
    BigDecimal totalCountedQr,
    @Schema(description = "Total total contado sumando métodos", example = "710000")
    BigDecimal totalCounted, 
    @Schema(description = "Diferencia entre esperado y contado", example = "0")
    BigDecimal differenceAmount, 
    @Schema(description = "Estado del cierre", example = "BALANCED", allowableValues = {"BALANCED", "POSITIVE_DIFF", "NEGATIVE_DIFF"})
    String status, 
    @Schema(description = "Notas adicionales", example = "Sin novedades")
    String notes,
    @Schema(description = "Mensaje informativo sobre el cierre", example = "✅ Cierre cuadrado. No hay diferencias.")
    String message, 
    @Schema(description = "Base de caja para el próximo día", example = "100000")
    BigDecimal previousBaseBalance) {
}
