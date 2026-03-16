package com.suresell.orders.infrastructure.web.adapter;

import com.suresell.orders.application.dto.OrderResponseRecord;
import com.suresell.orders.application.usecase.printer.PrintOrderTicketUseCase;
import com.suresell.orders.application.usecase.printer.PrintTicketUseCase;
import com.suresell.orders.domain.model.printer.PosTicketRequest;
import com.suresell.orders.domain.port.out.PrinterPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/printer")
@Tag(name = "Printer", description = "Operaciones de impresión y hardware POS")
public class PrinterController {

    private final PrintTicketUseCase printTicketUseCase;
    private final PrintOrderTicketUseCase printOrderTicketUseCase;
    private final PrinterPort printerPort;

    public PrinterController(PrintTicketUseCase printTicketUseCase, PrintOrderTicketUseCase printOrderTicketUseCase, PrinterPort printerPort) {
        this.printTicketUseCase = printTicketUseCase;
        this.printOrderTicketUseCase = printOrderTicketUseCase;
        this.printerPort = printerPort;
    }
    @PostMapping("/ticket")
    @Operation(summary = "Imprimir ticket de venta", description = "Envía una solicitud de impresión de ticket a la impresora térmica configurada.")
    public ResponseEntity<String> printTicket(@RequestBody PosTicketRequest request) {
        try {
            printTicketUseCase.execute(request);
            return ResponseEntity.ok("Impresión enviada correctamente");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error imprimiendo: " + e.getMessage());
        }
    }
    @PostMapping("/ticket-batch")
    @Operation(summary = "Imprimir lote de tickets", description = "Envía múltiples solicitudes de impresión en secuencia. Útil para imprimir todas las órdenes pendientes cuando falla la red.")
    public ResponseEntity<String> printTicketBatch(@RequestBody java.util.List<PosTicketRequest> requests) {
        try {
            for (PosTicketRequest request : requests) {
                printTicketUseCase.execute(request);
            }
            return ResponseEntity.ok(requests.size() + " impresiones enviadas correctamente");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error en lote de impresión: " + e.getMessage());
        }
    }
    @PostMapping("/drawer/open")
    @Operation(summary = "Abrir cajón monedero", description = "Envía el comando de pulso para abrir el cajón monedero conectado a la impresora.")
    public ResponseEntity<String> openDrawer() {
        try {
            printerPort.openDrawer();
            return ResponseEntity.ok("Comando de apertura enviado");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error abriendo cajón: " + e.getMessage());
        }
    }
    @GetMapping("/status")
    @Operation(summary = "Obtener estado de la impresora", description = "Verifica si la impresora está en línea y lista para recibir comandos.")
    public ResponseEntity<String> getStatus() {
        boolean ready = printerPort.isPrinterReady();
        return ready ? ResponseEntity.ok("ONLINE") : ResponseEntity.status(503).body("OFFLINE");
    }

    @PostMapping("/order-ticket")
    @Operation(summary = "Imprimir ticket de orden/emergencia", description = "Imprime un ticket operativo de preparación usando el objeto OrderResponseRecord estándar.")
    public ResponseEntity<String> printOrderTicket(@RequestBody OrderResponseRecord request) {
        try {
            printOrderTicketUseCase.execute(request);
            return ResponseEntity.ok("Comanda enviada correctamente");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error imprimiendo comanda: " + e.getMessage());
        }
    }
}
