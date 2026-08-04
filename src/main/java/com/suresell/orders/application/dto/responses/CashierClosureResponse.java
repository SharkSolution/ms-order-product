package com.suresell.orders.application.dto.responses;
import java.math.BigDecimal;
import java.util.Map;
/**
 * Resultado del cierre de caja.
 *
 * <p>{@code roundingAdjustment} es ADITIVO (contrato de compatibilidad, fase A):
 * un POS que no lo conozca simplemente lo ignora. Es lo que el negocio dejó de
 * cobrar al dividir cuentas de mesa entre comensales — se reporta como línea
 * propia porque un descuadre silencioso rompería la promesa de un cierre
 * auditable al peso.
 */
public record CashierClosureResponse(
        String status,
        String message,
        Map<String, BigDecimal> shortages,
        BigDecimal baseToKeep,
        BigDecimal amountToDeposit,
        BigDecimal roundingAdjustment
) {}