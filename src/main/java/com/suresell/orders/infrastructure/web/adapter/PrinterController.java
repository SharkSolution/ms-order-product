package com.suresell.orders.infrastructure.web.adapter;

import com.suresell.orders.application.usecase.printer.PrintTicketUseCase;
import com.suresell.orders.domain.model.printer.PosTicketRequest;
import com.suresell.orders.domain.port.out.PrinterPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/printer")
public class PrinterController {

    private final PrintTicketUseCase printTicketUseCase;
    private final PrinterPort printerPort;

    public PrinterController(PrintTicketUseCase printTicketUseCase, PrinterPort printerPort) {
        this.printTicketUseCase = printTicketUseCase;
        this.printerPort = printerPort;
    }

    @PostMapping("/ticket")
    public ResponseEntity<String> printTicket(@RequestBody PosTicketRequest request) {
        try {
            printTicketUseCase.execute(request);
            return ResponseEntity.ok("Impresión enviada correctamente");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error imprimiendo: " + e.getMessage());
        }
    }

    @PostMapping("/drawer/open")
    public ResponseEntity<String> openDrawer() {
        try {
            printerPort.openDrawer();
            return ResponseEntity.ok("Comando de apertura enviado");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error abriendo cajón: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        boolean ready = printerPort.isPrinterReady();
        return ready ? ResponseEntity.ok("ONLINE") : ResponseEntity.status(503).body("OFFLINE");
    }
}
