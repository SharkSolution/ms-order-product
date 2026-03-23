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

import java.math.BigDecimal;
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

    public ExecuteDailyClosureUseCase(OrderRepository orderRepository, DailyClosureRepository closureRepository,
                                    CashflowCalculator cashflowCalculator, ObjectMapper objectMapper,
                                    SyncOutboxRepositoryPort syncOutboxRepositoryPort) {
        this.orderRepository = orderRepository;
        this.closureRepository = closureRepository;
        this.cashflowCalculator = cashflowCalculator;
        this.objectMapper = objectMapper;
        this.syncOutboxRepositoryPort = syncOutboxRepositoryPort;
    }

    @Transactional
    public CashierClosureResponse execute(ExecuteClosureRequest request, String userName) {
        BigDecimal calculatedTotalCash = cashflowCalculator.calculateTotalCash(request.cashDetail());
        BigDecimal calculatedBase = cashflowCalculator.calculateBaseForNextDay(request.cashDetail());

        LocalDateTime closingTime = LocalDateTime.now(BOGOTA_ZONE);
        LocalDateTime openingTime = getOpeningTime(request.sellerId());
        long startEpochMillis = openingTime.atZone(BOGOTA_ZONE).toInstant().toEpochMilli();
        long endEpochMillis = closingTime.atZone(BOGOTA_ZONE).toInstant().toEpochMilli();

        List<Object[]> totals = orderRepository.sumTotalsByPaymentMethodAndSeller(
                startEpochMillis,
                endEpochMillis
        );
        log.info("totales: {}", totals);

        Map<String, BigDecimal> expected = parseTotals(totals);

        BigDecimal pureSales = expected.getOrDefault("CASH", BigDecimal.ZERO)
                .add(expected.getOrDefault("CARD", BigDecimal.ZERO))
                .add(expected.getOrDefault("NEQUI", BigDecimal.ZERO))
                .add(expected.getOrDefault("QR", BigDecimal.ZERO));

        BigDecimal previousBase = closureRepository.findFirstByOrderByClosingTimeDesc()
                .map(DailyClosure::getBaseBalanceForNextDay)
                .orElse(BigDecimal.ZERO);

        BigDecimal salesCash = expected.getOrDefault("CASH", BigDecimal.ZERO);
        BigDecimal trueExpectedCash = salesCash.add(previousBase); // Ventas + Base Inicial

        expected.put("CASH", trueExpectedCash);

        BigDecimal diffCash = calculatedTotalCash.subtract(trueExpectedCash);
        BigDecimal diffCard = request.countedCard().subtract(expected.getOrDefault("CARD", BigDecimal.ZERO));
        BigDecimal diffNequi = request.countedNequi().subtract(expected.getOrDefault("NEQUI", BigDecimal.ZERO));
        BigDecimal diffQr = request.countedQr().subtract(expected.getOrDefault("QR", BigDecimal.ZERO));

        BigDecimal totalDifference = diffCash.add(diffCard).add(diffNequi).add(diffQr);

        BigDecimal amountToDeposit = calculatedTotalCash.subtract(calculatedBase);
        if (amountToDeposit.compareTo(BigDecimal.ZERO) < 0) {
            amountToDeposit = BigDecimal.ZERO;
        }

        DailyClosure savedClosure = saveClosureAudit(request, expected, totalDifference, openingTime, closingTime,
                userName, calculatedTotalCash, calculatedBase, diffCash, diffCard, diffNequi, diffQr, previousBase, pureSales);

        saveClosureToOutbox(savedClosure);

        Map<String, BigDecimal> shortages = new HashMap<>();
        if (diffCash.compareTo(BigDecimal.ZERO) < 0) shortages.put("Efectivo", diffCash);
        if (diffCard.compareTo(BigDecimal.ZERO) < 0) shortages.put("Tarjeta", diffCard);
        if (diffNequi.compareTo(BigDecimal.ZERO) < 0) shortages.put("Nequi", diffNequi);
        if (diffQr.compareTo(BigDecimal.ZERO) < 0) shortages.put("QR", diffQr);

        String message = (shortages.isEmpty())
                ? "Cierre exitoso. Por favor ajuste la base."
                : "Cierre con novedades. Se detectaron faltantes. ¡Notificación enviada a Administrador!";

        return new CashierClosureResponse(
                (shortages.isEmpty() ? "SUCCESS" : "SHORTAGE"),
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
                                  BigDecimal pureSales
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
        entity.setTotalCountedNequi(request.countedNequi() != null ? request.countedNequi() : BigDecimal.ZERO);
        entity.setTotalCountedQr(request.countedQr() != null ? request.countedQr() : BigDecimal.ZERO);

        BigDecimal totalCounted = entity.getTotalCountedCash()
                .add(entity.getTotalCountedCard())
                .add(entity.getTotalCountedNequi())
                .add(entity.getTotalCountedQr());
        entity.setTotalCounted(totalCounted);

        entity.setTotalExpectedCash(expected.getOrDefault("CASH", BigDecimal.ZERO));
        entity.setTotalExpectedCard(expected.getOrDefault("CARD", BigDecimal.ZERO));
        entity.setTotalExpectedNequi(expected.getOrDefault("NEQUI", BigDecimal.ZERO));
        entity.setTotalExpectedQr(expected.getOrDefault("QR", BigDecimal.ZERO));

        BigDecimal totalExpected = entity.getTotalExpectedCash()
                .add(entity.getTotalExpectedCard())
                .add(entity.getTotalExpectedNequi())
                .add(entity.getTotalExpectedQr());
        entity.setTotalExpected(totalExpected);

        entity.setDifferenceCash(diffCash);
        entity.setDifferenceCard(diffCard);
        entity.setDifferenceNequi(diffNequi);
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
