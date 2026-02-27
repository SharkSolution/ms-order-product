package com.suresell.orders.application.usecase.printer;

import com.suresell.orders.domain.model.printer.PosTicketItem;
import com.suresell.orders.domain.model.printer.PosTicketRequest;
import com.suresell.orders.domain.port.out.PrinterPort;
import com.suresell.orders.infrastructure.printer.EscPosBuilder;
import org.springframework.stereotype.Service;
import java.text.DecimalFormat;

@Service
public class PrintTicketUseCase {

    private final PrinterPort printerPort;
    private final EscPosBuilder pos;
    // Formateador de dinero sin decimales excesivos (típico en COP)
    private static final DecimalFormat MONEY_FMT = new DecimalFormat("$ #,##0");

    public PrintTicketUseCase(PrinterPort printerPort, EscPosBuilder pos) {
        this.printerPort = printerPort;
        this.pos = pos;
    }

    public void execute(PosTicketRequest ticket) {
        byte[] receiptBytes = pos.buildReceipt(bos -> {

            // 1. ENCABEZADO
            pos.alignCenter(bos);
            pos.boldOn(bos);
            pos.textLn(bos, ticket.businessName().toUpperCase());
            pos.boldOff(bos);
            pos.textLn(bos, "NIT: " + ticket.nit());
            pos.textLn(bos, ticket.address());
            pos.textLn(bos, "Tel: " + ticket.phone());
            pos.textLn(bos, "Regimen Fiscal: IVA Responsable"); // O No Responsable

            pos.feed(bos, 1);

            // Datos DIAN (Obligatorio en Colombia)
            if(ticket.resolutionDian() != null) {
                pos.textLn(bos, "Autorización Numeración de Facturación");
                pos.textLn(bos, ticket.resolutionDian());
                pos.textLn(bos, ticket.resolutionRange());
            }

            pos.feed(bos, 1);
            pos.textLn(bos, "------------------------------------------");

            // 2. DATOS TRANSACCIÓN
            pos.alignLeft(bos);
            pos.textLn(bos, "Factura de Venta No: " + ticket.ticketNumber());
            pos.textLn(bos, "Fecha: " + ticket.dateTime());
            pos.textLn(bos, "Cajero: " + ticket.cashierName());
            if(ticket.customerName() != null) {
                pos.textLn(bos, "Cliente: " + ticket.customerName());
                pos.textLn(bos, "CC/NIT: " + ticket.customerId());
            }

            pos.textLn(bos, "------------------------------------------");

            // 3. DETALLE DE PRODUCTOS
            // Formato simplificado: CANT x PRECIO | TOTAL (Omitimos nombre largo en misma linea)
            pos.boldOn(bos);
            pos.textLn(bos, "ITEM / DESCRIPCION           TOTAL");
            pos.boldOff(bos);

            for (PosTicketItem item : ticket.items()) {
                // Linea 1: Nombre del producto
                pos.alignLeft(bos);
                pos.textLn(bos, item.name());

                // Linea 2: Cantidad x Precio Unitario ....... Total
                String quantityPrice = item.quantity() + " x " + MONEY_FMT.format(item.unitPrice());
                String totalStr = MONEY_FMT.format(item.total());

                // Justificar manualmente (Asumiendo ancho aprox 42-48 chars)
                String line = String.format("%-25s %15s", quantityPrice, totalStr);
                pos.textLn(bos, line);
            }

            pos.textLn(bos, "------------------------------------------");

            // 4. TOTALES
            pos.alignRight(bos);
            pos.textLn(bos, "SUBTOTAL: " + MONEY_FMT.format(ticket.subtotal()));
            // En Colombia se suele discriminar el IVA
            pos.textLn(bos, "IMPUESTOS (INC): " + MONEY_FMT.format(ticket.tax()));

            pos.boldOn(bos);
            pos.feed(bos, 1); // Espacio pequeño
            pos.textLn(bos, "TOTAL A PAGAR: " + MONEY_FMT.format(ticket.total()));
            pos.boldOff(bos);

            pos.feed(bos, 1);
            pos.textLn(bos, "Efectivo: " + MONEY_FMT.format(ticket.cashGiven()));
            pos.textLn(bos, "Cambio: " + MONEY_FMT.format(ticket.change()));

            // 5. PIE DE PÁGINA
            pos.feed(bos, 1);
            pos.alignCenter(bos);
            pos.textLn(bos, "Forma de Pago: " + ticket.paymentMethod());
            pos.feed(bos, 1);
            pos.feed(bos, 1);

            pos.textLn(bos, ticket.footerMessage());
            pos.textLn(bos, "Sistema POS desarrollado por");
            pos.textLn(bos, "--------- SureSell ---------");
            pos.textLn(bos, "\u00AD-- www.suresell.com.co --\u00AD");

            // QR (Opcional) - Implementación simplificada
            // Si necesitas QR real, debes usar los comandos byte específicos en EscPosBuilder

            pos.feed(bos, 2); // Espacio para corte
        });

        // Enviar a puerto
        printerPort.printBytes(receiptBytes);

        // Abrir cajón automáticamente tras imprimir
        printerPort.openDrawer();
    }
}