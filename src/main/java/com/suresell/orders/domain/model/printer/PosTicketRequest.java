package com.suresell.orders.domain.model.printer;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Solicitud completa de impresión de ticket POS")
public record PosTicketRequest(
        @Schema(description = "Nombre comercial del negocio", example = "SURESELL RESTAURANTE")
        String businessName,
        @Schema(description = "NIT del negocio", example = "900123456-1")
        String nit,
        @Schema(description = "Dirección del negocio", example = "Calle 123 # 45-67")
        String address,
        @Schema(description = "Teléfono de contacto", example = "300 123 4567")
        String phone,
        @Schema(description = "Resolución de facturación de la DIAN", example = "18764000001234 de 2024-01-01")
        String resolutionDian,
        @Schema(description = "Rango de numeración autorizado", example = "De PRE-1 a PRE-10000")
        String resolutionRange,
        @Schema(description = "Número de la factura o ticket", example = "INV-001")
        String ticketNumber,
        @Schema(description = "Nombre del cajero", example = "Cajero Principal")
        String cashierName,
        @Schema(description = "Nombre del cliente", example = "Juan Pérez")
        String customerName,
        @Schema(description = "Identificación del cliente (CC/NIT)", example = "1010202030")
        String customerId,
        @Schema(description = "Fecha y hora de la transacción", example = "2024-02-28 14:30:00")
        String dateTime,
        @Schema(description = "Lista de productos incluidos en el ticket")
        List<PosTicketItem> items,
        @Schema(description = "Subtotal de la venta", example = "50000")
        BigDecimal subtotal,
        @Schema(description = "Total de impuestos (IVA/INC)", example = "9500")
        BigDecimal tax, // IVA Total
        @Schema(description = "Total final a pagar", example = "59500")
        BigDecimal total,
        @Schema(description = "Método de pago utilizado", example = "CASH")
        String paymentMethod,
        @Schema(description = "Monto entregado por el cliente", example = "100000")
        BigDecimal cashGiven, // Cuánto entregó el cliente (para calcular vueltas)
        @Schema(description = "Cambio o vueltas entregadas", example = "40500")
        BigDecimal change,    // Vueltas/Cambio
        @Schema(description = "Contenido para generar el código QR", example = "URL_FACTURA_ELECTRONICA")
        String qrContent,
        @Schema(description = "Mensaje opcional al final del ticket", example = "Gracias por su compra")
        String footerMessage
) {}

