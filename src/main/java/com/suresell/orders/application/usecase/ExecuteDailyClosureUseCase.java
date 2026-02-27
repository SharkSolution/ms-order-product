package com.suresell.orders.application.usecase;

import com.suresell.orders.application.dto.request.ExecuteClosureRequest;
import com.suresell.orders.application.dto.responses.CashierClosureResponse;
import com.suresell.orders.domain.model.DailyClosure;
import com.suresell.orders.infrastructure.persistence.DailyClosureRepository;
import com.suresell.orders.infrastructure.persistence.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExecuteDailyClosureUseCase {

    private final OrderRepository orderRepository;
    private final DailyClosureRepository closureRepository;

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    public ExecuteDailyClosureUseCase(OrderRepository orderRepository, DailyClosureRepository closureRepository) {
        this.orderRepository = orderRepository;
        this.closureRepository = closureRepository;
    }

    @Transactional
    public CashierClosureResponse execute(ExecuteClosureRequest request, String userName) {
        LocalDateTime closingTime = LocalDateTime.now(BOGOTA_ZONE);

        LocalDateTime openingTime = getOpeningTime(request.sellerId());

        long startEpochMillis = openingTime.atZone(BOGOTA_ZONE).toInstant().toEpochMilli();
        long endEpochMillis = closingTime.atZone(BOGOTA_ZONE).toInstant().toEpochMilli();
        List<Object[]> totals = orderRepository.sumTotalsByPaymentMethodAndSeller(
                startEpochMillis,
                endEpochMillis,
                request.sellerId()
        );
        Map<String, BigDecimal> expected = parseTotals(totals);

        BigDecimal diffCash = request.countedCash().subtract(expected.getOrDefault("CASH", BigDecimal.ZERO));
        BigDecimal diffCard = request.countedCard().subtract(expected.getOrDefault("CARD", BigDecimal.ZERO));
        BigDecimal diffNequi = request.countedNequi().subtract(expected.getOrDefault("NEQUI", BigDecimal.ZERO));
        BigDecimal diffQr = request.countedQr().subtract(expected.getOrDefault("QR", BigDecimal.ZERO));

        BigDecimal totalDifference = diffCash.add(diffCard).add(diffNequi).add(diffQr);

        saveClosureAudit(request, expected, totalDifference, openingTime, closingTime, userName);

        Map<String, BigDecimal> shortages = new HashMap<>();

        if (diffCash.compareTo(BigDecimal.ZERO) < 0) shortages.put("Efectivo", diffCash);
        if (diffCard.compareTo(BigDecimal.ZERO) < 0) shortages.put("Tarjeta", diffCard);
        if (diffNequi.compareTo(BigDecimal.ZERO) < 0) shortages.put("Nequi", diffNequi);
        if (diffQr.compareTo(BigDecimal.ZERO) < 0) shortages.put("QR", diffQr);

        if (shortages.isEmpty()) {
            return new CashierClosureResponse("OK", "Cierre de caja registrado correctamente.", Map.of());
        } else {
            return new CashierClosureResponse("SHORTAGE", "Se detectaron faltantes en el conteo.", shortages);
        }
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


    private void saveClosureAudit(ExecuteClosureRequest request, Map<String, BigDecimal> expected,
                                  BigDecimal totalDifference, LocalDateTime openingTime,
                                  LocalDateTime closingTime, String userName) {

        DailyClosure entity = new DailyClosure();
        entity.setOpeningTime(openingTime);
        entity.setClosingTime(closingTime);
        entity.setUserName(userName);
        entity.setNotes(request.notes());

        entity.setBaseBalanceForNextDay(request.baseBalanceForNextDay() != null ?
                request.baseBalanceForNextDay() : BigDecimal.ZERO);

        entity.setTotalCountedCash(request.countedCash() != null ? request.countedCash() : BigDecimal.ZERO);
        entity.setTotalCountedCard(request.countedCard() != null ? request.countedCard() : BigDecimal.ZERO);
        entity.setTotalCountedNequi(request.countedNequi() != null ? request.countedNequi() : BigDecimal.ZERO);
        entity.setTotalCountedQr(request.countedQr() != null ? request.countedQr() : BigDecimal.ZERO);

        entity.setTotalExpectedCash(expected.getOrDefault("CASH", BigDecimal.ZERO));
        entity.setTotalExpectedCard(expected.getOrDefault("CARD", BigDecimal.ZERO));
        entity.setTotalExpectedNequi(expected.getOrDefault("NEQUI", BigDecimal.ZERO));
        entity.setTotalExpectedQr(expected.getOrDefault("QR", BigDecimal.ZERO));

        entity.setDifferenceAmount(totalDifference);

        entity.setStatus(totalDifference.compareTo(BigDecimal.ZERO) < 0 ? "SHORTAGE" : "OK");

        closureRepository.save(entity);
    }
}
