package com.suresell.orders.application.usecase;

import com.suresell.orders.application.dto.request.RegisterPaymentRecordRequest;
import com.suresell.orders.application.dto.responses.PaymentRecordResponse;
import com.suresell.orders.domain.model.DailyPaymentRecord;
import com.suresell.orders.infrastructure.persistence.DailyPaymentRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class DailyPaymentRecordService {

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private final DailyPaymentRecordRepository repository;

    public DailyPaymentRecordService(DailyPaymentRecordRepository repository) {
        this.repository = repository;
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
        return toResponse(saved);
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
