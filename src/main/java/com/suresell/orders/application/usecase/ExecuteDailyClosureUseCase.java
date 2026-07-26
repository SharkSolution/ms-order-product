package com.suresell.orders.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suresell.orders.application.dto.request.ExecuteClosureRequest;
import com.suresell.orders.application.dto.responses.CashierClosureResponse;
import com.suresell.orders.domain.model.DailyClosure;
import com.suresell.orders.domain.model.SyncOutbox;
import com.suresell.orders.domain.port.out.SyncOutboxRepositoryPort;
import com.suresell.orders.domain.service.CashflowCalculator;
import com.suresell.orders.infrastructure.persistence.DailyClosureRepository;
import com.suresell.orders.infrastructure.persistence.OrderRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.UUID;

@Log4j2
@Service
public class ExecuteDailyClosureUseCase {
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private final OrderRepository orderRepository;
    private final DailyClosureRepository closureRepository;
    private final CashflowCalculator cashflowCalculator;
    private final ObjectMapper objectMapper;
    private final SyncOutboxRepositoryPort syncOutboxRepositoryPort;
    private final DailyPaymentRecordService dailyPaymentRecordService;
    // F5 multipago: splits de órdenes MIXED para el esperado por método.
    private final com.suresell.orders.infrastructure.persistence.OrderPaymentRepository orderPaymentRepository;
    // N3/Inc.4: el cierre se bloquea si quedan mesas sin cobrar.
    private final TableSessionService tableSessionService;

    public ExecuteDailyClosureUseCase(OrderRepository orderRepository, DailyClosureRepository closureRepository,
                                    CashflowCalculator cashflowCalculator, ObjectMapper objectMapper,
                                    SyncOutboxRepositoryPort syncOutboxRepositoryPort,
                                    DailyPaymentRecordService dailyPaymentRecordService,
                                    com.suresell.orders.infrastructure.persistence.OrderPaymentRepository orderPaymentRepository,
                                    TableSessionService tableSessionService) {
        this.orderRepository = orderRepository;
        this.closureRepository = closureRepository;
        this.cashflowCalculator = cashflowCalculator;
        this.objectMapper = objectMapper;
        this.syncOutboxRepositoryPort = syncOutboxRepositoryPort;
        this.dailyPaymentRecordService = dailyPaymentRecordService;
        this.orderPaymentRepository = orderPaymentRepository;
        this.tableSessionService = tableSessionService;
    }

    @Value("${sync.cloud.core-url:http://localhost:8083/api/core}")
    private String coreApiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Transactional
    public CashierClosureResponse execute(ExecuteClosureRequest request, String userName) {
        // N3/Inc.4 — NO se puede cerrar caja con mesas sin cobrar: ese consumo
        // quedaría fuera del cuadre y aparecería como venta al día siguiente.
        // Se bloquea y se dice CUÁLES, para que el cajero sepa qué hacer.
        var mesasAbiertas = tableSessionService.pendientesDeCobro();
        if (!mesasAbiertas.isEmpty()) {
            String detalle = mesasAbiertas.stream()
                    .map(m -> "mesa " + m.getTableId())
                    .collect(java.util.stream.Collectors.joining(", "));
            throw new IllegalStateException(String.format(
                    "Hay %d mesa(s) abiertas sin cobrar (%s). Ciérralas antes de cerrar la caja.",
                    mesasAbiertas.size(), detalle));
        }
        BigDecimal calculatedTotalCash = cashflowCalculator.calculateTotalCash(request.cashDetail());
        BigDecimal calculatedBase = cashflowCalculator.calculateBaseForNextDay(request.cashDetail());

        LocalDateTime closingTime = LocalDateTime.now(BOGOTA_ZONE);
        LocalDateTime openingTime = getOpeningTime(request.sellerId());

        List<Object[]> totals = orderRepository.sumTotalsByPaymentMethodAndSeller(
                openingTime,
                closingTime
        );
        log.info("totales: {}", totals);

        Map<String, BigDecimal> expected = parseTotals(totals);

        // F5 multipago: sumar los splits de las órdenes MIXED por método.
        for (Object[] row : orderPaymentRepository.sumSplitsByMethod(openingTime, closingTime)) {
            String method = String.valueOf(row[0]);
            BigDecimal amount = (BigDecimal) row[1];
            expected.merge(method, amount == null ? BigDecimal.ZERO : amount, BigDecimal::add);
        }

        // N2/6.6 — Nequi eliminado: lo que llegue rotulado NEQUI (histórico o de
        // un APK viejo) se PLIEGA dentro de QR antes de cualquier cálculo, para
        // que el cierre no arrastre una categoría que ya no existe.
        BigDecimal nequiHeredado = expected.remove("NEQUI");
        if (nequiHeredado != null && nequiHeredado.compareTo(BigDecimal.ZERO) != 0) {
            expected.merge("QR", nequiHeredado, BigDecimal::add);
            log.info("Cierre: ${} rotulados NEQUI se contabilizan como QR (Nequi eliminado)", nequiHeredado);
        }

        BigDecimal pureSales = expected.getOrDefault("CASH", BigDecimal.ZERO)
                .add(expected.getOrDefault("CARD", BigDecimal.ZERO))
                .add(expected.getOrDefault("QR", BigDecimal.ZERO));

        BigDecimal previousBase = closureRepository.findFirstByOrderByClosingTimeDesc()
                .map(DailyClosure::getBaseBalanceForNextDay)
                .orElse(BigDecimal.ZERO);

        BigDecimal salesCash = expected.getOrDefault("CASH", BigDecimal.ZERO);

        // Calcular gastos menores acumulados y sumarlos de forma automática en el backend
        BigDecimal totalPettyCashExpenses = BigDecimal.ZERO;
        if (request.pettyCashExpenses() != null) {
            for (com.suresell.orders.application.dto.request.PettyCashExpenseRequest expense : request.pettyCashExpenses()) {
                if (expense.amount() != null) {
                    totalPettyCashExpenses = totalPettyCashExpenses.add(expense.amount());
                }
            }
        }

        // Deducir el total de gastos menores de la caja esperada
        BigDecimal trueExpectedCash = salesCash.add(previousBase).subtract(totalPettyCashExpenses); // Ventas + Base Inicial - Gastos Menores

        expected.put("CASH", trueExpectedCash);

        // Obtener el valor QR registrado por admin desde ms-core-app
        BigDecimal countedQr = getQrAmountFromCoreApp(closingTime.toLocalDate(), request.countedQr());
        log.info("Valor QR a usar en cierre: {}", countedQr);

        BigDecimal diffCash = calculatedTotalCash.subtract(trueExpectedCash);
        BigDecimal diffCard = request.countedCard().subtract(expected.getOrDefault("CARD", BigDecimal.ZERO));
        // Ya no hay diferencia propia de Nequi: la categoría desapareció.
        BigDecimal diffNequi = BigDecimal.ZERO;
        BigDecimal diffQr = countedQr.subtract(expected.getOrDefault("QR", BigDecimal.ZERO));

        BigDecimal totalDifference = diffCash.add(diffCard).add(diffNequi).add(diffQr);

        BigDecimal amountToDeposit = calculatedTotalCash.subtract(calculatedBase);
        if (amountToDeposit.compareTo(BigDecimal.ZERO) < 0) {
            amountToDeposit = BigDecimal.ZERO;
        }

        DailyClosure savedClosure = saveClosureAudit(request, expected, totalDifference, openingTime, closingTime,
                userName, calculatedTotalCash, calculatedBase, diffCash, diffCard, diffNequi, diffQr, previousBase, pureSales, countedQr, totalPettyCashExpenses);

        saveClosureToOutbox(savedClosure);

        Map<String, BigDecimal> shortages = new HashMap<>();
        if (diffCash.compareTo(BigDecimal.ZERO) < 0) shortages.put("Efectivo", diffCash);
        if (diffCard.compareTo(BigDecimal.ZERO) < 0) shortages.put("Tarjeta", diffCard);
        if (diffQr.compareTo(BigDecimal.ZERO) < 0) shortages.put("QR", diffQr);

        boolean hasNetShortage = totalDifference.compareTo(BigDecimal.ZERO) < 0;

        String message = (!hasNetShortage)
                ? "Cierre exitoso. Por favor ajuste la base."
                : "Cierre con novedades. Se detectaron faltantes. ¡Notificación enviada a Administrador!";

        return new CashierClosureResponse(
                (!hasNetShortage ? "SUCCESS" : "SHORTAGE"),
                message,
                shortages,
                calculatedBase,
                amountToDeposit
        );
    }

    private Map<String, BigDecimal> parseTotals(List<Object[]> queryResults) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (Object[] result : queryResults) {
            String method = (String) result[0];
            BigDecimal amount = new BigDecimal(result[1].toString());
            map.put(method, amount);
        }
        return map;
    }

    private BigDecimal getQrAmountFromCoreApp(LocalDate date, BigDecimal fallbackManualQr) {
        try {
            String url = coreApiUrl + "/qr-payments/by-date?date=" + date.toString();
            log.info("Consultando pagos QR en ms-core-app: {}", url);
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                BigDecimal qrAmount = new BigDecimal(response.getBody().get("amount").asText());
                log.info("Valor QR obtenido exitosamente desde ms-core-app: {}", qrAmount);
                return qrAmount;
            }
        } catch (Exception e) {
            log.warn("Error al consultar pago QR en ms-core-app (posible falta de internet o registro no existe): {}", e.getMessage());
        }
        log.info("Usando valor QR manual del cajero como fallback: {}", fallbackManualQr);
        return fallbackManualQr != null ? fallbackManualQr : BigDecimal.ZERO;
    }

    private LocalDateTime getOpeningTime(String sellerId) {
        return closureRepository.findLastClosingTimeByUser(sellerId)
                .orElse(LocalDateTime.now().toLocalDate().atStartOfDay());
    }

    private DailyClosure saveClosureAudit(ExecuteClosureRequest request,
                                  Map<String, BigDecimal> expected,
                                  BigDecimal totalDifference,
                                  LocalDateTime openingTime,
                                  LocalDateTime closingTime,
                                  String userName,
                                  BigDecimal calculatedTotalCash,
                                  BigDecimal calculatedBase,
                                  BigDecimal diffCash,
                                  BigDecimal diffCard,
                                  BigDecimal diffNequi,
                                  BigDecimal diffQr,
                                  BigDecimal previousBase,
                                  BigDecimal pureSales,
                                  BigDecimal countedQr,
                                  BigDecimal pettyCashExpenses
                                          ) {

        DailyClosure entity = new DailyClosure();
        entity.setId(UUID.randomUUID());
        entity.setOpeningTime(openingTime);
        entity.setClosingTime(closingTime);
        entity.setClosureDate(closingTime.toLocalDate());
        entity.setUserName(userName);
        entity.setNotes(request.notes());
        entity.setBaseBalanceForNextDay(calculatedBase != null ? calculatedBase : BigDecimal.ZERO);
        entity.setTotalSales(pureSales);
        entity.setPettyCashExpenses(pettyCashExpenses != null ? pettyCashExpenses : BigDecimal.ZERO);

        try {
            if (request.pettyCashExpenses() != null) {
                entity.setPettyCashExpensesAudit(objectMapper.writeValueAsString(request.pettyCashExpenses()));
            }
        } catch (JsonProcessingException e) {
            log.error("Error serializando auditoria de gastos menores", e);
            entity.setPettyCashExpensesAudit("ERROR_SERIALIZING_EXPENSES");
        }

        try {
            if (request.cashDetail() != null) {
                String jsonAudit = objectMapper.writeValueAsString(request.cashDetail());
                entity.setCashCountAudit(jsonAudit);
            }
        } catch (JsonProcessingException e) {
            log.error("Error serializando auditoría de billetes", e);
            entity.setCashCountAudit("ERROR_SERIALIZING_AUDIT");
        }

        entity.setTotalCountedCash(calculatedTotalCash != null ? calculatedTotalCash : BigDecimal.ZERO);
        entity.setTotalCountedCard(request.countedCard() != null ? request.countedCard() : BigDecimal.ZERO);
        entity.setTotalCountedQr(countedQr != null ? countedQr : BigDecimal.ZERO);

        BigDecimal totalCounted = entity.getTotalCountedCash()
                .add(entity.getTotalCountedCard())
                .add(entity.getTotalCountedQr());
        entity.setTotalCounted(totalCounted);

        entity.setTotalExpectedCash(expected.getOrDefault("CASH", BigDecimal.ZERO));
        entity.setTotalExpectedCard(expected.getOrDefault("CARD", BigDecimal.ZERO));
        entity.setTotalExpectedQr(expected.getOrDefault("QR", BigDecimal.ZERO));

        BigDecimal totalExpected = entity.getTotalExpectedCash()
                .add(entity.getTotalExpectedCard())
                .add(entity.getTotalExpectedQr());
        entity.setTotalExpected(totalExpected);

        entity.setDifferenceCash(diffCash);
        entity.setDifferenceCard(diffCard);
        entity.setDifferenceQr(diffQr);

        entity.setDifferenceAmount(totalDifference);
        entity.setTotalDifference(totalDifference); // revisar si mejor quitar

        int comparison = totalDifference.compareTo(BigDecimal.ZERO);
        if (comparison == 0) {
            entity.setStatus("BALANCED");
            entity.setStatusMessage("Cierre Cuadrado");
        } else if (comparison > 0) {
            entity.setStatus("POSITIVE_DIFF");
            entity.setStatusMessage("Sobrante Detectado");
        } else {
            entity.setStatus("NEGATIVE_DIFF");
            entity.setStatusMessage("Faltante Detectado");
        }
        try {
            log.info("Datos del cierre a persistir: \n{}",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(entity));
        } catch (Exception e) {
            log.warn("No se pudo imprimir la entidad en el log", e);
        }
        closureRepository.save(entity);

        return entity;
    }

    private void saveClosureToOutbox(DailyClosure closure) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", "CLOSURE_CREATED");
            payload.put("closure", closure);
            SyncOutbox outbox = new SyncOutbox();
            outbox.setAggregateType("DAILY_CLOSURE");
            outbox.setAggregateUuid(closure.getId());
            outbox.setAggregateId(0L);
            outbox.setEventType("CLOSURE_CREATED");
            outbox.setPayloadJson(objectMapper.writeValueAsString(payload));
            outbox.setStatus("PENDING");
            outbox.setAttempts(0);
            outbox.setNextRetryAt(System.currentTimeMillis());
            outbox.setCreatedAt(System.currentTimeMillis());
            outbox.setUpdatedAt(System.currentTimeMillis());
            syncOutboxRepositoryPort.save(outbox);
        } catch (Exception e) {
            log.error("Error encolando sincronización de cierre: {}", e.getMessage());
        }
    }
}
