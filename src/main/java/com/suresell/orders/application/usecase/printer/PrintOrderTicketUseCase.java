package com.suresell.orders.application.usecase.printer;

import com.suresell.orders.application.dto.OrderResponseRecord;
import com.suresell.orders.application.dto.OrderItemResponseRecord;
import com.suresell.orders.domain.port.out.PrinterPort;
import com.suresell.orders.infrastructure.printer.EscPosBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class PrintOrderTicketUseCase {

    private final PrinterPort printerPort;
    private final EscPosBuilder pos;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public PrintOrderTicketUseCase(PrinterPort printerPort, EscPosBuilder pos) {
        this.printerPort = printerPort;
        this.pos = pos;
    }

    public void execute(OrderResponseRecord order) {
        log.info("Inicia Impresion de Ticket de Orden/Emergencia #{}", order.idOrder());

        byte[] receiptBytes = pos.buildReceipt(bos -> {

            pos.alignCenter(bos);

            if (order.pagerColor() != null && order.pagerNumber() != null) {
                pos.inverseOn(bos);
                pos.textSize(bos, 1, 1);
                pos.boldOn(bos);

                String pagerInfo = " " + order.pagerColor().toUpperCase() + " - " + order.pagerNumber() + " ";
                pos.textLn(bos, pagerInfo);

                pos.inverseOff(bos);
                pos.textSize(bos, 0, 0);
                pos.boldOff(bos);
            } else {
                pos.inverseOn(bos);
                pos.textSize(bos, 1, 1);
                pos.textLn(bos, " SIN RASTREADOR ");
                pos.inverseOff(bos);
                pos.textSize(bos, 0, 0);
            }

            pos.feed(bos, 1);

            pos.alignLeft(bos);
            pos.textLn(bos, "Orden #: " + order.idOrder());
            if (order.createdAt() != null) {
                pos.textLn(bos, "Fecha: " + order.createdAt().format(DATE_FMT));
            }
            pos.textLn(bos, "------------------------------------------");

            pos.boldOn(bos);
            pos.textLn(bos, "CANT  PRODUCTO");
            pos.boldOff(bos);
            pos.textLn(bos, "------------------------------------------");

            if (order.items() != null) {
                for (OrderItemResponseRecord item : order.items()) {
                    pos.boldOn(bos);
                    pos.textLn(bos, String.format(" %-4d %s", item.quantity(), item.nameProduct()));
                    pos.boldOff(bos);

                    if (item.instructions() != null && !item.instructions().isBlank()) {
                        pos.textLn(bos, "      * " + item.instructions());
                    }
                }
            }

            pos.textLn(bos, "------------------------------------------");
            pos.feed(bos, 3);
        });

        printerPort.printBytes(receiptBytes);
        log.info("Ticket de Orden impreso correctamente");
    }
}