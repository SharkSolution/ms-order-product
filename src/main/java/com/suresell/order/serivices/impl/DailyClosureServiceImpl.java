/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.entity.DailyClosure
 *  com.suresell.order.model.entity.Order
 *  com.suresell.order.model.enums.OrderStatus
 *  com.suresell.order.model.record.ClosurePreviewResponse
 *  com.suresell.order.model.record.ClosureRequest
 *  com.suresell.order.model.record.ClosureResponse
 *  com.suresell.order.repository.DailyClosureRepository
 *  com.suresell.order.repository.OrderRepository
 *  com.suresell.order.serivices.DailyClosureService
 *  com.suresell.order.serivices.impl.DailyClosureServiceImpl
 *  lombok.Generated
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 */
package com.suresell.order.serivices.impl;

import com.suresell.order.model.entity.DailyClosure;
import com.suresell.order.model.entity.Order;
import com.suresell.order.model.enums.OrderStatus;
import com.suresell.order.model.record.ClosurePreviewResponse;
import com.suresell.order.model.record.ClosureRequest;
import com.suresell.order.model.record.ClosureResponse;
import com.suresell.order.repository.DailyClosureRepository;
import com.suresell.order.repository.OrderRepository;
import com.suresell.order.serivices.DailyClosureService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import lombok.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyClosureServiceImpl
implements DailyClosureService {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(DailyClosureServiceImpl.class);
    private final DailyClosureRepository closureRepository;
    private final OrderRepository orderRepository;
    private static final String PAYMENT_CASH = "CASH";
    private static final String PAYMENT_CARD = "CARD";
    private static final String PAYMENT_NEQUI = "NEQUI";
    private static final String PAYMENT_QR = "QR";
    private static final String STATUS_BALANCED = "BALANCED";
    private static final String STATUS_POSITIVE_DIFF = "POSITIVE_DIFF";
    private static final String STATUS_NEGATIVE_DIFF = "NEGATIVE_DIFF";

    @Transactional(readOnly=true)
    public ClosurePreviewResponse getClosurePreview() {
        log.info("Generando preview de cierre de caja");
        LocalDateTime openingTime = this.determineOpeningTime();
        LocalDateTime currentTime = LocalDateTime.now();
        List orders = this.getOrdersForPeriod(openingTime, currentTime);
        log.info("\u00d3rdenes encontradas para el per\u00edodo: {} (desde {} hasta {})", new Object[]{orders.size(), openingTime, currentTime});
        BigDecimal totalCash = this.calculateTotalByPaymentMethod(orders, PAYMENT_CASH);
        BigDecimal totalCard = this.calculateTotalByPaymentMethod(orders, PAYMENT_CARD);
        BigDecimal totalNequi = this.calculateTotalByPaymentMethod(orders, PAYMENT_NEQUI);
        BigDecimal totalQr = this.calculateTotalByPaymentMethod(orders, PAYMENT_QR);
        BigDecimal totalExpected = totalCash.add(totalCard).add(totalNequi).add(totalQr);
        return new ClosurePreviewResponse(openingTime, currentTime, totalCash, totalCard, totalNequi, totalQr, totalExpected, orders.size(), "Preview de cierre generado correctamente");
    }

    @Transactional
    public ClosureResponse executeClosure(ClosureRequest request) {
        log.info("Ejecutando cierre de caja para cajero: {}", (Object)request.userName());
        LocalDateTime openingTime = this.determineOpeningTime();
        LocalDateTime closingTime = LocalDateTime.now();
        List orders = this.getOrdersForPeriod(openingTime, closingTime);
        BigDecimal expectedCash = this.calculateTotalByPaymentMethod(orders, PAYMENT_CASH);
        BigDecimal expectedCard = this.calculateTotalByPaymentMethod(orders, PAYMENT_CARD);
        BigDecimal expectedNequi = this.calculateTotalByPaymentMethod(orders, PAYMENT_NEQUI);
        BigDecimal expectedQr = this.calculateTotalByPaymentMethod(orders, PAYMENT_QR);
        BigDecimal countedCash = request.totalCountedCash();
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
        DailyClosure savedClosure = (DailyClosure)this.closureRepository.save((Object)closure);
        log.info("Cierre ejecutado exitosamente. ID: {}, Cajero: {}, Status: {}, Diferencia: {}", new Object[]{savedClosure.getId(), savedClosure.getUserName(), status, difference});
        return new ClosureResponse(savedClosure.getId(), savedClosure.getUserName(), savedClosure.getOpeningTime(), savedClosure.getClosingTime(), expectedCash, expectedCard, expectedNequi, expectedQr, totalExpected, countedCash, countedCard, countedNequi, countedQr, totalCounted, difference, status, savedClosure.getNotes(), this.generateClosureMessage(status, difference));
    }

    private LocalDateTime determineOpeningTime() {
        return this.closureRepository.findLastClosure().map(DailyClosure::getClosingTime).orElseGet(() -> {
            log.info("No se encontr\u00f3 cierre anterior. Usando inicio del d\u00eda");
            return LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        });
    }

    private List<Order> getOrdersForPeriod(LocalDateTime from, LocalDateTime to) {
        return this.orderRepository.findAll().stream().filter(order -> OrderStatus.PAGADO.equals((Object)order.getStatus())).filter(order -> order.getPaymentMethod() != null).filter(order -> {
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
        log.info("Obteniendo historial completo de cierres de caja");
        List closures = this.closureRepository.findAllClosuresOrderByDateDesc();
        log.info("Cierres encontrados: {}", (Object)closures.size());
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
        return new ClosureResponse(closure.getId(), closure.getUserName(), closure.getOpeningTime(), closure.getClosingTime(), expectedCash, expectedCard, expectedNequi, expectedQr, totalExpected, countedCash, countedCard, countedNequi, countedQr, totalCounted, closure.getDifferenceAmount(), closure.getStatus(), closure.getNotes(), this.generateClosureMessage(closure.getStatus(), closure.getDifferenceAmount()));
    }

    @Generated
    public DailyClosureServiceImpl(DailyClosureRepository closureRepository, OrderRepository orderRepository) {
        this.closureRepository = closureRepository;
        this.orderRepository = orderRepository;
    }
}

