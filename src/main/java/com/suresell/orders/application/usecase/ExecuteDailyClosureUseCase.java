package com.suresell.orders.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suresell.orders.application.dto.request.ExecuteClosureRequest;
import com.suresell.orders.application.dto.responses.CashierClosureResponse;
import com.suresell.orders.domain.model.DailyClosure;
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

@Log4j2
@Service
public class ExecuteDailyClosureUseCase {
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private final OrderRepository orderRepository;
    private final DailyClosureRepository closureRepository;
    private final CashflowCalculator cashflowCalculator;
    private final ObjectMapper objectMapper;

    public ExecuteDailyClosureUseCase(OrderRepository orderRepository, DailyClosureRepository closureRepository, CashflowCalculator cashflowCalculator, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.closureRepository = closureRepository;
        this.cashflowCalculator = cashflowCalculator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CashierClosureResponse execute(ExecuteClosureRequest request, String userName) {
        BigDecimal calculatedTotalCash = cashflowCalculator.calculateTotalCash(request.cashDetail());
        BigDecimal calculatedBase = cashflowCalculator.calculateBaseForNextDay(request.cashDetail());
        BigDecimal amountToDeposit = calculatedTotalCash.subtract(calculatedBase);
        if (amountToDeposit.compareTo(BigDecimal.ZERO) < 0) {
            amountToDeposit = BigDecimal.ZERO;
        }
        LocalDateTime closingTime = LocalDateTime.now(BOGOTA_ZONE);
        LocalDateTime openingTime = getOpeningTime(request.sellerId());
        long startEpochMillis = openingTime.atZone(BOGOTA_ZONE).toInstant().toEpochMilli();
        long endEpochMillis = closingTime.atZone(BOGOTA_ZONE).toInstant().toEpochMilli();
        List<Object[]> totals = orderRepository.sumTotalsByPaymentMethodAndSeller(
                startEpochMillis,
                endEpochMillis
        );
        Map<String, BigDecimal> expected = parseTotals(totals);
        BigDecimal diffCash = calculatedTotalCash.subtract(expected.getOrDefault("CASH", BigDecimal.ZERO));
        BigDecimal diffCard = request.countedCard().subtract(expected.getOrDefault("CARD", BigDecimal.ZERO));
        BigDecimal diffNequi = request.countedNequi().subtract(expected.getOrDefault("NEQUI", BigDecimal.ZERO));
        BigDecimal diffQr = request.countedQr().subtract(expected.getOrDefault("QR", BigDecimal.ZERO));
        BigDecimal totalDifference = diffCash.add(diffCard).add(diffNequi).add(diffQr);
        saveClosureAudit(request, expected, totalDifference, openingTime, closingTime,
                userName, calculatedTotalCash, calculatedBase, diffCash, diffCard, diffNequi, diffQr);
        Map<String, BigDecimal> shortages = new HashMap<>();
        if (diffCash.compareTo(BigDecimal.ZERO) < 0) shortages.put("Efectivo", diffCash);
        if (diffCard.compareTo(BigDecimal.ZERO) < 0) shortages.put("Tarjeta", diffCard);
        if (diffNequi.compareTo(BigDecimal.ZERO) < 0) shortages.put("Nequi", diffNequi);
        if (diffQr.compareTo(BigDecimal.ZERO) < 0) shortages.put("QR", diffQr);
        String message = (shortages.isEmpty())
                ? "Cierre exitoso. Por favor ajuste la base."
                : "Cierre con novedades. Se detectaron faltantes. ¡Notificacion enviada a Administrador!";
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

    private void saveClosureAudit(ExecuteClosureRequest request,
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
                                  BigDecimal diffQr) {
        DailyClosure entity = new DailyClosure();
        entity.setOpeningTime(openingTime);
        entity.setClosingTime(closingTime);
        entity.setUserName(userName);
        entity.setNotes(request.notes());
        entity.setBaseBalanceForNextDay(calculatedBase != null ? calculatedBase : BigDecimal.ZERO);
        entity.setTotalCountedCash(calculatedTotalCash != null ? calculatedTotalCash : BigDecimal.ZERO);
        try {
            if (request.cashDetail() != null) {
                String jsonAudit = objectMapper.writeValueAsString(request.cashDetail());
                entity.setCashCountAudit(jsonAudit);
            }
        } catch (JsonProcessingException e) {
            log.error("Error serializando auditoría de billetes", e);
            entity.setCashCountAudit("ERROR_SERIALIZING_AUDIT");
        }
        entity.setDifferenceCard(diffCard);
        entity.setDifferenceCash(diffCash);
        entity.setDifferenceQr(diffQr);
        entity.setDifferenceNequi(diffNequi);

        entity.setTotalCountedCard(request.countedCard() != null ? request.countedCard() : BigDecimal.ZERO);
        entity.setTotalCountedNequi(request.countedNequi() != null ? request.countedNequi() : BigDecimal.ZERO);
        entity.setTotalCountedQr(request.countedQr() != null ? request.countedQr() : BigDecimal.ZERO);
        entity.setTotalCounted(entity.getTotalCountedCard().add(entity.getTotalCountedNequi()).add(entity.getTotalCountedQr()).add(entity.getTotalCountedCash()));
        entity.setTotalExpectedCash(expected.getOrDefault("CASH", BigDecimal.ZERO));
        entity.setTotalExpectedCard(expected.getOrDefault("CARD", BigDecimal.ZERO));
        entity.setTotalExpectedNequi(expected.getOrDefault("NEQUI", BigDecimal.ZERO));
        entity.setTotalExpectedQr(expected.getOrDefault("QR", BigDecimal.ZERO));
        entity.setTotalExpected(entity.getTotalExpectedCard().add(entity.getTotalExpectedQr()).add(entity.getTotalExpectedNequi()).add(entity.getTotalExpectedCash()));
        entity.setDifferenceAmount(totalDifference);
        entity.setTotalDifference(totalDifference);
        entity.setStatus(totalDifference.compareTo(BigDecimal.ZERO) < 0 ? "SHORTAGE" : "OK");
        closureRepository.save(entity);
    }
}
