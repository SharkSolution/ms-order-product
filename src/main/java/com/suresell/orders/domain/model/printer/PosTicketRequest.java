package com.suresell.orders.domain.model.printer;


import java.math.BigDecimal;
import java.util.List;

public record PosTicketRequest(
        String businessName,
        String nit,
        String address,
        String phone,
        String resolutionDian,
        String resolutionRange,
        String ticketNumber,
        String cashierName,
        String customerName,
        String customerId,
        String dateTime,
        List<PosTicketItem> items,
        BigDecimal subtotal,
        BigDecimal tax, // IVA Total
        BigDecimal total,
        String paymentMethod,
        BigDecimal cashGiven, // Cuánto entregó el cliente (para calcular vueltas)
        BigDecimal change,    // Vueltas/Cambio
        String qrContent,
        String footerMessage
) {}

