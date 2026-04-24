package com.suresell.orders.application.usecase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suresell.orders.application.dto.request.RegisterPaymentRecordRequest;
import com.suresell.orders.application.dto.responses.PaymentRecordResponse;
import com.suresell.orders.domain.model.DailyPaymentRecord;
import com.suresell.orders.domain.port.out.SyncOutboxRepositoryPort;
import com.suresell.orders.infrastructure.persistence.DailyPaymentRecordRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class DailyPaymentRecordServiceTest {

    @Mock
    private DailyPaymentRecordRepository repository;

    @Mock
    private SyncOutboxRepositoryPort syncOutboxRepositoryPort;

    @Captor
    private ArgumentCaptor<DailyPaymentRecord> recordCaptor;

    private DailyPaymentRecordService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new DailyPaymentRecordService(repository, syncOutboxRepositoryPort, objectMapper);
    }

    private DailyPaymentRecord createRecord(LocalDate date, String method, BigDecimal amount) {
        DailyPaymentRecord record = new DailyPaymentRecord();
        record.setId(UUID.randomUUID());
        record.setRecordDate(date);
        record.setPaymentMethod(method);
        record.setAmount(amount);
        record.setRegisteredBy("Admin");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        return record;
    }

    @Nested
    @DisplayName("Register or Update Tests")
    class RegisterOrUpdateTests {

        @Test
        @DisplayName("Should create new record when none exists for date and method")
        void shouldCreateNewRecord() {
            // Arrange
            LocalDate today = LocalDate.now();
            RegisterPaymentRecordRequest request = new RegisterPaymentRecordRequest(
                    today, "QR", BigDecimal.valueOf(150000), "Reporte del día"
            );

            when(repository.findByRecordDateAndPaymentMethod(today, "QR"))
                    .thenReturn(Optional.empty());
            when(repository.save(any(DailyPaymentRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            PaymentRecordResponse response = service.registerOrUpdate(request, "AdminUser");

            // Assert
            verify(repository).save(recordCaptor.capture());
            DailyPaymentRecord saved = recordCaptor.getValue();

            assertNotNull(saved.getId());
            assertEquals(today, saved.getRecordDate());
            assertEquals("QR", saved.getPaymentMethod());
            assertEquals(BigDecimal.valueOf(150000), saved.getAmount());
            assertEquals("AdminUser", saved.getRegisteredBy());
            assertEquals("Reporte del día", saved.getNotes());
            assertNotNull(saved.getCreatedAt());
        }

        @Test
        @DisplayName("Should update existing record when one exists for date and method")
        void shouldUpdateExistingRecord() {
            // Arrange
            LocalDate today = LocalDate.now();
            DailyPaymentRecord existing = createRecord(today, "QR", BigDecimal.valueOf(100000));

            RegisterPaymentRecordRequest request = new RegisterPaymentRecordRequest(
                    today, "QR", BigDecimal.valueOf(150000), "Actualización"
            );

            when(repository.findByRecordDateAndPaymentMethod(today, "QR"))
                    .thenReturn(Optional.of(existing));
            when(repository.save(any(DailyPaymentRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            PaymentRecordResponse response = service.registerOrUpdate(request, "NewAdmin");

            // Assert
            verify(repository).save(recordCaptor.capture());
            DailyPaymentRecord saved = recordCaptor.getValue();

            assertEquals(existing.getId(), saved.getId()); // Same ID
            assertEquals(BigDecimal.valueOf(150000), saved.getAmount()); // Updated amount
            assertEquals("NewAdmin", saved.getRegisteredBy()); // Updated user
            assertEquals("Actualización", saved.getNotes()); // Updated notes
        }

        @Test
        @DisplayName("Should normalize payment method to uppercase")
        void shouldNormalizePaymentMethodToUppercase() {
            // Arrange
            LocalDate today = LocalDate.now();
            RegisterPaymentRecordRequest request = new RegisterPaymentRecordRequest(
                    today, "qr", BigDecimal.valueOf(50000), null
            );

            when(repository.findByRecordDateAndPaymentMethod(today, "QR"))
                    .thenReturn(Optional.empty());
            when(repository.save(any(DailyPaymentRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            service.registerOrUpdate(request, "Admin");

            // Assert
            verify(repository).findByRecordDateAndPaymentMethod(today, "QR");
            verify(repository).save(recordCaptor.capture());
            assertEquals("QR", recordCaptor.getValue().getPaymentMethod());
        }
    }

    @Nested
    @DisplayName("Query Tests")
    class QueryTests {

        @Test
        @DisplayName("Should return QR amount for specific date when exists")
        void shouldReturnQrAmountWhenExists() {
            // Arrange
            LocalDate today = LocalDate.now();
            DailyPaymentRecord record = createRecord(today, "QR", BigDecimal.valueOf(200000));

            when(repository.findQrByDate(today)).thenReturn(Optional.of(record));

            // Act
            Optional<BigDecimal> result = service.getQrAmountForDate(today);

            // Assert
            assertTrue(result.isPresent());
            assertEquals(BigDecimal.valueOf(200000), result.get());
        }

        @Test
        @DisplayName("Should return empty when no QR record exists for date")
        void shouldReturnEmptyWhenNoQrRecord() {
            // Arrange
            LocalDate today = LocalDate.now();
            when(repository.findQrByDate(today)).thenReturn(Optional.empty());

            // Act
            Optional<BigDecimal> result = service.getQrAmountForDate(today);

            // Assert
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return all records for a specific date")
        void shouldReturnAllRecordsForDate() {
            // Arrange
            LocalDate today = LocalDate.now();
            List<DailyPaymentRecord> records = List.of(
                    createRecord(today, "QR", BigDecimal.valueOf(100000)),
                    createRecord(today, "NEQUI", BigDecimal.valueOf(50000))
            );

            when(repository.findByRecordDate(today)).thenReturn(records);

            // Act
            List<PaymentRecordResponse> result = service.getByDate(today);

            // Assert
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("Should return records within date range")
        void shouldReturnRecordsInDateRange() {
            // Arrange
            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 1, 7);

            List<DailyPaymentRecord> records = List.of(
                    createRecord(LocalDate.of(2025, 1, 2), "QR", BigDecimal.valueOf(100000)),
                    createRecord(LocalDate.of(2025, 1, 5), "QR", BigDecimal.valueOf(150000))
            );

            when(repository.findByDateRange(startDate, endDate)).thenReturn(records);

            // Act
            List<PaymentRecordResponse> result = service.getByDateRange(startDate, endDate);

            // Assert
            assertEquals(2, result.size());
        }
    }

    @Nested
    @DisplayName("Delete Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should delete record when exists")
        void shouldDeleteRecordWhenExists() {
            // Arrange
            LocalDate today = LocalDate.now();
            DailyPaymentRecord record = createRecord(today, "QR", BigDecimal.valueOf(100000));

            when(repository.findByRecordDateAndPaymentMethod(today, "QR"))
                    .thenReturn(Optional.of(record));

            // Act
            service.delete(today, "QR");

            // Assert
            verify(repository).delete(record);
        }

        @Test
        @DisplayName("Should not throw when deleting non-existent record")
        void shouldNotThrowWhenDeletingNonExistent() {
            // Arrange
            LocalDate today = LocalDate.now();
            when(repository.findByRecordDateAndPaymentMethod(today, "QR"))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertDoesNotThrow(() -> service.delete(today, "QR"));
            verify(repository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("Response Mapping Tests")
    class ResponseMappingTests {

        @Test
        @DisplayName("Should map all fields correctly to response")
        void shouldMapAllFieldsToResponse() {
            // Arrange
            LocalDate today = LocalDate.now();
            LocalDateTime now = LocalDateTime.now();

            DailyPaymentRecord record = new DailyPaymentRecord();
            record.setId(UUID.randomUUID());
            record.setRecordDate(today);
            record.setPaymentMethod("QR");
            record.setAmount(BigDecimal.valueOf(175000));
            record.setRegisteredBy("TestAdmin");
            record.setNotes("Test notes");
            record.setCreatedAt(now);
            record.setUpdatedAt(now);

            when(repository.findQrByDate(today)).thenReturn(Optional.of(record));

            // Act
            Optional<PaymentRecordResponse> result = service.getQrForDate(today);

            // Assert
            assertTrue(result.isPresent());
            PaymentRecordResponse response = result.get();

            assertEquals(record.getId(), response.id());
            assertEquals(today, response.recordDate());
            assertEquals("QR", response.paymentMethod());
            assertEquals(BigDecimal.valueOf(175000), response.amount());
            assertEquals("TestAdmin", response.registeredBy());
            assertEquals("Test notes", response.notes());
            assertEquals(now, response.createdAt());
            assertEquals(now, response.updatedAt());
        }
    }
}
