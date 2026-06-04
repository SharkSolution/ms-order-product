package com.suresell.orders.application.usecase;
import com.suresell.orders.application.dto.ApplyDiscountCommand;
import com.suresell.orders.application.dto.ApplyDiscountResult;
import com.suresell.orders.application.dto.LinkOrderCouponCommand;
import com.suresell.orders.application.dto.OrderItemDto;
import com.suresell.orders.application.dto.OrderItemRequestRecord;
import com.suresell.orders.application.dto.OrderItemResponseRecord;
import com.suresell.orders.application.dto.OrderRequestRecord;
import com.suresell.orders.application.dto.OrderResponseRecord;
import com.suresell.orders.application.dto.ProductResponse;
import com.suresell.orders.domain.model.SyncOutbox;
import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderDeliveryTracking;
import com.suresell.orders.domain.model.OrderEditHistory;
import com.suresell.orders.domain.model.OrderItem;
import com.suresell.orders.domain.model.OrderStatus;
import com.suresell.orders.domain.port.out.SyncOutboxRepositoryPort;
import com.suresell.orders.domain.port.in.DiscountPort;
import com.suresell.orders.domain.port.in.OrderPort;
import com.suresell.orders.domain.port.out.OrderEditHistoryRepositoryPort;
import com.suresell.orders.domain.port.out.OrderDeliveryTrackingRepositoryPort;
import com.suresell.orders.domain.port.out.OrderItemRepositoryPort;
import com.suresell.orders.domain.port.out.OrderRepositoryPort;
import com.suresell.orders.domain.port.out.ProductCatalogPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suresell.orders.application.dto.PagerAvailabilityDto;
import com.suresell.orders.application.dto.PagerAvailabilityResponse;
import com.suresell.orders.domain.model.PagerColor;
import com.suresell.orders.shared.exception.OrderEditNotAllowedException;
import com.suresell.orders.shared.exception.PagerOcupadoException;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@Primary
@RequiredArgsConstructor
public class OrderHandler implements OrderPort {
    private static final Logger log = LoggerFactory.getLogger(OrderHandler.class);
    private final OrderRepositoryPort orderRepositoryPort;
    private final OrderDeliveryTrackingRepositoryPort orderDeliveryTrackingRepositoryPort;
    private final SyncOutboxRepositoryPort syncOutboxRepositoryPort;
    private final OrderItemRepositoryPort orderItemRepositoryPort;
    private final ProductCatalogPort productCatalogPort;
    private final DiscountPort discountService;
    private final OrderEditHistoryRepositoryPort orderEditHistoryRepository;
    private final ObjectMapper objectMapper;
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private static final int MAX_EDIT_MINUTES = 7;

    @Override
    public PagerAvailabilityResponse getPagerAvailability() {
        List<Order> activeOrders = orderRepositoryPort.findActiveOrdersWithItems(OrderStatus.pagado);
        Set<String> occupiedPagers = activeOrders.stream()
                .filter(o -> {
                    OrderDeliveryTracking dt = o.getDeliveryTracking();
                    return o.getPagerColor() != null && o.getPagerNumber() != null &&
                           (dt == null || (!Boolean.TRUE.equals(dt.getDelivered()) && !Boolean.TRUE.equals(dt.getPagerReturned())));
                })
                .map(o -> o.getPagerColor().toUpperCase() + "-" + o.getPagerNumber())
                .collect(Collectors.toSet());
        List<PagerAvailabilityDto> available = new ArrayList<>();
        List<PagerAvailabilityDto> occupied = new ArrayList<>();
        for (PagerColor color : PagerColor.values()) {
            for (int i = 1; i <= 16; i++) {
                String number = String.valueOf(i);
                boolean isOccupied = occupiedPagers.contains(color.name() + "-" + number);
                PagerAvailabilityDto dto = new PagerAvailabilityDto(color, number, !isOccupied);
                if (isOccupied) {
                    occupied.add(dto);
                } else {
                    available.add(dto);
                }
            }
        }
        return new PagerAvailabilityResponse(available, occupied);
    }

    @Override
    @Transactional
    public Order createOrUpdateOrder(OrderRequestRecord dto) {
        validatePaymentMethod(dto.paymentMethod());
        validatePagerAvailability(dto.pagerColor(), dto.pagerNumber(), null);
        Order order = new Order();
        order.setPagerColor(dto.pagerColor());
        order.setPagerNumber(dto.pagerNumber());
        order.setStatus(OrderStatus.pagado);
        order.setPaymentMethod(dto.paymentMethod());
        order.setWaiterId(dto.waiterId());
        order.setWaiterName(dto.waiterName());
        order.setCreatedAt(LocalDateTime.now(BOGOTA_ZONE));
        
        BigDecimal subtotal = dto.items().stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setSubtotal(subtotal);
        BigDecimal total = applyDiscountIfPresent(order, dto.discountCode(), subtotal);
        order.setTotal(total);
        
        // 1. Guardar la Orden (genera idOrder numérico)
        Order savedOrder = orderRepositoryPort.save(order);
        
        // 2. Recuperar ID generado
        Long numericId = orderRepositoryPort.findNumericIdByUuid(savedOrder.getUuidId())
                .orElseThrow(() -> new RuntimeException("Error al recuperar ID numérico de la orden recién creada"));
        
        savedOrder.setIdOrder(numericId);

        // 3. Crear y Guardar Items individualmente con el ID numérico poblado
        List<OrderItem> items = createOrderItems(savedOrder, dto.items());
        for (OrderItem item : items) {
            orderItemRepositoryPort.save(item);
        }
        savedOrder.setItems(items);

        // 4. Crear e Insertar el tracking con el ID ya conocido
        OrderDeliveryTracking tracking = new OrderDeliveryTracking();
        tracking.setOrder(savedOrder);
        tracking.setOrderId(numericId);
        tracking.setOrderIdUuid(savedOrder.getUuidId());
        tracking.setDelivered(false);
        tracking.setPreparationDurationSeconds(null);
        
        orderDeliveryTrackingRepositoryPort.save(tracking);
        savedOrder.setDeliveryTracking(tracking);

        saveOrderCreatedOutbox(savedOrder, tracking);
        linkCouponIfPresent(savedOrder);
        return savedOrder;
    }

    @Override
    public List<OrderResponseRecord> getAllOrders() {
        List<Order> orders = orderRepositoryPort.findAllWithItems();
        Map<String, String> productNames = buildProductNameCacheFromOrders(orders);
        return orders.stream().map(order -> toOrderResponseRecord(order, productNames)).toList();
    }

    @Override
    public Page<OrderResponseRecord> getAllOrdersPaginated(String pagerColor, String pagerNumber, Long idOrder, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> ordersPage = orderRepositoryPort.findAllOrdersOnly(pagerColor, pagerNumber, idOrder, pageable);
        if (ordersPage.isEmpty()) {
            return Page.empty(pageable);
        }
        List<Long> orderIds = ordersPage.getContent().stream().map(Order::getIdOrder).toList();
        List<OrderItem> items = orderItemRepositoryPort.findByOrderIds(orderIds);
        Map<Long, List<OrderItem>> itemsByOrderId = items.stream()
                .collect(Collectors.groupingBy(oi -> oi.getOrder().getIdOrder()));
        ordersPage.getContent().forEach(order -> {
            List<OrderItem> orderItems = itemsByOrderId.getOrDefault(order.getIdOrder(), new ArrayList<>());
            order.setItems(orderItems);
        });
        Map<String, String> productNames = buildProductNameCacheFromOrders(ordersPage.getContent());
        return ordersPage.map(order -> toOrderResponseRecord(order, productNames));
    }

    @Override
    public List<OrderResponseRecord> getAllOrdersKeyset(Long afterId, int size) {
        Pageable pageable = PageRequest.of(0, size);
        List<Order> orders = orderRepositoryPort.findOrdersAfter(afterId, pageable);
        if (orders.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> orderIds = orders.stream().map(Order::getIdOrder).toList();
        List<OrderItem> items = orderItemRepositoryPort.findByOrderIds(orderIds);
        Map<Long, List<OrderItem>> itemsByOrderId = items.stream()
                .collect(Collectors.groupingBy(oi -> oi.getOrder().getIdOrder()));
        orders.forEach(order -> {
            List<OrderItem> orderItems = itemsByOrderId.getOrDefault(order.getIdOrder(), new ArrayList<>());
            order.setItems(orderItems);
        });
        Map<String, String> productNames = buildProductNameCacheFromOrders(orders);
        return orders.stream().map(order -> toOrderResponseRecord(order, productNames)).toList();
    }

    @Override
    public OrderResponseRecord getOrderById(Long orderId) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada con ID: " + orderId));
        Map<String, String> productNames = buildProductNameCache(order.getItems());
        return toOrderResponseRecord(order, productNames);
    }

    @Override
    @Transactional
    public void updateOrder(Long orderId, OrderRequestRecord dto) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada con ID: " + orderId));
        LocalDateTime now = LocalDateTime.now(BOGOTA_ZONE);
        long minutesSinceCreation = ChronoUnit.MINUTES.between(order.getCreatedAt(), now);
        if (minutesSinceCreation > MAX_EDIT_MINUTES) {
            throw new OrderEditNotAllowedException(
                    String.format(
                            "No se puede editar la orden #%d. Han pasado %d minutos desde su creación (máximo permitido: %d minutos)",
                            orderId,
                            minutesSinceCreation,
                            MAX_EDIT_MINUTES),
                    "ORDER_EDIT_TIME_EXCEEDED");
        }
        if (!(order.getPagerColor().equals(dto.pagerColor()) && order.getPagerNumber().equals(dto.pagerNumber()))) {
            validatePagerAvailability(dto.pagerColor(), dto.pagerNumber(), orderId);
        }
        List<OrderItem> previousItems = new ArrayList<>(order.getItems());
        BigDecimal previousTotal = order.getTotal();
        order.setPagerColor(dto.pagerColor());
        order.setPagerNumber(dto.pagerNumber());
        order.getItems().clear();
        List<OrderItem> newItems = createOrderItems(order, dto.items());
        order.getItems().addAll(newItems);
        BigDecimal subtotal = order.getItems().stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setSubtotal(subtotal);
        BigDecimal total = applyDiscountIfPresent(order, dto.discountCode(), subtotal);
        order.setTotal(total);
        Order savedOrder = orderRepositoryPort.save(order);
        saveOrderCreatedOutbox(savedOrder, savedOrder.getDeliveryTracking());
        saveEditHistory(orderId, previousItems, order.getItems(), previousTotal, order.getTotal());
    }

    private void saveTrackingToOutbox(OrderDeliveryTracking tracking) {
        try {
            Map<String, Object> trackingPayload = new HashMap<>();
            trackingPayload.put("orderId", tracking.getOrderId());
            trackingPayload.put("orderUuidId", tracking.getOrderIdUuid());
            trackingPayload.put("delivered", Boolean.TRUE.equals(tracking.getDelivered()));
            trackingPayload.put("pagerReturned", Boolean.TRUE.equals(tracking.getPagerReturned()));
            trackingPayload.put("preparationDurationSeconds", tracking.getPreparationDurationSeconds());
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", "TRACKING_UPDATED");
            payload.put("orderId", tracking.getOrderId());
            payload.put("orderUuidId", tracking.getOrderIdUuid());
            payload.put("tracking", trackingPayload);
            SyncOutbox outbox = new SyncOutbox();
            outbox.setAggregateType("TRACKING");
            outbox.setAggregateId(tracking.getOrderId());
            outbox.setEventType("TRACKING_UPDATED");
            outbox.setPayloadJson(objectMapper.writeValueAsString(payload));
            outbox.setStatus("PENDING");
            outbox.setAttempts(0);
            outbox.setNextRetryAt(System.currentTimeMillis());
            outbox.setCreatedAt(System.currentTimeMillis());
            outbox.setUpdatedAt(System.currentTimeMillis());
            syncOutboxRepositoryPort.save(outbox);
        } catch (Exception e) {
            log.error("Error encolando sincronización de tracking: {}", e.getMessage());
        }
    }

    private void saveEditHistory(Long orderId, List<OrderItem> oldItems, List<OrderItem> newItems, BigDecimal oldTotal, BigDecimal newTotal) {
        LocalDateTime now = LocalDateTime.now(BOGOTA_ZONE);
        Set<String> productIds = new LinkedHashSet<>();
        oldItems.forEach(item -> productIds.add(item.getProductId()));
        newItems.forEach(item -> productIds.add(item.getProductId()));
        Map<String, String> productNames = buildProductNameCacheByIds(productIds);
        for (OrderItem oldItem : oldItems) {
            boolean found = newItems.stream().anyMatch(n -> n.getProductId().equals(oldItem.getProductId()));
            if (!found) {
                OrderEditHistory history = new OrderEditHistory();
                history.setOrderId(orderId);
                history.setEditType("ITEM_REMOVED");
                history.setProductId(oldItem.getProductId());
                history.setProductName(productNames.getOrDefault(oldItem.getProductId(), oldItem.getProductId()));
                history.setOldQuantity(oldItem.getQuantity());
                history.setNewQuantity(0);
                history.setOldTotal(oldTotal);
                history.setNewTotal(newTotal);
                history.setEditedAt(now);
                OrderEditHistory savedHistory = orderEditHistoryRepository.save(history);
                saveEditHistoryToOutbox(savedHistory);
            }
        }
        for (OrderItem newItem : newItems) {
            OrderItem oldItem = oldItems.stream()
                    .filter(o -> o.getProductId().equals(newItem.getProductId()))
                    .findFirst()
                    .orElse(null);
            if (oldItem == null) {
                OrderEditHistory history = new OrderEditHistory();
                history.setOrderId(orderId);
                history.setEditType("ITEM_ADDED");
                history.setProductId(newItem.getProductId());
                history.setProductName(productNames.getOrDefault(newItem.getProductId(), newItem.getProductId()));
                history.setOldQuantity(0);
                history.setNewQuantity(newItem.getQuantity());
                history.setOldTotal(oldTotal);
                history.setNewTotal(newTotal);
                history.setEditedAt(now);
                OrderEditHistory savedHistory = orderEditHistoryRepository.save(history);
                saveEditHistoryToOutbox(savedHistory);
            } else if (oldItem.getQuantity() != newItem.getQuantity()) {
                OrderEditHistory history = new OrderEditHistory();
                history.setOrderId(orderId);
                history.setEditType("ITEM_QUANTITY_CHANGED");
                history.setProductId(newItem.getProductId());
                history.setProductName(productNames.getOrDefault(newItem.getProductId(), newItem.getProductId()));
                history.setOldQuantity(oldItem.getQuantity());
                history.setNewQuantity(newItem.getQuantity());
                history.setOldTotal(oldTotal);
                history.setNewTotal(newTotal);
                history.setEditedAt(now);
                OrderEditHistory savedHistory = orderEditHistoryRepository.save(history);
                saveEditHistoryToOutbox(savedHistory);
            }
        }
    }

    private void saveEditHistoryToOutbox(OrderEditHistory history) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", "EDIT_HISTORY_CREATED");
            payload.put("history", history);
            SyncOutbox outbox = new SyncOutbox();
            outbox.setAggregateType("EDIT_HISTORY");
            outbox.setAggregateId(history.getId());
            outbox.setEventType("EDIT_HISTORY_CREATED");
            outbox.setPayloadJson(objectMapper.writeValueAsString(payload));
            outbox.setStatus("PENDING");
            outbox.setAttempts(0);
            outbox.setNextRetryAt(System.currentTimeMillis());
            outbox.setCreatedAt(System.currentTimeMillis());
            outbox.setUpdatedAt(System.currentTimeMillis());
            syncOutboxRepositoryPort.save(outbox);
        } catch (Exception e) {
            log.error("Error encolando sincronización de historial de edición: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public OrderResponseRecord applyDiscountToOrder(Long orderId, String discountCode) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada con ID: " + orderId));
        if (order.getDiscountCode() != null) {
            throw new IllegalStateException("La orden ya tiene un descuento aplicado: " + order.getDiscountCode());
        }
        BigDecimal subtotal = order.getSubtotal();
        Map<String, ProductResponse> productCache = buildProductCache(order.getItems());
        List<OrderItemDto> itemsForDiscount = order.getItems().stream().map(item -> {
            ProductResponse productDetails = productCache.get(item.getProductId());
            Long productIdLong = null;
            try {
                productIdLong = Long.parseLong(item.getProductId());
            } catch (NumberFormatException ignored) {
            }
            return new OrderItemDto(
                    item.getProductId(),
                    productIdLong,
                    productDetails != null ? productDetails.nameProduct() : null,
                    productDetails != null ? productDetails.categoryName() : null,
                    item.getQuantity(),
                    item.getUnitPrice());
        }).toList();
        ApplyDiscountCommand command = new ApplyDiscountCommand(
                discountCode,
                LocalDateTime.now(BOGOTA_ZONE),
                itemsForDiscount,
                subtotal);
        ApplyDiscountResult discountResult = discountService.applyDiscount(command);
        if (!discountResult.valid()) {
            throw new IllegalArgumentException("Cupón inválido: " + discountResult.message());
        }
        order.setDiscountCode(discountResult.discountCode());
        order.setDiscountAmount(discountResult.discountAmount());
        order.setDiscountPercentage(discountResult.discountPercentage());
        BigDecimal total = discountResult.newSubtotal();
        order.setTotal(total);
        Order savedOrder = orderRepositoryPort.save(order);
        LinkOrderCouponCommand linkCommand = new LinkOrderCouponCommand(
                savedOrder.getIdOrder(),
                savedOrder.getDiscountCode(),
                savedOrder.getSubtotal(),
                savedOrder.getDiscountAmount(),
                savedOrder.getTotal());
        discountService.linkOrderWithCoupon(linkCommand);
        return toOrderResponseRecord(savedOrder, buildProductNameCache(savedOrder.getItems()));
    }

    @Override
    public Page<OrderEditHistory> getOrderEditHistory(Long orderId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (orderId != null) {
            return orderEditHistoryRepository.findByOrderIdOrderByEditedAtDesc(orderId, pageable);
        }
        return orderEditHistoryRepository.findAllByOrderByEditedAtDesc(pageable);
    }

    @Override
    @Transactional
    public void markAsDeliveredLocally(Long orderId) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada con ID: " + orderId));
        
        OrderDeliveryTracking tracking = order.getDeliveryTracking();
        boolean isNewTracking = false;
        if (tracking == null) {
            tracking = new OrderDeliveryTracking();
            tracking.setOrder(order);
            tracking.setOrderId(order.getIdOrder());
            tracking.setOrderIdUuid(order.getUuidId());
            order.setDeliveryTracking(tracking);
            isNewTracking = true;
        } else {
            if (tracking.getOrderId() == null) {
                tracking.setOrderId(order.getIdOrder());
            }
            if (tracking.getOrderIdUuid() == null) {
                tracking.setOrderIdUuid(order.getUuidId());
            }
        }
        
        tracking.setDelivered(true);
        tracking.setPagerReturned(true);
        
        if (isNewTracking) {
            orderDeliveryTrackingRepositoryPort.save(tracking);
        }
        
        orderRepositoryPort.save(order);
        log.info("Orden #{} marcada como ENTREGADA y Pager liberado MANUALMENTE (Acción de Cajero).", orderId);
        saveTrackingToOutbox(tracking);
    }

    @Override
    @Transactional
    public void markAsPrinted(Long orderId) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada con ID: " + orderId));

        if (Boolean.TRUE.equals(order.getIsPrinted())) {
            log.info("La orden #{} ya estaba marcada como IMPRESA. Se omite actualización.", orderId);
            return;
        }

        order.setIsPrinted(true);
        orderRepositoryPort.save(order);
        log.info("Orden #{} marcada como IMPRESA físicamente (Contingencia).", orderId);
        saveOrderPrintedStatusToOutbox(order);
    }

    private void saveOrderPrintedStatusToOutbox(Order order) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", "ORDER_PRINTED_UPDATED");
            payload.put("aggregateType", "ORDER");
            payload.put("aggregateId", order.getIdOrder());
            payload.put("idOrder", order.getIdOrder());
            payload.put("uuidId", order.getUuidId());
            payload.put("isPrinted", order.getIsPrinted());
            payload.put("updatedAt", LocalDateTime.now(BOGOTA_ZONE).toString());

            SyncOutbox outbox = new SyncOutbox();
            outbox.setAggregateType("ORDER");
            outbox.setAggregateId(order.getIdOrder());
            outbox.setEventType("ORDER_PRINTED_UPDATED");
            outbox.setPayloadJson(objectMapper.writeValueAsString(payload));
            outbox.setStatus("PENDING");
            outbox.setAttempts(0);
            outbox.setNextRetryAt(System.currentTimeMillis());
            outbox.setCreatedAt(System.currentTimeMillis());
            outbox.setUpdatedAt(System.currentTimeMillis());

            syncOutboxRepositoryPort.save(outbox);
        } catch (JsonProcessingException e) {
            log.error("Error critico serializando evento outbox para orden {}", order.getIdOrder());
            throw new RuntimeException("Fallo al generar payload de sincronizacion", e);
        }
    }

    private void validatePaymentMethod(String paymentMethod) {
        if (paymentMethod == null || !List.of("CASH", "CARD", "NEQUI", "QR").contains(paymentMethod)) {
            throw new IllegalArgumentException("Método de pago inválido. Debe ser: CASH, CARD, NEQUI o QR");
        }
    }

    private void saveOrderCreatedOutbox(Order order, OrderDeliveryTracking tracking) {
        try {
            long now = System.currentTimeMillis();
            SyncOutbox outbox = new SyncOutbox();
            outbox.setAggregateType("ORDER");
            outbox.setAggregateId(order.getIdOrder());
            outbox.setEventType("ORDER_CREATED");
            outbox.setPayloadJson(buildOrderCreatedPayload(order, tracking));
            outbox.setStatus("PENDING");
            outbox.setAttempts(0);
            outbox.setNextRetryAt(now);
            outbox.setLastError(null);
            outbox.setCreatedAt(now);
            outbox.setUpdatedAt(now);
            outbox.setSyncedAt(null);
            syncOutboxRepositoryPort.save(outbox);
        } catch (Exception e) {
            log.error("Error encolando sincronización de orden {}: {}", order.getIdOrder(), e.getMessage());
        }
    }

    private String buildOrderCreatedPayload(Order order, OrderDeliveryTracking tracking) {
        Map<String, Object> trackingPayload = new HashMap<>();
        trackingPayload.put("orderId", order.getIdOrder());
        trackingPayload.put("orderUuidId", order.getUuidId());
        trackingPayload.put("delivered", Boolean.TRUE.equals(tracking.getDelivered()));
        trackingPayload.put("pagerReturned", Boolean.TRUE.equals(tracking.getPagerReturned()));
        trackingPayload.put("preparationDurationSeconds", tracking.getPreparationDurationSeconds());
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "ORDER_CREATED");
        payload.put("aggregateType", "ORDER");
        payload.put("aggregateId", order.getIdOrder());
        payload.put("order", buildOrderPayload(order));
        payload.put("tracking", trackingPayload);
        payload.put("createdAt", LocalDateTime.now(BOGOTA_ZONE).toString());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el payload del outbox para la orden " + order.getIdOrder(), ex);
        }
    }

    private Map<String, Object> buildOrderPayload(Order order) {
        List<Map<String, Object>> items = order.getItems() == null
                ? List.of()
                : order.getItems().stream()
                        .map(item -> {
                            Map<String, Object> itemPayload = new HashMap<>();
                            itemPayload.put("idOrderItem", item.getIdOrderItem());
                            itemPayload.put("uuidId", item.getUuidId());
                            itemPayload.put("productId", item.getProductId());
                            itemPayload.put("quantity", item.getQuantity());
                            itemPayload.put("unitPrice", item.getUnitPrice());
                            itemPayload.put("totalPrice", item.getTotalPrice());
                            itemPayload.put("instructions", item.getInstructions());
                            itemPayload.put("comboGroup", item.getComboGroup());
                            return itemPayload;
                        })
                        .toList();
        Map<String, Object> orderPayload = new HashMap<>();
        orderPayload.put("idOrder", order.getIdOrder());
        orderPayload.put("uuidId", order.getUuidId());
        orderPayload.put("pagerColor", order.getPagerColor());
        orderPayload.put("pagerNumber", order.getPagerNumber());
        orderPayload.put("createdAt", order.getCreatedAt());
        orderPayload.put("status", order.getStatus().name());
        orderPayload.put("paymentMethod", order.getPaymentMethod());
        orderPayload.put("subtotal", order.getSubtotal());
        orderPayload.put("total", order.getTotal());
        orderPayload.put("discountCode", order.getDiscountCode());
        orderPayload.put("discountPercentage", order.getDiscountPercentage());
        orderPayload.put("discountAmount", order.getDiscountAmount());
        orderPayload.put("isPrinted", Boolean.TRUE.equals(order.getIsPrinted()));
        orderPayload.put("waiterId", order.getWaiterId());
        orderPayload.put("waiterName", order.getWaiterName());
        orderPayload.put("items", items);
        return orderPayload;
    }

    private void validatePagerAvailability(String pagerColor, String pagerNumber, Long excludeOrderId) {
        Optional<Order> existingPagerOrder = orderRepositoryPort.findOccupiedPagerOrder(
                pagerColor, pagerNumber, OrderStatus.pagado);
        if (existingPagerOrder.isPresent()
                && (excludeOrderId == null || !existingPagerOrder.get().getIdOrder().equals(excludeOrderId))) {
            throw new PagerOcupadoException(
                    String.format(
                            "El pager %s %s ya está en uso por la orden #%d",
                            pagerColor,
                            pagerNumber,
                            existingPagerOrder.get().getIdOrder()),
                    "PAGER_OCUPADO");
        }
    }

    private List<OrderItem> createOrderItems(Order order, List<OrderItemRequestRecord> itemDtos) {
        return itemDtos.stream().map(itemDto -> {
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setOrderId(order.getIdOrder()); // ASIGNACIÓN CRÍTICA PARA SQLITE
            item.setProductId(itemDto.productId());
            item.setQuantity(itemDto.quantity());
            item.setUnitPrice(itemDto.unitPrice());
            item.setTotalPrice(itemDto.unitPrice().multiply(BigDecimal.valueOf(itemDto.quantity())));
            item.setInstructions(itemDto.instructions());
            item.setComboGroup(itemDto.comboGroup());
            return item;
        }).toList();
    }

    private BigDecimal applyDiscountIfPresent(Order order, String discountCode, BigDecimal subtotal) {
        if (discountCode == null || discountCode.trim().isEmpty()) {
            return subtotal;
        }
        Map<String, ProductResponse> productCache = buildProductCache(order.getItems());
        List<OrderItemDto> itemsForDiscount = order.getItems().stream().map(item -> {
            ProductResponse productDetails = productCache.get(item.getProductId());
            Long productIdLong = null;
            try {
                productIdLong = Long.parseLong(item.getProductId());
            } catch (NumberFormatException ignored) {
            }
            return new OrderItemDto(
                    item.getProductId(),
                    productIdLong,
                    productDetails != null ? productDetails.nameProduct() : null,
                    productDetails != null ? productDetails.categoryName() : null,
                    item.getQuantity(),
                    item.getUnitPrice());
        }).toList();
        ApplyDiscountCommand command = new ApplyDiscountCommand(
                discountCode,
                LocalDateTime.now(BOGOTA_ZONE),
                itemsForDiscount,
                subtotal);
        ApplyDiscountResult discountResult = discountService.applyDiscount(command);
        if (discountResult.valid()) {
            order.setDiscountCode(discountResult.discountCode());
            order.setDiscountAmount(discountResult.discountAmount());
            order.setDiscountPercentage(discountResult.discountPercentage());
            return discountResult.newSubtotal();
        }
        return subtotal;
    }

    private void linkCouponIfPresent(Order order) {
        if (order.getDiscountCode() != null) {
            LinkOrderCouponCommand linkCommand = new LinkOrderCouponCommand(
                    order.getIdOrder(),
                    order.getDiscountCode(),
                    order.getSubtotal(),
                    order.getDiscountAmount(),
                    order.getTotal());
            discountService.linkOrderWithCoupon(linkCommand);
        }
    }

    private OrderResponseRecord toOrderResponseRecord(Order order, Map<String, String> productNames) {
        List<OrderItemResponseRecord> items = order.getItems() == null
                ? List.of()
                : order.getItems().stream().map(item -> toOrderItemResponseRecord(item, productNames)).toList();
        OrderDeliveryTracking deliveryTracking = order.getDeliveryTracking();
        boolean delivered = deliveryTracking != null && Boolean.TRUE.equals(deliveryTracking.getDelivered());
        Integer preparationDurationSeconds = deliveryTracking != null
                ? deliveryTracking.getPreparationDurationSeconds()
                : null;
        return new OrderResponseRecord(
                order.getIdOrder(),
                order.getPagerColor(),
                order.getPagerNumber(),
                order.getCreatedAt(),
                order.getSubtotal(),
                order.getTotal(),
                order.getStatus().getDisplayName(),
                order.getPaymentMethod(),
                order.getDiscountCode(),
                order.getDiscountPercentage(),
                order.getDiscountAmount(),
                delivered,
                Boolean.TRUE.equals(order.getSynced()),
                Boolean.TRUE.equals(order.getIsPrinted()),
                preparationDurationSeconds,
                items,
                order.getWaiterName());
    }

    private OrderItemResponseRecord toOrderItemResponseRecord(OrderItem item, Map<String, String> productNames) {
        return new OrderItemResponseRecord(
                item.getProductId(),
                productNames.getOrDefault(item.getProductId(), item.getProductId()),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice(),
                item.getInstructions(),
                item.getComboGroup());
    }

    private Map<String, String> buildProductNameCacheFromOrders(List<Order> orders) {
        Set<String> productIds = new LinkedHashSet<>();
        for (Order order : orders) {
            if (order.getItems() != null) {
                order.getItems().forEach(item -> productIds.add(item.getProductId()));
            }
        }
        return buildProductNameCacheByIds(productIds);
    }

    private Map<String, String> buildProductNameCache(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }
        Set<String> productIds = items.stream()
                .map(OrderItem::getProductId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return buildProductNameCacheByIds(productIds);
    }

    private Map<String, String> buildProductNameCacheByIds(Set<String> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<String, ProductResponse> productCache = productCatalogPort.findProductsByIds(productIds);
        Map<String, String> names = productCache.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().nameProduct()));
        for (String productId : productIds) {
            names.putIfAbsent(productId, productId);
        }
        return names;
    }

    private Map<String, ProductResponse> buildProductCache(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }
        Set<String> productIds = items.stream()
                .map(OrderItem::getProductId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return productCatalogPort.findProductsByIds(productIds);
    }

    @Override
    @Transactional
    public void releasePager(String color, String number) {
        List<Order> activeOrders = orderRepositoryPort.findActiveOrdersWithItems(OrderStatus.pagado);
        
        Optional<Order> targetOrder = activeOrders.stream()
                .filter(o -> color.equalsIgnoreCase(o.getPagerColor()) && number.equalsIgnoreCase(o.getPagerNumber()))
                .filter(o -> {
                    OrderDeliveryTracking dt = o.getDeliveryTracking();
                    return dt == null || (!dt.getDelivered() && !dt.getPagerReturned());
                })
                .findFirst();

        if (targetOrder.isPresent()) {
            markAsDeliveredLocally(targetOrder.get().getIdOrder());
        } else {
            log.warn("Intento de liberar Pager {} {} fallido: No se encontró orden activa asociada.", color, number);
        }
    }
}
