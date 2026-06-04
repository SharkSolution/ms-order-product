package com.suresell.orders.infrastructure.web;

import com.suresell.orders.application.dto.WaiterClosurePreviewResponse;
import com.suresell.orders.application.dto.WaiterClosureRequest;
import com.suresell.orders.application.dto.WaiterClosureResponse;
import com.suresell.orders.application.usecase.WaiterClosureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders/waiter-closures")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Tag(name = "Waiter Closures", description = "Endpoints para el Cierre de Caja de Meseros")
public class WaiterClosureController {

    private final WaiterClosureService waiterClosureService;

    public WaiterClosureController(WaiterClosureService waiterClosureService) {
        this.waiterClosureService = waiterClosureService;
    }

    @GetMapping("/preview")
    @Operation(summary = "Obtener vista previa del cierre de caja del mesero hoy")
    public ResponseEntity<WaiterClosurePreviewResponse> getWaiterClosurePreview(@RequestParam String waiterId) {
        WaiterClosurePreviewResponse preview = waiterClosureService.getWaiterClosurePreview(waiterId);
        return ResponseEntity.ok(preview);
    }

    @PostMapping
    @Operation(summary = "Registrar cierre de caja físico de un mesero")
    public ResponseEntity<WaiterClosureResponse> executeWaiterClosure(@Valid @RequestBody WaiterClosureRequest request) {
        WaiterClosureResponse response = waiterClosureService.executeWaiterClosure(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
