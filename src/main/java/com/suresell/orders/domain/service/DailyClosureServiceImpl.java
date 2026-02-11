package com.suresell.orders.domain.service;
import com.suresell.orders.domain.model.DailyClosure;
import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderStatus;
import com.suresell.orders.application.dto.ClosurePreviewResponse;
import com.suresell.orders.application.dto.ClosureRequest;
import com.suresell.orders.application.dto.ClosureResponse;
import com.suresell.orders.infrastructure.persistence.OrderRepository;
import com.suresell.orders.domain.port.in.DailyClosureService;
import com.suresell.orders.domain.port.out.DailyClosureRepositoryPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class DailyClosureServiceImpl
implements DailyClosureService {
    private static final Logger log = LoggerFactory.getLogger(DailyClosureServiceImpl.class);
    private final DailyClosureRepositoryPort closureRepositoryPort;
    private final OrderRepository orderRepository;
    private static final String PAYMENT_CASH = "CASH";
    private static final String PAYMENT_CARD = "CARD";
    private static final String PAYMENT_NEQUI = "NEQUI";
    private static final String PAYMENT_QR = "QR";
    private static final String STATUS_BALANCED = "BALANCED";
    private static final String STATUS_POSITIVE_DIFF = "POSITIVE_DIFF";
    private static final String STATUS_NEGATIVE_DIFF = "NEGATIVE_DIFF";
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    @Transactional(readOnly=true)
    public ClosurePreviewResponse getClosurePreview() {
        LocalDateTime startOfDay = LocalDate.now(BOGOTA_ZONE).atStartOfDay();
        LocalDateTime endOfDay = LocalDateTime.now(BOGOTA_ZONE).with(LocalTime.MAX);

        List<Object[]> results = this.orderRepository.findTotalByPaymentMethodAndStatus(OrderStatus.pagado, startOfDay, endOfDay);
        Optional<LocalDateTime> minCreatedAt = this.orderRepository.findMinCreatedAtByStatus(OrderStatus.pagado, startOfDay, endOfDay);
        Integer countOrders = this.orderRepository.countByStatus(OrderStatus.pagado, startOfDay, endOfDay);

        BigDecimal totalCash = BigDecimal.ZERO;
        BigDecimal totalCard = BigDecimal.ZERO;
        BigDecimal totalNequi = BigDecimal.ZERO;
        BigDecimal totalQr = BigDecimal.ZERO;
        BigDecimal baseBalance = this.closureRepositoryPort.findLastClosure().map(DailyClosure::getBaseBalanceForNextDay).orElse(BigDecimal.ZERO);


        for (Object[] result : results) {
            String paymentMethod = (String)result[0];
            BigDecimal sumTotal = BigDecimal.valueOf(((Long)result[1]).doubleValue());

            if (paymentMethod == null) {
                continue;
            }

            switch (paymentMethod) {
                case "CASH": {
                    totalCash = sumTotal;
                    break;
                }
                case "CARD": {
                    totalCard = sumTotal;
                    break;
                }
                case "NEQUI": {
                    totalNequi = sumTotal;
                    break;
                }
                case "QR": {
                    totalQr = sumTotal;
                    break;
                }
                default: {
                }
            }
        }
        BigDecimal totalExpected = totalCash.add(totalCard).add(totalNequi).add(totalQr);
        LocalDateTime currentTime = LocalDateTime.now(BOGOTA_ZONE);
        LocalDateTime openingTime = minCreatedAt.orElse(startOfDay);
        int totalOrders = countOrders != null ? countOrders : 0;
        return new ClosurePreviewResponse(openingTime, currentTime, totalOrders, "Preview de cierre generado correctamente para el d\u00eda actual.");
    }

    @Transactional
    public ClosureResponse executeClosure(ClosureRequest request) {
        LocalDateTime openingTime = this.determineOpeningTime();
        LocalDateTime closingTime = LocalDateTime.now(BOGOTA_ZONE);
        List orders = this.getOrdersForPeriod(openingTime, closingTime);
        BigDecimal expectedCash = this.calculateTotalByPaymentMethod(orders, PAYMENT_CASH);
        BigDecimal expectedCard = this.calculateTotalByPaymentMethod(orders, PAYMENT_CARD);
        BigDecimal expectedNequi = this.calculateTotalByPaymentMethod(orders, PAYMENT_NEQUI);
        BigDecimal expectedQr = this.calculateTotalByPaymentMethod(orders, PAYMENT_QR);

        BigDecimal previousBaseBalance = this.closureRepositoryPort.findLastClosure().map(DailyClosure::getBaseBalanceForNextDay).orElse(BigDecimal.ZERO);

        BigDecimal countedCash = request.totalCountedCash().subtract(previousBaseBalance);
        BigDecimal countedCard = request.totalCountedCard();
        BigDecimal countedNequi = request.totalCountedNequi();
        BigDecimal countedQr = request.totalCountedQr();
        
        BigDecimal totalExpected = expectedCash.add(expectedCard).add(expectedNequi).add(expectedQr);
        BigDecimal totalCounted = countedCash.add(countedCard).add(countedNequi).add(countedQr);
        BigDecimal difference = totalCounted.subtract(totalExpected);
        String status = this.determineStatus(difference);
        DailyClosure closure = new DailyClosure();
        closure.setId(UUID.randomUUID());
        closure.setUserName(request.userName());
        closure.setOpeningTime(openingTime);
        closure.setClosingTime(closingTime);
        closure.setTotalExpectedCash(expectedCash);
        closure.setTotalExpectedCard(expectedCard);
        closure.setTotalExpectedNequi(expectedNequi);
        closure.setTotalExpectedQr(expectedQr);
        closure.setTotalCountedCash(countedCash);
        closure.setTotalCountedCard(countedCard);
        closure.setTotalCountedNequi(countedNequi);
        closure.setTotalCountedQr(countedQr);
        closure.setDifferenceAmount(difference);
        closure.setStatus(status);
        closure.setNotes(request.notes());
        closure.setBaseBalanceForNextDay(request.baseBalanceForNextDay());
        DailyClosure savedClosure = this.closureRepositoryPort.save(closure);
        return new ClosureResponse(savedClosure.getId(), savedClosure.getUserName(), savedClosure.getOpeningTime(), savedClosure.getClosingTime(), expectedCash, expectedCard, expectedNequi, expectedQr, totalExpected, countedCash, countedCard, countedNequi, countedQr, totalCounted, difference, status, savedClosure.getNotes(), this.generateClosureMessage(status, difference), previousBaseBalance);
    }    
    private LocalDateTime determineOpeningTime() {
        return this.closureRepositoryPort.findLastClosure().map(DailyClosure::getClosingTime).orElseGet(() -> {
            return this.orderRepository.findFirstByOrderByCreatedAtAsc().map(Order::getCreatedAt).orElse(LocalDateTime.now(BOGOTA_ZONE));
        });
    }
    private List<Order> getOrdersForPeriod(LocalDateTime from, LocalDateTime to) {
        return this.orderRepository.findAll().stream().filter(order -> OrderStatus.pagado.equals(order.getStatus())).filter(order -> order.getPaymentMethod() != null).filter(order -> {
            LocalDateTime orderTime = order.getCreatedAt();
            return !orderTime.isBefore(from) && !orderTime.isAfter(to);
        }).toList();
    }
    private BigDecimal calculateTotalByPaymentMethod(List<Order> orders, String paymentMethod) {
        long total = orders.stream().filter(order -> paymentMethod.equalsIgnoreCase(order.getPaymentMethod())).mapToLong(Order::getTotal).sum();
        return BigDecimal.valueOf(total);
    }
    private String determineStatus(BigDecimal difference) {
        int comparison = difference.compareTo(BigDecimal.ZERO);
        if (comparison == 0) {
            return STATUS_BALANCED;
        }
        if (comparison > 0) {
            return STATUS_POSITIVE_DIFF;
        }
        return STATUS_NEGATIVE_DIFF;
    }
    private String generateClosureMessage(String status, BigDecimal difference) {
        return switch (status) {
            case STATUS_BALANCED -> "\u2705 Cierre cuadrado. No hay diferencias.";
            case STATUS_POSITIVE_DIFF -> String.format("\u26a0\ufe0f Sobrante detectado: $%.2f", difference);
            case STATUS_NEGATIVE_DIFF -> String.format("\u274c Faltante detectado: $%.2f", difference.abs());
            default -> "Cierre ejecutado";
        };
    }
    @Transactional(readOnly=true)
    public List<ClosureResponse> getAllClosures() {
        List<DailyClosure> closures = this.closureRepositoryPort.findAllClosuresOrderByDateDesc();
        return closures.stream().map(arg_0 -> this.mapToClosureResponse(arg_0)).toList();
    }
    private ClosureResponse mapToClosureResponse(DailyClosure closure) {
        BigDecimal expectedCash = closure.getTotalExpectedCash() != null ? closure.getTotalExpectedCash() : BigDecimal.ZERO;
        BigDecimal expectedCard = closure.getTotalExpectedCard() != null ? closure.getTotalExpectedCard() : BigDecimal.ZERO;
        BigDecimal expectedNequi = closure.getTotalExpectedNequi() != null ? closure.getTotalExpectedNequi() : BigDecimal.ZERO;
        BigDecimal expectedQr = closure.getTotalExpectedQr() != null ? closure.getTotalExpectedQr() : BigDecimal.ZERO;
        BigDecimal countedCash = closure.getTotalCountedCash() != null ? closure.getTotalCountedCash() : BigDecimal.ZERO;
        BigDecimal countedCard = closure.getTotalCountedCard() != null ? closure.getTotalCountedCard() : BigDecimal.ZERO;
        BigDecimal countedNequi = closure.getTotalCountedNequi() != null ? closure.getTotalCountedNequi() : BigDecimal.ZERO;
        BigDecimal countedQr = closure.getTotalCountedQr() != null ? closure.getTotalCountedQr() : BigDecimal.ZERO;
        BigDecimal totalExpected = expectedCash.add(expectedCard).add(expectedNequi).add(expectedQr);
        BigDecimal totalCounted = countedCash.add(countedCard).add(countedNequi).add(countedQr);
        return new ClosureResponse(closure.getId(), closure.getUserName(), closure.getOpeningTime(), closure.getClosingTime(), expectedCash, expectedCard, expectedNequi, expectedQr, totalExpected, countedCash, countedCard, countedNequi, countedQr, totalCounted, closure.getDifferenceAmount(), closure.getStatus(), closure.getNotes(), this.generateClosureMessage(closure.getStatus(), closure.getDifferenceAmount()), closure.getBaseBalanceForNextDay());
    }
    public DailyClosureServiceImpl(DailyClosureRepositoryPort closureRepositoryPort, OrderRepository orderRepository) {
        this.closureRepositoryPort = closureRepositoryPort;
        this.orderRepository = orderRepository;
    }
}
