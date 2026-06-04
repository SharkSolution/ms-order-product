package com.suresell.orders.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suresell.orders.application.dto.WaiterClosurePreviewResponse;
import com.suresell.orders.application.dto.WaiterClosureRequest;
import com.suresell.orders.application.dto.WaiterClosureResponse;
import com.suresell.orders.domain.model.OrderStatus;
import com.suresell.orders.domain.model.SyncOutbox;
import com.suresell.orders.domain.model.WaiterClosure;
import com.suresell.orders.domain.port.out.SyncOutboxRepositoryPort;
import com.suresell.orders.infrastructure.persistence.OrderRepository;
import com.suresell.orders.infrastructure.persistence.WaiterClosureRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class WaiterClosureService {

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private final WaiterClosureRepository waiterClosureRepository;
    private final OrderRepository orderRepository;
    private final SyncOutboxRepositoryPort syncOutboxRepositoryPort;
    private final ObjectMapper objectMapper;

    public WaiterClosureService(WaiterClosureRepository waiterClosureRepository,
                                 OrderRepository orderRepository,
                                 SyncOutboxRepositoryPort syncOutboxRepositoryPort,
                                 ObjectMapper objectMapper) {
        this.waiterClosureRepository = waiterClosureRepository;
        this.orderRepository = orderRepository;
        this.syncOutboxRepositoryPort = syncOutboxRepositoryPort;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public WaiterClosurePreviewResponse getWaiterClosurePreview(String waiterId) {
        LocalDateTime startOfDay = LocalDate.now(BOGOTA_ZONE).atStartOfDay();
        LocalDateTime endOfDay = LocalDateTime.now(BOGOTA_ZONE).with(LocalTime.MAX);

        List<Object[]> results = orderRepository.sumTotalsByPaymentMethodAndWaiter(
                waiterId,
                OrderStatus.pagado,
                startOfDay,
                endOfDay
        );

        BigDecimal totalExpectedCash = BigDecimal.ZERO;
        BigDecimal totalExpectedCard = BigDecimal.ZERO;
        BigDecimal totalExpectedQr = BigDecimal.ZERO;

        for (Object[] result : results) {
            String method = (String) result[0];
            BigDecimal sum = result[1] == null ? BigDecimal.ZERO : (BigDecimal) result[1];
            if (method == null) continue;

            String normMethod = method.toUpperCase();
            if (normMethod.equals("CASH") || normMethod.equals("EFECTIVO")) {
                totalExpectedCash = totalExpectedCash.add(sum);
            } else if (normMethod.equals("CARD") || normMethod.equals("TARJETA")) {
                totalExpectedCard = totalExpectedCard.add(sum);
            } else if (normMethod.equals("QR") || normMethod.equals("NEQUI")) {
                totalExpectedQr = totalExpectedQr.add(sum);
            }
        }

        BigDecimal totalExpected = totalExpectedCash.add(totalExpectedCard).add(totalExpectedQr);
        BigDecimal lastBaseCash = waiterClosureRepository.findTopByWaiterIdOrderByClosedAtDesc(waiterId)
                .map(WaiterClosure::getBaseCash)
                .orElse(BigDecimal.ZERO);

        return new WaiterClosurePreviewResponse(
                waiterId,
                LocalDateTime.now(BOGOTA_ZONE),
                totalExpectedCash,
                totalExpectedCard,
                totalExpectedQr,
                totalExpected,
                lastBaseCash,
                "Vista previa generada para el mesero " + waiterId
        );
    }

    @Transactional
    public WaiterClosureResponse executeWaiterClosure(WaiterClosureRequest request) {
        LocalDateTime now = LocalDateTime.now(BOGOTA_ZONE);
        LocalDate today = LocalDate.now(BOGOTA_ZONE);

        // Calculate expected sales from the database to avoid tampering
        WaiterClosurePreviewResponse preview = getWaiterClosurePreview(request.waiterId());

        BigDecimal expectedCash = preview.totalExpectedCash();
        BigDecimal expectedCard = preview.totalExpectedCard();
        BigDecimal expectedQr = preview.totalExpectedQr();

        // Calculations
        BigDecimal baseCash = request.baseCash();
        BigDecimal expectedCashWithBase = expectedCash.add(baseCash); // Deliverable Cash includes the initial base cash

        BigDecimal differenceCash = request.totalCountedCash().subtract(expectedCashWithBase);
        BigDecimal differenceCard = request.totalCountedCard().subtract(expectedCard);
        BigDecimal differenceQr = request.totalCountedQr().subtract(expectedQr);
        BigDecimal totalDifference = differenceCash.add(differenceCard).add(differenceQr);

        String status = determineStatus(totalDifference);
        String message = generateClosureMessage(status, totalDifference);

        // Build entity
        WaiterClosure closure = new WaiterClosure();
        closure.setId(UUID.randomUUID());
        closure.setWaiterId(request.waiterId());
        closure.setWaiterName(request.waiterName());
        closure.setClosureDate(today);
        closure.setBaseCash(baseCash);

        closure.setTotalExpectedCash(expectedCash);
        closure.setTotalExpectedCard(expectedCard);
        closure.setTotalExpectedQr(expectedQr);

        closure.setTotalCountedCash(request.totalCountedCash());
        closure.setTotalCountedCard(request.totalCountedCard());
        closure.setTotalCountedQr(request.totalCountedQr());

        closure.setDifferenceCash(differenceCash);
        closure.setDifferenceCard(differenceCard);
        closure.setDifferenceQr(differenceQr);
        closure.setTotalDifference(totalDifference);

        closure.setStatus(status);
        closure.setNotes(request.notes());
        closure.setClosedAt(now);

        WaiterClosure saved = waiterClosureRepository.save(closure);
        saveClosureToOutbox(saved);

        return new WaiterClosureResponse(
                saved.getId(),
                saved.getWaiterId(),
                saved.getWaiterName(),
                saved.getClosedAt(),
                saved.getBaseCash(),
                saved.getTotalExpectedCash(),
                saved.getTotalExpectedCard(),
                saved.getTotalExpectedQr(),
                saved.getTotalCountedCash(),
                saved.getTotalCountedCard(),
                saved.getTotalCountedQr(),
                saved.getDifferenceCash(),
                saved.getDifferenceCard(),
                saved.getDifferenceQr(),
                saved.getTotalDifference(),
                saved.getStatus(),
                saved.getNotes(),
                message
        );
    }

    private String determineStatus(BigDecimal difference) {
        int comparison = difference.compareTo(BigDecimal.ZERO);
        if (comparison == 0) return "BALANCED";
        if (comparison > 0) return "POSITIVE_DIFF";
        return "NEGATIVE_DIFF";
    }

    private String generateClosureMessage(String status, BigDecimal difference) {
        return switch (status) {
            case "BALANCED" -> "✅ Cierre de turno cuadrado sin diferencias.";
            case "POSITIVE_DIFF" -> String.format("⚠️ Sobrante de caja: $%.2f", difference);
            case "NEGATIVE_DIFF" -> String.format("❌ Faltante de caja: $%.2f", difference.abs());
            default -> "Cierre de turno completado.";
        };
    }

    private void saveClosureToOutbox(WaiterClosure closure) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", "WAITER_CLOSURE_SAVED");
            
            Map<String, Object> closureMap = new HashMap<>();
            closureMap.put("id", closure.getId().toString());
            closureMap.put("waiterId", closure.getWaiterId());
            closureMap.put("waiterName", closure.getWaiterName());
            closureMap.put("closureDate", closure.getClosureDate().toString());
            closureMap.put("baseCash", closure.getBaseCash());
            closureMap.put("totalExpectedCash", closure.getTotalExpectedCash());
            closureMap.put("totalExpectedCard", closure.getTotalExpectedCard());
            closureMap.put("totalExpectedQr", closure.getTotalExpectedQr());
            closureMap.put("totalCountedCash", closure.getTotalCountedCash());
            closureMap.put("totalCountedCard", closure.getTotalCountedCard());
            closureMap.put("totalCountedQr", closure.getTotalCountedQr());
            closureMap.put("differenceCash", closure.getDifferenceCash());
            closureMap.put("differenceCard", closure.getDifferenceCard());
            closureMap.put("differenceQr", closure.getDifferenceQr());
            closureMap.put("totalDifference", closure.getTotalDifference());
            closureMap.put("status", closure.getStatus());
            closureMap.put("notes", closure.getNotes() != null ? closure.getNotes() : "");
            closureMap.put("closedAt", closure.getClosedAt().toString());
            
            payload.put("closure", closureMap);

            SyncOutbox outbox = new SyncOutbox();
            outbox.setAggregateType("WAITER_CLOSURE");
            outbox.setAggregateUuid(closure.getId());
            outbox.setAggregateId(0L);
            outbox.setEventType("WAITER_CLOSURE_SAVED");
            outbox.setPayloadJson(objectMapper.writeValueAsString(payload));
            outbox.setStatus("PENDING");
            outbox.setAttempts(0);
            outbox.setNextRetryAt(System.currentTimeMillis());
            outbox.setCreatedAt(System.currentTimeMillis());
            outbox.setUpdatedAt(System.currentTimeMillis());

            syncOutboxRepositoryPort.save(outbox);
            log.info("Evento WAITER_CLOSURE_SAVED encolado para sincronización: {}", closure.getId());
        } catch (Exception e) {
            log.error("Error encolando sincronización de cierre de mesero: {}", e.getMessage());
        }
    }
}
