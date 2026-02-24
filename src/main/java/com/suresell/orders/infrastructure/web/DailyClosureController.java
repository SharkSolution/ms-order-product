package com.suresell.orders.infrastructure.web;

import com.suresell.orders.application.dto.ClosurePreviewResponse;
import com.suresell.orders.application.dto.ClosureRequest;
import com.suresell.orders.application.dto.ClosureResponse;
import com.suresell.orders.application.dto.request.ExecuteClosureRequest;
import com.suresell.orders.application.dto.responses.CashierClosureResponse;
import com.suresell.orders.application.usecase.ExecuteDailyClosureUseCase;
import com.suresell.orders.domain.port.in.DailyClosurePort; // Renamed from DailyClosureService
import com.suresell.orders.shared.export.DailyClosureExcelExporter; // Changed package
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = {"/api/closures"})
@Tag(name = "Daily Closures", description = "Gestión de cierres diarios de caja")
public class DailyClosureController {

    private final DailyClosurePort dailyClosurePort;
    private final DailyClosureExcelExporter excelExporter;
    private final ExecuteDailyClosureUseCase executeDailyClosureUseCase;

    private static final Logger log = LoggerFactory.getLogger(DailyClosureController.class);
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    public DailyClosureController(DailyClosurePort dailyClosurePort, DailyClosureExcelExporter excelExporter, ExecuteDailyClosureUseCase executeDailyClosureUseCase) {
        this.dailyClosurePort = dailyClosurePort;
        this.excelExporter = excelExporter;
        this.executeDailyClosureUseCase = executeDailyClosureUseCase;
    }

    @GetMapping(value = {"/export/excel"})
    @Operation(summary = "Exportar historial de cierres a Excel")
    public ResponseEntity<Resource> exportClosuresToExcel() {
        try {
            List closures = this.dailyClosurePort.getAllClosures();
            if (closures.isEmpty()) {
                return ResponseEntity.noContent().build();
            }
            ByteArrayInputStream in = this.excelExporter.export(closures);
            HttpHeaders headers = new HttpHeaders();
            String filename = "historial_cierres_caja_" + String.valueOf(LocalDate.now(BOGOTA_ZONE)) + ".xlsx";
            headers.add("Content-Disposition", "attachment; filename=" + filename);
            return ((ResponseEntity.BodyBuilder) ((ResponseEntity.BodyBuilder) ResponseEntity.ok().headers(headers)).contentType(MediaType.parseMediaType((String) "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))).body(new InputStreamResource(in));
        } catch (IOException e) {
            return ResponseEntity.status((HttpStatusCode) HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping(value = {"/preview"})
    @Operation(summary = "Obtener preview del cierre diario")
    public ResponseEntity<ClosurePreviewResponse> getClosurePreview() {
        try {
            ClosurePreviewResponse preview = this.dailyClosurePort.getClosurePreview();
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            throw new RuntimeException("Error al generar preview de cierre: " + e.getMessage());
        }
    }

    @PostMapping
    @Operation(summary = "Ejecutar cierre de caja físico")
    public ResponseEntity<CashierClosureResponse> executeClosure(
            @Valid @RequestBody ExecuteClosureRequest request,
            @RequestHeader(value = "X-User-Name", defaultValue = "System") String userName) {

        CashierClosureResponse response = executeDailyClosureUseCase.execute(request, userName);
        return ResponseEntity.ok(response);
    }


    @GetMapping(value = {"/history"})
    @Operation(summary = "Listar historial de cierres")
    public ResponseEntity<List<ClosureResponse>> getAllClosures() {
        try {
            List closures = this.dailyClosurePort.getAllClosures();
            return ResponseEntity.ok(closures);
        } catch (Exception e) {
            throw new RuntimeException("Error al obtener historial de cierres: " + e.getMessage());
        }
    }

    @ExceptionHandler(value = {MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        HashMap errors = new HashMap();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.status((HttpStatusCode) HttpStatus.BAD_REQUEST).body(Map.of("error", "Errores de validaci\u00f3n", "fields", errors));
    }

}
