package com.suresell.orders.application.usecase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.suresell.orders.application.dto.dto.CashCountDetail;
import com.suresell.orders.application.dto.request.ExecuteClosureRequest;
import com.suresell.orders.application.dto.responses.CashierClosureResponse;
import com.suresell.orders.domain.model.DailyClosure;
import com.suresell.orders.domain.model.SyncOutbox;
import com.suresell.orders.domain.port.out.SyncOutboxRepositoryPort;
import com.suresell.orders.domain.service.CashflowCalculator;
import com.suresell.orders.infrastructure.persistence.DailyClosureRepository;
import com.suresell.orders.infrastructure.persistence.DailyPaymentRecordRepository;
import com.suresell.orders.infrastructure.persistence.OrderRepository;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ExecuteDailyClosureUseCaseTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DailyClosureRepository closureRepository;

    @Mock
    private SyncOutboxRepositoryPort syncOutboxRepositoryPort;

    @Mock
    private DailyPaymentRecordRepository dailyPaymentRecordRepository;

    private CashflowCalculator cashflowCalculator;
    private ObjectMapper objectMapper;
    private DailyPaymentRecordService dailyPaymentRecordService;
    private ExecuteDailyClosureUseCase useCase;

    @Captor
    private ArgumentCaptor<LocalDateTime> startTimeCaptor;

    @Captor
    private ArgumentCaptor<LocalDateTime> endTimeCaptor;

    @Captor
    private ArgumentCaptor<DailyClosure> closureCaptor;

    @BeforeEach
    void setUp() {
        cashflowCalculator = new CashflowCalculator();
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        dailyPaymentRecordService = new DailyPaymentRecordService(dailyPaymentRecordRepository);
        useCase = new ExecuteDailyClosureUseCase(
                orderRepository,
                closureRepository,
                cashflowCalculator,
                objectMapper,
                syncOutboxRepositoryPort,
                dailyPaymentRecordService
        );
        // Default: no admin QR record (fallback to manual input)
        // Using lenient() to allow overriding in specific tests
        lenient().when(dailyPaymentRecordRepository.findQrByDate(any(LocalDate.class)))
                .thenReturn(Optional.empty());
    }

    private CashCountDetail createCashDetail(int bill50k, int bill20k, int bill10k, int bill5k,
                                              int bill2k, int coin1000, int coin500) {
        return new CashCountDetail(0, bill50k, bill20k, bill10k, bill5k, bill2k, coin1000, coin500, 0, 0, 0);
    }

    private ExecuteClosureRequest createRequest(CashCountDetail cashDetail,
                                                 BigDecimal countedCard,
                                                 BigDecimal countedNequi,
                                                 BigDecimal countedQr) {
        return new ExecuteClosureRequest(
                cashDetail,
                null, // countedCash - se calcula del cashDetail
                countedCard,
                countedNequi,
                countedQr,
                "Test closure",
                "seller-001"
        );
    }

    private List<Object[]> createSalesTotals(BigDecimal cash, BigDecimal card,
                                              BigDecimal nequi, BigDecimal qr) {
        List<Object[]> totals = new ArrayList<>();
        if (cash != null && cash.compareTo(BigDecimal.ZERO) > 0) {
            totals.add(new Object[]{"CASH", cash});
        }
        if (card != null && card.compareTo(BigDecimal.ZERO) > 0) {
            totals.add(new Object[]{"CARD", card});
        }
        if (nequi != null && nequi.compareTo(BigDecimal.ZERO) > 0) {
            totals.add(new Object[]{"NEQUI", nequi});
        }
        if (qr != null && qr.compareTo(BigDecimal.ZERO) > 0) {
            totals.add(new Object[]{"QR", qr});
        }
        return totals;
    }

    private void mockNoAdminQrRecord() {
        when(dailyPaymentRecordRepository.findQrByDate(any(LocalDate.class)))
                .thenReturn(Optional.empty());
    }

    private com.suresell.orders.domain.model.DailyPaymentRecord createAdminQrRecord(BigDecimal amount) {
        com.suresell.orders.domain.model.DailyPaymentRecord record = new com.suresell.orders.domain.model.DailyPaymentRecord();
        record.setId(java.util.UUID.randomUUID());
        record.setRecordDate(LocalDate.now());
        record.setPaymentMethod("QR");
        record.setAmount(amount);
        record.setRegisteredBy("Admin");
        record.setCreatedAt(LocalDateTime.now());
        return record;
    }

    @Nested
    @DisplayName("Query Parameter Tests")
    class QueryParameterTests {

        @Test
        @DisplayName("Should pass LocalDateTime parameters to repository query")
        void shouldPassLocalDateTimeParametersToQuery() {
            // Arrange
            CashCountDetail cashDetail = createCashDetail(1, 0, 0, 0, 0, 0, 0); // 50,000
            ExecuteClosureRequest request = createRequest(cashDetail, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            LocalDateTime lastClosingTime = LocalDateTime.of(2025, 1, 15, 8, 0, 0);
            when(closureRepository.findLastClosingTimeByUser("seller-001"))
                    .thenReturn(Optional.of(lastClosingTime));
            when(orderRepository.sumTotalsByPaymentMethodAndSeller(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(createSalesTotals(BigDecimal.valueOf(50000), null, null, null));
            when(closureRepository.findFirstByOrderByClosingTimeDesc())
                    .thenReturn(Optional.empty());
            when(closureRepository.save(any(DailyClosure.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(syncOutboxRepositoryPort.save(any(SyncOutbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            useCase.execute(request, "TestUser");

            // Assert
            verify(orderRepository).sumTotalsByPaymentMethodAndSeller(
                    startTimeCaptor.capture(),
                    endTimeCaptor.capture()
            );

            LocalDateTime capturedStart = startTimeCaptor.getValue();
            LocalDateTime capturedEnd = endTimeCaptor.getValue();

            assertEquals(lastClosingTime, capturedStart);
            assertNotNull(capturedEnd);
            assertTrue(capturedEnd.isAfter(capturedStart));
            // Verify it's a LocalDateTime, not epoch millis
            assertTrue(capturedStart.getYear() >= 2025);
        }
    }

    @Nested
    @DisplayName("Balanced Closure Tests")
    class BalancedClosureTests {

        @Test
        @DisplayName("Should return SUCCESS when cash counted equals expected sales plus previous base")
        void shouldReturnSuccessWhenBalanced() {
            // Arrange: Vendimos 50,000 en efectivo, base anterior 0, contamos 50,000
            CashCountDetail cashDetail = createCashDetail(1, 0, 0, 0, 0, 0, 0); // 1 billete de 50k = 50,000
            ExecuteClosureRequest request = createRequest(cashDetail, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            when(closureRepository.findLastClosingTimeByUser("seller-001"))
                    .thenReturn(Optional.of(LocalDateTime.now().minusHours(8)));
            when(orderRepository.sumTotalsByPaymentMethodAndSeller(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(createSalesTotals(BigDecimal.valueOf(50000), null, null, null));
            when(closureRepository.findFirstByOrderByClosingTimeDesc())
                    .thenReturn(Optional.empty()); // No previous base
            when(closureRepository.save(any(DailyClosure.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(syncOutboxRepositoryPort.save(any(SyncOutbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            CashierClosureResponse response = useCase.execute(request, "TestUser");

            // Assert
            assertEquals("SUCCESS", response.status());
            assertTrue(response.shortages().isEmpty());
        }

        @Test
        @DisplayName("Should return SUCCESS when counted equals expected with previous base included")
        void shouldReturnSuccessWithPreviousBase() {
            // Arrange: Vendimos 30,000 en efectivo, base anterior 20,000, contamos 50,000 (30k + 20k)
            CashCountDetail cashDetail = createCashDetail(1, 0, 0, 0, 0, 0, 0); // 50,000
            ExecuteClosureRequest request = createRequest(cashDetail, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            DailyClosure previousClosure = new DailyClosure();
            previousClosure.setBaseBalanceForNextDay(BigDecimal.valueOf(20000));

            when(closureRepository.findLastClosingTimeByUser("seller-001"))
                    .thenReturn(Optional.of(LocalDateTime.now().minusHours(8)));
            when(orderRepository.sumTotalsByPaymentMethodAndSeller(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(createSalesTotals(BigDecimal.valueOf(30000), null, null, null));
            when(closureRepository.findFirstByOrderByClosingTimeDesc())
                    .thenReturn(Optional.of(previousClosure)); // Previous base = 20,000
            when(closureRepository.save(any(DailyClosure.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(syncOutboxRepositoryPort.save(any(SyncOutbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            CashierClosureResponse response = useCase.execute(request, "TestUser");

            // Assert
            assertEquals("SUCCESS", response.status());
            assertTrue(response.shortages().isEmpty());
        }
    }

    @Nested
    @DisplayName("Shortage Detection Tests")
    class ShortageDetectionTests {

        @Test
        @DisplayName("Should detect cash shortage when counted is less than expected")
        void shouldDetectCashShortage() {
            // Arrange: Vendimos 50,000 pero solo contamos 40,000 (faltante de 10,000)
            CashCountDetail cashDetail = createCashDetail(0, 2, 0, 0, 0, 0, 0); // 2 billetes de 20k = 40,000
            ExecuteClosureRequest request = createRequest(cashDetail, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            when(closureRepository.findLastClosingTimeByUser("seller-001"))
                    .thenReturn(Optional.of(LocalDateTime.now().minusHours(8)));
            when(orderRepository.sumTotalsByPaymentMethodAndSeller(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(createSalesTotals(BigDecimal.valueOf(50000), null, null, null));
            when(closureRepository.findFirstByOrderByClosingTimeDesc())
                    .thenReturn(Optional.empty());
            when(closureRepository.save(any(DailyClosure.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(syncOutboxRepositoryPort.save(any(SyncOutbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            CashierClosureResponse response = useCase.execute(request, "TestUser");

            // Assert
            assertEquals("SHORTAGE", response.status());
            assertFalse(response.shortages().isEmpty());
            assertTrue(response.shortages().containsKey("Efectivo"));
            assertEquals(BigDecimal.valueOf(-10000), response.shortages().get("Efectivo"));
        }

        @Test
        @DisplayName("Should detect card shortage when counted is less than expected")
        void shouldDetectCardShortage() {
            // Arrange: Vendimos 30,000 en tarjeta pero reportamos 25,000
            CashCountDetail cashDetail = createCashDetail(0, 0, 0, 0, 0, 0, 0); // 0 efectivo
            ExecuteClosureRequest request = createRequest(cashDetail, BigDecimal.valueOf(25000), BigDecimal.ZERO, BigDecimal.ZERO);

            when(closureRepository.findLastClosingTimeByUser("seller-001"))
                    .thenReturn(Optional.of(LocalDateTime.now().minusHours(8)));
            when(orderRepository.sumTotalsByPaymentMethodAndSeller(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(createSalesTotals(null, BigDecimal.valueOf(30000), null, null));
            when(closureRepository.findFirstByOrderByClosingTimeDesc())
                    .thenReturn(Optional.empty());
            when(closureRepository.save(any(DailyClosure.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(syncOutboxRepositoryPort.save(any(SyncOutbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            CashierClosureResponse response = useCase.execute(request, "TestUser");

            // Assert
            assertEquals("SHORTAGE", response.status());
            assertTrue(response.shortages().containsKey("Tarjeta"));
            assertEquals(BigDecimal.valueOf(-5000), response.shortages().get("Tarjeta"));
        }

        @Test
        @DisplayName("Should detect multiple shortages across payment methods")
        void shouldDetectMultipleShortages() {
            // Arrange: Faltantes en efectivo y nequi
            CashCountDetail cashDetail = createCashDetail(0, 1, 0, 0, 0, 0, 0); // 20,000 (esperado 30,000)
            ExecuteClosureRequest request = createRequest(
                    cashDetail,
                    BigDecimal.valueOf(10000), // Card OK
                    BigDecimal.valueOf(5000),  // Nequi: esperado 10,000, contado 5,000
                    BigDecimal.ZERO
            );

            when(closureRepository.findLastClosingTimeByUser("seller-001"))
                    .thenReturn(Optional.of(LocalDateTime.now().minusHours(8)));
            when(orderRepository.sumTotalsByPaymentMethodAndSeller(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(createSalesTotals(
                            BigDecimal.valueOf(30000),  // Cash expected
                            BigDecimal.valueOf(10000),  // Card expected
                            BigDecimal.valueOf(10000),  // Nequi expected
                            null
                    ));
            when(closureRepository.findFirstByOrderByClosingTimeDesc())
                    .thenReturn(Optional.empty());
            when(closureRepository.save(any(DailyClosure.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(syncOutboxRepositoryPort.save(any(SyncOutbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            CashierClosureResponse response = useCase.execute(request, "TestUser");

            // Assert
            assertEquals("SHORTAGE", response.status());
            assertEquals(2, response.shortages().size());
            assertTrue(response.shortages().containsKey("Efectivo"));
            assertTrue(response.shortages().containsKey("Nequi"));
        }
    }

    @Nested
    @DisplayName("Surplus Detection Tests")
    class SurplusDetectionTests {

        @Test
        @DisplayName("Should return SUCCESS with surplus (not marked as shortage)")
        void shouldReturnSuccessWithSurplus() {
            // Arrange: Vendimos 40,000 pero contamos 50,000 (sobrante)
            CashCountDetail cashDetail = createCashDetail(1, 0, 0, 0, 0, 0, 0); // 50,000
            ExecuteClosureRequest request = createRequest(cashDetail, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            when(closureRepository.findLastClosingTimeByUser("seller-001"))
                    .thenReturn(Optional.of(LocalDateTime.now().minusHours(8)));
            when(orderRepository.sumTotalsByPaymentMethodAndSeller(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(createSalesTotals(BigDecimal.valueOf(40000), null, null, null));
            when(closureRepository.findFirstByOrderByClosingTimeDesc())
                    .thenReturn(Optional.empty());
            when(closureRepository.save(any(DailyClosure.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(syncOutboxRepositoryPort.save(any(SyncOutbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            CashierClosureResponse response = useCase.execute(request, "TestUser");

            // Assert
            assertEquals("SUCCESS", response.status());
            assertTrue(response.shortages().isEmpty()); // Surplus is not a shortage
        }
    }

    @Nested
    @DisplayName("Closure Entity Persistence Tests")
    class ClosurePersistenceTests {

        @Test
        @DisplayName("Should save closure with correct expected and counted values")
        void shouldSaveClosureWithCorrectValues() {
            // Arrange
            CashCountDetail cashDetail = createCashDetail(1, 0, 0, 0, 0, 0, 0); // 50,000
            ExecuteClosureRequest request = createRequest(
                    cashDetail,
                    BigDecimal.valueOf(20000),
                    BigDecimal.valueOf(10000),
                    BigDecimal.valueOf(5000)
            );

            when(closureRepository.findLastClosingTimeByUser("seller-001"))
                    .thenReturn(Optional.of(LocalDateTime.now().minusHours(8)));
            when(orderRepository.sumTotalsByPaymentMethodAndSeller(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(createSalesTotals(
                            BigDecimal.valueOf(50000),
                            BigDecimal.valueOf(20000),
                            BigDecimal.valueOf(10000),
                            BigDecimal.valueOf(5000)
                    ));
            when(closureRepository.findFirstByOrderByClosingTimeDesc())
                    .thenReturn(Optional.empty());
            when(closureRepository.save(any(DailyClosure.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(syncOutboxRepositoryPort.save(any(SyncOutbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            useCase.execute(request, "TestUser");

            // Assert
            verify(closureRepository).save(closureCaptor.capture());
            DailyClosure savedClosure = closureCaptor.getValue();

            assertEquals(BigDecimal.valueOf(50000), savedClosure.getTotalExpectedCash());
            assertEquals(BigDecimal.valueOf(20000), savedClosure.getTotalExpectedCard());
            assertEquals(BigDecimal.valueOf(10000), savedClosure.getTotalExpectedNequi());
            assertEquals(BigDecimal.valueOf(5000), savedClosure.getTotalExpectedQr());

            assertEquals(BigDecimal.valueOf(50000), savedClosure.getTotalCountedCash());
            assertEquals(BigDecimal.valueOf(20000), savedClosure.getTotalCountedCard());
            assertEquals(BigDecimal.valueOf(10000), savedClosure.getTotalCountedNequi());
            assertEquals(BigDecimal.valueOf(5000), savedClosure.getTotalCountedQr());

            assertEquals("BALANCED", savedClosure.getStatus());
        }

        @Test
        @DisplayName("Should save closure with NEGATIVE_DIFF status when shortage detected")
        void shouldSaveClosureWithNegativeDiffStatus() {
            // Arrange: Faltante
            CashCountDetail cashDetail = createCashDetail(0, 1, 0, 0, 0, 0, 0); // 20,000 (esperado 30,000)
            ExecuteClosureRequest request = createRequest(cashDetail, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            when(closureRepository.findLastClosingTimeByUser("seller-001"))
                    .thenReturn(Optional.of(LocalDateTime.now().minusHours(8)));
            when(orderRepository.sumTotalsByPaymentMethodAndSeller(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(createSalesTotals(BigDecimal.valueOf(30000), null, null, null));
            when(closureRepository.findFirstByOrderByClosingTimeDesc())
                    .thenReturn(Optional.empty());
            when(closureRepository.save(any(DailyClosure.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(syncOutboxRepositoryPort.save(any(SyncOutbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            useCase.execute(request, "TestUser");

            // Assert
            verify(closureRepository).save(closureCaptor.capture());
            DailyClosure savedClosure = closureCaptor.getValue();

            assertEquals("NEGATIVE_DIFF", savedClosure.getStatus());
            assertEquals("Faltante Detectado", savedClosure.getStatusMessage());
            assertEquals(BigDecimal.valueOf(-10000), savedClosure.getDifferenceCash());
        }

        @Test
        @DisplayName("Should save closure with POSITIVE_DIFF status when surplus detected")
        void shouldSaveClosureWithPositiveDiffStatus() {
            // Arrange: Sobrante
            CashCountDetail cashDetail = createCashDetail(1, 0, 0, 0, 0, 0, 0); // 50,000 (esperado 30,000)
            ExecuteClosureRequest request = createRequest(cashDetail, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            when(closureRepository.findLastClosingTimeByUser("seller-001"))
                    .thenReturn(Optional.of(LocalDateTime.now().minusHours(8)));
            when(orderRepository.sumTotalsByPaymentMethodAndSeller(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(createSalesTotals(BigDecimal.valueOf(30000), null, null, null));
            when(closureRepository.findFirstByOrderByClosingTimeDesc())
                    .thenReturn(Optional.empty());
            when(closureRepository.save(any(DailyClosure.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(syncOutboxRepositoryPort.save(any(SyncOutbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            useCase.execute(request, "TestUser");

            // Assert
            verify(closureRepository).save(closureCaptor.capture());
            DailyClosure savedClosure = closureCaptor.getValue();

            assertEquals("POSITIVE_DIFF", savedClosure.getStatus());
            assertEquals("Sobrante Detectado", savedClosure.getStatusMessage());
            assertEquals(BigDecimal.valueOf(20000), savedClosure.getDifferenceCash());
        }
    }

    @Nested
    @DisplayName("Base Calculation Tests")
    class BaseCalculationTests {

        @Test
        @DisplayName("Should calculate correct base for next day")
        void shouldCalculateCorrectBaseForNextDay() {
            // Arrange: Cash detail con billetes y monedas que generan una base específica
            // 2 billetes de 50k (max 2) + 5 billetes de 20k (max 10) + 10 billetes de 10k (max 15)
            CashCountDetail cashDetail = new CashCountDetail(
                    0,    // bill100k
                    2,    // bill50k -> 2 * 50,000 = 100,000
                    5,    // bill20k -> 5 * 20,000 = 100,000
                    10,   // bill10k -> 10 * 10,000 = 100,000
                    2,    // bill5k -> 2 * 5,000 = 10,000
                    3,    // bill2k -> 3 * 2,000 = 6,000
                    5,    // coin1000 -> 5 * 1,000 = 5,000
                    10,   // coin500 -> 10 * 500 = 5,000
                    0, 0, 0
            );
            ExecuteClosureRequest request = createRequest(cashDetail, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            when(closureRepository.findLastClosingTimeByUser("seller-001"))
                    .thenReturn(Optional.of(LocalDateTime.now().minusHours(8)));
            when(orderRepository.sumTotalsByPaymentMethodAndSeller(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(createSalesTotals(BigDecimal.valueOf(326000), null, null, null));
            when(closureRepository.findFirstByOrderByClosingTimeDesc())
                    .thenReturn(Optional.empty());
            when(closureRepository.save(any(DailyClosure.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(syncOutboxRepositoryPort.save(any(SyncOutbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            CashierClosureResponse response = useCase.execute(request, "TestUser");

            // Assert
            // Base = (2*50k) + (5*20k) + (10*10k) + (2*5k) + (3*2k) + (5*1k) + (10*500)
            // = 100,000 + 100,000 + 100,000 + 10,000 + 6,000 + 5,000 + 5,000 = 326,000
            assertEquals(BigDecimal.valueOf(326000), response.baseToKeep());
        }

        @Test
        @DisplayName("Should calculate amount to deposit correctly")
        void shouldCalculateAmountToDepositCorrectly() {
            // Arrange: Total cash = 100,000, Base to keep = 50,000 -> Deposit = 50,000
            CashCountDetail cashDetail = new CashCountDetail(
                    1,    // bill100k -> 100,000
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0
            );
            ExecuteClosureRequest request = createRequest(cashDetail, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            when(closureRepository.findLastClosingTimeByUser("seller-001"))
                    .thenReturn(Optional.of(LocalDateTime.now().minusHours(8)));
            when(orderRepository.sumTotalsByPaymentMethodAndSeller(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(createSalesTotals(BigDecimal.valueOf(100000), null, null, null));
            when(closureRepository.findFirstByOrderByClosingTimeDesc())
                    .thenReturn(Optional.empty());
            when(closureRepository.save(any(DailyClosure.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(syncOutboxRepositoryPort.save(any(SyncOutbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            CashierClosureResponse response = useCase.execute(request, "TestUser");

            // Assert
            // bill100k no se incluye en base (solo hasta 50k)
            // Base = 0, Total = 100,000 -> Deposit = 100,000
            assertEquals(BigDecimal.ZERO, response.baseToKeep());
            assertEquals(BigDecimal.valueOf(100000), response.amountToDeposit());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle zero sales day")
        void shouldHandleZeroSalesDay() {
            // Arrange: No sales, but has previous base
            CashCountDetail cashDetail = createCashDetail(0, 1, 0, 0, 0, 0, 0); // 20,000 (the base)
            ExecuteClosureRequest request = createRequest(cashDetail, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            DailyClosure previousClosure = new DailyClosure();
            previousClosure.setBaseBalanceForNextDay(BigDecimal.valueOf(20000));

            when(closureRepository.findLastClosingTimeByUser("seller-001"))
                    .thenReturn(Optional.of(LocalDateTime.now().minusHours(8)));
            when(orderRepository.sumTotalsByPaymentMethodAndSeller(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(new ArrayList<>()); // No sales
            when(closureRepository.findFirstByOrderByClosingTimeDesc())
                    .thenReturn(Optional.of(previousClosure));
            when(closureRepository.save(any(DailyClosure.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(syncOutboxRepositoryPort.save(any(SyncOutbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            CashierClosureResponse response = useCase.execute(request, "TestUser");

            // Assert: Expected cash = 0 sales + 20,000 base = 20,000, counted = 20,000
            assertEquals("SUCCESS", response.status());
            assertTrue(response.shortages().isEmpty());
        }

        @Test
        @DisplayName("Should handle first closure ever (no previous closure)")
        void shouldHandleFirstClosureEver() {
            // Arrange
            CashCountDetail cashDetail = createCashDetail(1, 0, 0, 0, 0, 0, 0); // 50,000
            ExecuteClosureRequest request = createRequest(cashDetail, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            when(closureRepository.findLastClosingTimeByUser("seller-001"))
                    .thenReturn(Optional.empty()); // First closure - uses start of day
            when(orderRepository.sumTotalsByPaymentMethodAndSeller(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(createSalesTotals(BigDecimal.valueOf(50000), null, null, null));
            when(closureRepository.findFirstByOrderByClosingTimeDesc())
                    .thenReturn(Optional.empty()); // No previous base
            when(closureRepository.save(any(DailyClosure.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(syncOutboxRepositoryPort.save(any(SyncOutbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            CashierClosureResponse response = useCase.execute(request, "TestUser");

            // Assert
            assertEquals("SUCCESS", response.status());
        }
    }

    @Nested
    @DisplayName("Outbox Sync Tests")
    class OutboxSyncTests {

        @Test
        @DisplayName("Should save closure to outbox for cloud sync")
        void shouldSaveClosureToOutbox() {
            // Arrange
            CashCountDetail cashDetail = createCashDetail(1, 0, 0, 0, 0, 0, 0);
            ExecuteClosureRequest request = createRequest(cashDetail, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            when(closureRepository.findLastClosingTimeByUser("seller-001"))
                    .thenReturn(Optional.of(LocalDateTime.now().minusHours(8)));
            when(orderRepository.sumTotalsByPaymentMethodAndSeller(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(createSalesTotals(BigDecimal.valueOf(50000), null, null, null));
            when(closureRepository.findFirstByOrderByClosingTimeDesc())
                    .thenReturn(Optional.empty());
            when(closureRepository.save(any(DailyClosure.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(syncOutboxRepositoryPort.save(any(SyncOutbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            useCase.execute(request, "TestUser");

            // Assert
            ArgumentCaptor<SyncOutbox> outboxCaptor = ArgumentCaptor.forClass(SyncOutbox.class);
            verify(syncOutboxRepositoryPort).save(outboxCaptor.capture());

            SyncOutbox savedOutbox = outboxCaptor.getValue();
            assertEquals("DAILY_CLOSURE", savedOutbox.getAggregateType());
            assertEquals("CLOSURE_CREATED", savedOutbox.getEventType());
            assertEquals("PENDING", savedOutbox.getStatus());
        }
    }

    @Nested
    @DisplayName("Admin QR Record Integration Tests")
    class AdminQrRecordIntegrationTests {

        @Test
        @DisplayName("Should use admin QR value when available instead of request value")
        void shouldUseAdminQrValueWhenAvailable() {
            // Arrange: Admin registró 100,000 en QR, cajero envía 50,000 (se ignora)
            CashCountDetail cashDetail = createCashDetail(0, 0, 0, 0, 0, 0, 0);
            ExecuteClosureRequest request = createRequest(
                    cashDetail,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.valueOf(50000) // Este valor debe ser ignorado
            );

            // Mock: Admin registró 100,000 en QR
            when(dailyPaymentRecordRepository.findQrByDate(any(LocalDate.class)))
                    .thenReturn(Optional.of(createAdminQrRecord(BigDecimal.valueOf(100000))));

            when(closureRepository.findLastClosingTimeByUser("seller-001"))
                    .thenReturn(Optional.of(LocalDateTime.now().minusHours(8)));
            when(orderRepository.sumTotalsByPaymentMethodAndSeller(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(createSalesTotals(null, null, null, BigDecimal.valueOf(100000)));
            when(closureRepository.findFirstByOrderByClosingTimeDesc())
                    .thenReturn(Optional.empty());
            when(closureRepository.save(any(DailyClosure.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(syncOutboxRepositoryPort.save(any(SyncOutbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            CashierClosureResponse response = useCase.execute(request, "TestUser");

            // Assert
            assertEquals("SUCCESS", response.status());
            assertTrue(response.shortages().isEmpty());

            verify(closureRepository).save(closureCaptor.capture());
            DailyClosure savedClosure = closureCaptor.getValue();
            // Debe usar el valor del admin (100,000), no el del request (50,000)
            assertEquals(BigDecimal.valueOf(100000), savedClosure.getTotalCountedQr());
        }

        @Test
        @DisplayName("Should fallback to request value when no admin QR record exists")
        void shouldFallbackToRequestValueWhenNoAdminRecord() {
            // Arrange: No hay registro de admin, cajero envía 80,000
            CashCountDetail cashDetail = createCashDetail(0, 0, 0, 0, 0, 0, 0);
            ExecuteClosureRequest request = createRequest(
                    cashDetail,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.valueOf(80000) // Este valor se debe usar
            );

            // Mock: No hay registro de admin (ya configurado por defecto en setUp)

            when(closureRepository.findLastClosingTimeByUser("seller-001"))
                    .thenReturn(Optional.of(LocalDateTime.now().minusHours(8)));
            when(orderRepository.sumTotalsByPaymentMethodAndSeller(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(createSalesTotals(null, null, null, BigDecimal.valueOf(80000)));
            when(closureRepository.findFirstByOrderByClosingTimeDesc())
                    .thenReturn(Optional.empty());
            when(closureRepository.save(any(DailyClosure.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(syncOutboxRepositoryPort.save(any(SyncOutbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            CashierClosureResponse response = useCase.execute(request, "TestUser");

            // Assert
            assertEquals("SUCCESS", response.status());

            verify(closureRepository).save(closureCaptor.capture());
            DailyClosure savedClosure = closureCaptor.getValue();
            // Debe usar el valor del request (80,000)
            assertEquals(BigDecimal.valueOf(80000), savedClosure.getTotalCountedQr());
        }

        @Test
        @DisplayName("Should detect QR shortage using admin value")
        void shouldDetectQrShortageUsingAdminValue() {
            // Arrange: Admin registró 70,000 pero se esperaban 100,000 en ventas QR
            CashCountDetail cashDetail = createCashDetail(0, 0, 0, 0, 0, 0, 0);
            ExecuteClosureRequest request = createRequest(
                    cashDetail,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.valueOf(100000) // Ignorado
            );

            // Mock: Admin registró solo 70,000
            when(dailyPaymentRecordRepository.findQrByDate(any(LocalDate.class)))
                    .thenReturn(Optional.of(createAdminQrRecord(BigDecimal.valueOf(70000))));

            when(closureRepository.findLastClosingTimeByUser("seller-001"))
                    .thenReturn(Optional.of(LocalDateTime.now().minusHours(8)));
            when(orderRepository.sumTotalsByPaymentMethodAndSeller(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(createSalesTotals(null, null, null, BigDecimal.valueOf(100000)));
            when(closureRepository.findFirstByOrderByClosingTimeDesc())
                    .thenReturn(Optional.empty());
            when(closureRepository.save(any(DailyClosure.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(syncOutboxRepositoryPort.save(any(SyncOutbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            CashierClosureResponse response = useCase.execute(request, "TestUser");

            // Assert
            assertEquals("SHORTAGE", response.status());
            assertTrue(response.shortages().containsKey("QR"));
            assertEquals(BigDecimal.valueOf(-30000), response.shortages().get("QR"));
        }

        @Test
        @DisplayName("Should handle null QR value in request when using fallback")
        void shouldHandleNullQrValueInRequestWhenFallback() {
            // Arrange: No admin record, request tiene QR null
            CashCountDetail cashDetail = createCashDetail(1, 0, 0, 0, 0, 0, 0); // 50,000 cash
            ExecuteClosureRequest request = createRequest(
                    cashDetail,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null // QR es null
            );

            when(closureRepository.findLastClosingTimeByUser("seller-001"))
                    .thenReturn(Optional.of(LocalDateTime.now().minusHours(8)));
            when(orderRepository.sumTotalsByPaymentMethodAndSeller(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(createSalesTotals(BigDecimal.valueOf(50000), null, null, null));
            when(closureRepository.findFirstByOrderByClosingTimeDesc())
                    .thenReturn(Optional.empty());
            when(closureRepository.save(any(DailyClosure.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(syncOutboxRepositoryPort.save(any(SyncOutbox.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            CashierClosureResponse response = useCase.execute(request, "TestUser");

            // Assert: Debe usar BigDecimal.ZERO como fallback
            assertEquals("SUCCESS", response.status());

            verify(closureRepository).save(closureCaptor.capture());
            DailyClosure savedClosure = closureCaptor.getValue();
            assertEquals(BigDecimal.ZERO, savedClosure.getTotalCountedQr());
        }
    }
}
