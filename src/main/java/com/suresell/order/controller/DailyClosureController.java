/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.controller.DailyClosureController
 *  com.suresell.order.model.record.ClosurePreviewResponse
 *  com.suresell.order.model.record.ClosureRequest
 *  com.suresell.order.model.record.ClosureResponse
 *  com.suresell.order.serivices.DailyClosureService
 *  com.suresell.order.serivices.export.DailyClosureExcelExporter
 *  jakarta.validation.Valid
 *  lombok.Generated
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.core.io.InputStreamResource
 *  org.springframework.core.io.Resource
 *  org.springframework.http.HttpHeaders
 *  org.springframework.http.HttpStatus
 *  org.springframework.http.HttpStatusCode
 *  org.springframework.http.MediaType
 *  org.springframework.http.ResponseEntity
 *  org.springframework.http.ResponseEntity$BodyBuilder
 *  org.springframework.web.bind.MethodArgumentNotValidException
 *  org.springframework.web.bind.annotation.ExceptionHandler
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.RequestBody
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RestController
 */
package com.suresell.order.controller;
import com.suresell.order.model.record.ClosurePreviewResponse;
import com.suresell.order.model.record.ClosureRequest;
import com.suresell.order.model.record.ClosureResponse;
import com.suresell.order.serivices.DailyClosureService;
import com.suresell.order.serivices.export.DailyClosureExcelExporter;
import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Generated;
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
    @Generated
    private static final Logger log = LoggerFactory.getLogger(DailyClosureController.class);
    private final DailyClosureService closureService;
    private final DailyClosureExcelExporter excelExporter;
    @GetMapping(value={"/export/excel"})
    public ResponseEntity<Resource> exportClosuresToExcel() {
        log.info("GET /api/closures/export/excel - Solicitud de exportaci\u00f3n de cierres a Excel");
        try {
            List closures = this.closureService.getAllClosures();
            if (closures.isEmpty()) {
                log.info("No hay cierres para exportar. Devolviendo respuesta sin contenido.");
                return ResponseEntity.noContent().build();
            }
            ByteArrayInputStream in = this.excelExporter.export(closures);
            HttpHeaders headers = new HttpHeaders();
            String filename = "historial_cierres_caja_" + String.valueOf(LocalDate.now()) + ".xlsx";
            headers.add("Content-Disposition", "attachment; filename=" + filename);
            log.info("Exportaci\u00f3n a Excel generada exitosamente. Archivo: {}", (Object)filename);
            return ((ResponseEntity.BodyBuilder)((ResponseEntity.BodyBuilder)ResponseEntity.ok().headers(headers)).contentType(MediaType.parseMediaType((String)"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))).body(new InputStreamResource(in));
        }
        catch (IOException e) {
            log.error("Error al generar el archivo Excel de cierres: {}", (Object)e.getMessage(), (Object)e);
            return ResponseEntity.status((HttpStatusCode)HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    @GetMapping(value={"/preview"})
    public ResponseEntity<ClosurePreviewResponse> getClosurePreview() {
        log.info("GET /api/closures/preview - Obteniendo preview de cierre");
        try {
            ClosurePreviewResponse preview = this.closureService.getClosurePreview();
            return ResponseEntity.ok(preview);
        }
        catch (Exception e) {
            log.error("Error generando preview de cierre: {}", (Object)e.getMessage(), (Object)e);
            throw new RuntimeException("Error al generar preview de cierre: " + e.getMessage());
        }
    }
    @PostMapping
    public ResponseEntity<?> executeClosure(@Valid @RequestBody ClosureRequest request) {
        log.info("POST /api/closures - Ejecutando cierre para cajero: {}", (Object)request.userName());
        try {
            ClosureResponse response = this.closureService.executeClosure(request);
            log.info("Cierre ejecutado exitosamente. ID: {}, Cajero: {}, Status: {}, Diferencia: {}", new Object[]{response.id(), response.userName(), response.status(), response.differenceAmount()});
            return ResponseEntity.status((HttpStatusCode)HttpStatus.CREATED).body((Object)response);
        }
        catch (IllegalArgumentException e) {
            log.warn("Validaci\u00f3n fallida al ejecutar cierre: {}", (Object)e.getMessage());
            return ResponseEntity.status((HttpStatusCode)HttpStatus.BAD_REQUEST).body(Map.of("error", "Datos inv\u00e1lidos", "message", e.getMessage()));
        }
        catch (Exception e) {
            log.error("Error ejecutando cierre para cajero {}: {}", new Object[]{request.userName(), e.getMessage(), e});
            return ResponseEntity.status((HttpStatusCode)HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error interno del servidor", "message", "No se pudo ejecutar el cierre: " + e.getMessage()));
        }
    }
    @GetMapping(value={"/history"})
    public ResponseEntity<List<ClosureResponse>> getAllClosures() {
        log.info("GET /api/closures/history - Obteniendo historial de cierres");
        try {
            List closures = this.closureService.getAllClosures();
            return ResponseEntity.ok(closures);
        }
        catch (Exception e) {
            log.error("Error obteniendo historial de cierres: {}", (Object)e.getMessage(), (Object)e);
            throw new RuntimeException("Error al obtener historial de cierres: " + e.getMessage());
        }
    }
    @ExceptionHandler(value={MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        HashMap errors = new HashMap();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.status((HttpStatusCode)HttpStatus.BAD_REQUEST).body(Map.of("error", "Errores de validaci\u00f3n", "fields", errors));
    }
    @Generated
    public DailyClosureController(DailyClosureService closureService, DailyClosureExcelExporter excelExporter) {
        this.closureService = closureService;
        this.excelExporter = excelExporter;
    }
}
