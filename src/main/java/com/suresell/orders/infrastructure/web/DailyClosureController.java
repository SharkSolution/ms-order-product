package com.suresell.orders.infrastructure.web;
import com.suresell.orders.application.dto.ClosurePreviewResponse;
import com.suresell.orders.application.dto.ClosureRequest;
import com.suresell.orders.application.dto.ClosureResponse;
import com.suresell.orders.domain.port.in.DailyClosurePort; // Renamed from DailyClosureService
import com.suresell.orders.shared.export.DailyClosureExcelExporter; // Changed package
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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping(value={"/api/closures"})
public class DailyClosureController {
    private static final Logger log = LoggerFactory.getLogger(DailyClosureController.class);
    private final DailyClosurePort dailyClosurePort;
    private final DailyClosureExcelExporter excelExporter;
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    @GetMapping(value={"/export/excel"})
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
            return ((ResponseEntity.BodyBuilder)((ResponseEntity.BodyBuilder)ResponseEntity.ok().headers(headers)).contentType(MediaType.parseMediaType((String)"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))).body(new InputStreamResource(in));
        }
        catch (IOException e) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    @GetMapping(value={"/preview"})
    public ResponseEntity<ClosurePreviewResponse> getClosurePreview() {
        try {
            ClosurePreviewResponse preview = this.dailyClosurePort.getClosurePreview();
            return ResponseEntity.ok(preview);
        }
        catch (Exception e) {
            throw new RuntimeException("Error al generar preview de cierre: " + e.getMessage());
        }
    }
    @PostMapping
    public ResponseEntity<?> executeClosure(@Valid @RequestBody ClosureRequest request) {
        try {
            ClosureResponse response = this.dailyClosurePort.executeClosure(request);
            return ResponseEntity.status((HttpStatusCode)HttpStatus.CREATED).body((Object)response);
        }
        catch (IllegalArgumentException e) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.BAD_REQUEST).body(Map.of("error", "Datos inv\u00e1lidos", "message", e.getMessage()));
        }
        catch (Exception e) {
            return ResponseEntity.status((HttpStatusCode)HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error interno del servidor", "message", "No se pudo ejecutar el cierre: " + e.getMessage()));
        }
    }
    @GetMapping(value={"/history"})
    public ResponseEntity<List<ClosureResponse>> getAllClosures() {
        try {
            List closures = this.dailyClosurePort.getAllClosures();
            return ResponseEntity.ok(closures);
        }
        catch (Exception e) {
            throw new RuntimeException("Error al obtener historial de cierres: " + e.getMessage());
        }
    }
    @ExceptionHandler(value={MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        HashMap errors = new HashMap();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.status((HttpStatusCode)HttpStatus.BAD_REQUEST).body(Map.of("error", "Errores de validaci\u00f3n", "fields", errors));
    }
    public DailyClosureController(DailyClosurePort dailyClosurePort, DailyClosureExcelExporter excelExporter) {
        this.dailyClosurePort = dailyClosurePort;
        this.excelExporter = excelExporter;
    }
}
