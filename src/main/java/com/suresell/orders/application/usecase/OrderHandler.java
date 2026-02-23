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
import com.suresell.orders.domain.model.OrderSyncOutbox;
import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderDeliveryTracking;
import com.suresell.orders.domain.model.OrderEditHistory;
import com.suresell.orders.domain.model.OrderItem;
import com.suresell.orders.domain.model.OrderStatus;
import com.suresell.orders.domain.port.out.OrderSyncOutboxRepositoryPort;
import com.suresell.orders.domain.port.in.DiscountPort;
import com.suresell.orders.domain.port.in.OrderPort;
import com.suresell.orders.domain.port.out.OrderEditHistoryRepositoryPort;
import com.suresell.orders.domain.port.out.OrderDeliveryTrackingRepositoryPort;
import com.suresell.orders.domain.port.out.OrderItemRepositoryPort;
import com.suresell.orders.domain.port.out.OrderRepositoryPort;
import com.suresell.orders.domain.port.out.ProductCatalogPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class OrderHandler implements OrderPort {

    private final OrderRepositoryPort orderRepositoryPort;
    private final OrderDeliveryTrackingRepositoryPort orderDeliveryTrackingRepositoryPort;
    private final OrderSyncOutboxRepositoryPort orderSyncOutboxRepositoryPort;
    private final OrderItemRepositoryPort orderItemRepositoryPort;
    private final ProductCatalogPort productCatalogPort;
    private final DiscountPort discountService;
    private final OrderEditHistoryRepositoryPort orderEditHistoryRepository;
    private final ObjectMapper objectMapper;

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private static final int MAX_EDIT_MINUTES = 7;

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
        order.setCreatedAt(LocalDateTime.now(BOGOTA_ZONE));

        List<OrderItem> items = createOrderItems(order, dto.items());
        order.setItems(items);

        BigDecimal subtotal = order.getItems().stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setSubtotal(subtotal);

        BigDecimal total = applyDiscountIfPresent(order, dto.discountCode(), subtotal);
        order.setTotal(total);

        Order savedOrder = orderRepositoryPort.save(order);
        OrderDeliveryTracking tracking = createInitialDeliveryTracking(savedOrder);
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
    public Page<OrderResponseRecord> getAllOrdersPaginated(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> ordersPage = orderRepositoryPort.findAllOrdersOnly(pageable);

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

        orderRepositoryPort.save(order);
        saveEditHistory(orderId, previousItems, order.getItems(), previousTotal, order.getTotal());
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
                orderEditHistoryRepository.save(history);
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
                orderEditHistoryRepository.save(history);
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
                orderEditHistoryRepository.save(history);
            }
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

    private void validatePaymentMethod(String paymentMethod) {
        if (paymentMethod == null || !List.of("CASH", "CARD", "NEQUI", "QR").contains(paymentMethod)) {
            throw new IllegalArgumentException("Método de pago inválido. Debe ser: CASH, CARD, NEQUI o QR");
        }
    }

    private OrderDeliveryTracking createInitialDeliveryTracking(Order order) {
        OrderDeliveryTracking tracking = new OrderDeliveryTracking();
        tracking.setOrder(order);
        tracking.setDelivered(false);
        tracking.setPreparationDurationSeconds(null);
        return orderDeliveryTrackingRepositoryPort.save(tracking);
    }

    private void saveOrderCreatedOutbox(Order order, OrderDeliveryTracking tracking) {
        long now = System.currentTimeMillis();
        OrderSyncOutbox outbox = new OrderSyncOutbox();
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
        orderSyncOutboxRepositoryPort.save(outbox);
    }

    private String buildOrderCreatedPayload(Order order, OrderDeliveryTracking tracking) {
        Map<String, Object> trackingPayload = new HashMap<>();
        trackingPayload.put("orderId", order.getIdOrder());
        trackingPayload.put("delivered", Boolean.TRUE.equals(tracking.getDelivered()));
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
                preparationDurationSeconds,
                items);
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
}
