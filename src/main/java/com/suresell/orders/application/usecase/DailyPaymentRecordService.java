package com.suresell.orders.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suresell.orders.application.dto.request.RegisterPaymentRecordRequest;
import com.suresell.orders.application.dto.responses.PaymentRecordResponse;
import com.suresell.orders.domain.model.DailyPaymentRecord;
import com.suresell.orders.domain.model.SyncOutbox;
import com.suresell.orders.domain.port.out.SyncOutboxRepositoryPort;
import com.suresell.orders.infrastructure.persistence.DailyPaymentRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class DailyPaymentRecordService {

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private final DailyPaymentRecordRepository repository;
    private final SyncOutboxRepositoryPort syncOutboxRepositoryPort;
    private final ObjectMapper objectMapper;

    public DailyPaymentRecordService(DailyPaymentRecordRepository repository,
                                     SyncOutboxRepositoryPort syncOutboxRepositoryPort,
                                     ObjectMapper objectMapper) {
        this.repository = repository;
        this.syncOutboxRepositoryPort = syncOutboxRepositoryPort;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentRecordResponse registerOrUpdate(RegisterPaymentRecordRequest request, String userName) {
        LocalDateTime now = LocalDateTime.now(BOGOTA_ZONE);

        Optional<DailyPaymentRecord> existing = repository.findByRecordDateAndPaymentMethod(
                request.recordDate(),
                request.paymentMethod().toUpperCase()
        );

        DailyPaymentRecord record;
        if (existing.isPresent()) {
            record = existing.get();
            record.setAmount(request.amount());
            record.setNotes(request.notes());
            record.setRegisteredBy(userName);
            record.setUpdatedAt(now);
            log.info("Actualizando registro de pago {} para fecha {} con monto {}",
                    request.paymentMethod(), request.recordDate(), request.amount());
        } else {
            record = new DailyPaymentRecord();
            record.setId(UUID.randomUUID());
            record.setRecordDate(request.recordDate());
            record.setPaymentMethod(request.paymentMethod().toUpperCase());
            record.setAmount(request.amount());
            record.setNotes(request.notes());
            record.setRegisteredBy(userName);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            log.info("Creando nuevo registro de pago {} para fecha {} con monto {}",
                    request.paymentMethod(), request.recordDate(), request.amount());
        }

        DailyPaymentRecord saved = repository.save(record);
        saveToOutbox(saved);
        return toResponse(saved);
    }

    private void saveToOutbox(DailyPaymentRecord record) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", "DAILY_PAYMENT_RECORD_SAVED");
            payload.put("record", Map.of(
                    "id", record.getId().toString(),
                    "recordDate", record.getRecordDate().toString(),
                    "paymentMethod", record.getPaymentMethod(),
                    "amount", record.getAmount(),
                    "registeredBy", record.getRegisteredBy() != null ? record.getRegisteredBy() : "",
                    "notes", record.getNotes() != null ? record.getNotes() : "",
                    "createdAt", record.getCreatedAt().toString(),
                    "updatedAt", record.getUpdatedAt() != null ? record.getUpdatedAt().toString() : record.getCreatedAt().toString()
            ));

            SyncOutbox outbox = new SyncOutbox();
            outbox.setAggregateType("DAILY_PAYMENT_RECORD");
            outbox.setAggregateUuid(record.getId());
            outbox.setAggregateId(0L);
            outbox.setEventType("DAILY_PAYMENT_RECORD_SAVED");
            outbox.setPayloadJson(objectMapper.writeValueAsString(payload));
            outbox.setStatus("PENDING");
            outbox.setAttempts(0);
            outbox.setNextRetryAt(System.currentTimeMillis());
            outbox.setCreatedAt(System.currentTimeMillis());
            outbox.setUpdatedAt(System.currentTimeMillis());
            syncOutboxRepositoryPort.save(outbox);
            log.info("Evento DAILY_PAYMENT_RECORD_SAVED encolado para sincronización: {}", record.getId());
        } catch (Exception e) {
            log.error("Error encolando sincronización de pago diario: {}", e.getMessage());
        }
    }

    public Optional<PaymentRecordResponse> getQrForDate(LocalDate date) {
        return repository.findQrByDate(date).map(this::toResponse);
    }

    public Optional<BigDecimal> getQrAmountForDate(LocalDate date) {
        return repository.findQrByDate(date).map(DailyPaymentRecord::getAmount);
    }

    public Optional<PaymentRecordResponse> getByDateAndMethod(LocalDate date, String paymentMethod) {
        return repository.findByRecordDateAndPaymentMethod(date, paymentMethod.toUpperCase())
                .map(this::toResponse);
    }

    public List<PaymentRecordResponse> getByDate(LocalDate date) {
        return repository.findByRecordDate(date).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<PaymentRecordResponse> getByDateRange(LocalDate startDate, LocalDate endDate) {
        return repository.findByDateRange(startDate, endDate).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void delete(LocalDate date, String paymentMethod) {
        repository.findByRecordDateAndPaymentMethod(date, paymentMethod.toUpperCase())
                .ifPresent(record -> {
                    repository.delete(record);
                    log.info("Eliminado registro de pago {} para fecha {}", paymentMethod, date);
                });
    }

    private PaymentRecordResponse toResponse(DailyPaymentRecord record) {
        return new PaymentRecordResponse(
                record.getId(),
                record.getRecordDate(),
                record.getPaymentMethod(),
                record.getAmount(),
                record.getRegisteredBy(),
                record.getNotes(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }
}
