package com.suresell.orders.infrastructure.web;

import com.suresell.orders.application.dto.request.RegisterPaymentRecordRequest;
import com.suresell.orders.application.dto.responses.PaymentRecordResponse;
import com.suresell.orders.application.usecase.DailyPaymentRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payment-records")
@Tag(name = "Daily Payment Records", description = "Registro de pagos diarios desde admin (QR, etc.)")
public class DailyPaymentRecordController {

    private final DailyPaymentRecordService service;

    public DailyPaymentRecordController(DailyPaymentRecordService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Registrar o actualizar un pago diario",
            description = "El admin registra el monto total de un método de pago para una fecha. Si ya existe, lo actualiza.")
    public ResponseEntity<PaymentRecordResponse> register(
            @Valid @RequestBody RegisterPaymentRecordRequest request,
            @RequestHeader(value = "X-User-Name", defaultValue = "Admin") String userName) {
        PaymentRecordResponse response = service.registerOrUpdate(request, userName);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/qr/today")
    @Operation(summary = "Obtener el monto QR registrado para hoy",
            description = "El POS consulta este endpoint para pre-llenar el valor QR en el cierre")
    public ResponseEntity<Map<String, Object>> getQrForToday() {
        LocalDate today = LocalDate.now();
        return service.getQrAmountForDate(today)
                .map(amount -> ResponseEntity.ok(buildQrResponse(today, amount, true)))
                .orElseGet(() -> ResponseEntity.ok(buildQrResponse(today, BigDecimal.ZERO, false)));
    }

    @GetMapping("/qr/{date}")
    @Operation(summary = "Obtener el monto QR registrado para una fecha específica")
    public ResponseEntity<Map<String, Object>> getQrForDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.getQrAmountForDate(date)
                .map(amount -> ResponseEntity.ok(buildQrResponse(date, amount, true)))
                .orElseGet(() -> ResponseEntity.ok(buildQrResponse(date, BigDecimal.ZERO, false)));
    }

    private Map<String, Object> buildQrResponse(LocalDate date, BigDecimal amount, boolean available) {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("date", date);
        response.put("paymentMethod", "QR");
        response.put("amount", amount);
        response.put("available", available);
        return response;
    }

    @GetMapping("/{date}")
    @Operation(summary = "Obtener todos los registros de pago para una fecha")
    public ResponseEntity<List<PaymentRecordResponse>> getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.getByDate(date));
    }

    @GetMapping("/{date}/{paymentMethod}")
    @Operation(summary = "Obtener un registro específico por fecha y método de pago")
    public ResponseEntity<PaymentRecordResponse> getByDateAndMethod(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable String paymentMethod) {
        return service.getByDateAndMethod(date, paymentMethod)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Obtener registros por rango de fechas")
    public ResponseEntity<List<PaymentRecordResponse>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(service.getByDateRange(startDate, endDate));
    }

    @DeleteMapping("/{date}/{paymentMethod}")
    @Operation(summary = "Eliminar un registro de pago")
    public ResponseEntity<Void> delete(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable String paymentMethod) {
        service.delete(date, paymentMethod);
        return ResponseEntity.noContent().build();
    }
}
