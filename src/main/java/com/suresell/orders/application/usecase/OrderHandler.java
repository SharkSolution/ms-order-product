package com.suresell.orders.application.usecase;

import com.suresell.orders.shared.exception.AdminPasswordException;
import com.suresell.orders.shared.exception.OrderEditNotAllowedException;
import com.suresell.orders.shared.exception.PagerOcupadoException;
import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderEditHistory;
import com.suresell.orders.domain.model.OrderItem;
import com.suresell.orders.domain.model.OrderStatus;
import com.suresell.orders.application.dto.ApplyDiscountCommand;
import com.suresell.orders.application.dto.ApplyDiscountResult;
import com.suresell.orders.application.dto.LinkOrderCouponCommand;
import com.suresell.orders.application.dto.OrderItemDto;
import com.suresell.orders.application.dto.OrderItemRequestRecord;
import com.suresell.orders.application.dto.OrderItemResponseRecord;
import com.suresell.orders.application.dto.OrderRequestRecord;
import com.suresell.orders.application.dto.OrderResponseRecord;
import com.suresell.orders.application.dto.OrderSyncResponse;
import com.suresell.orders.application.usecase.DiscountHandler;
import com.suresell.orders.application.dto.ProductResponse;
import com.suresell.orders.infrastructure.persistence.OrderEditHistoryRepository;
import com.suresell.orders.infrastructure.client.adapter.ProductClientAdapter;
import com.suresell.orders.domain.port.in.OrderPort;
import com.suresell.orders.domain.port.out.OrderItemRepositoryPort;
import com.suresell.orders.domain.port.out.OrderRepositoryPort;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderHandler implements OrderPort {

    private final OrderRepositoryPort orderRepositoryPort;
    private final OrderItemRepositoryPort orderItemRepositoryPort;
    private final ProductClientAdapter productClient;
    private final DiscountHandler discountService;
    private final OrderEditHistoryRepository orderEditHistoryRepository;

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private static final int MAX_EDIT_MINUTES = 7;
    private static final String ADMIN_PASSWORD = "Admin2025*";

    @Override
    @Transactional
    public Order createOrUpdateOrder(OrderRequestRecord dto) {
        validatePaymentMethod(dto.paymentMethod());
        validatePagerAvailability(dto.pagerColor(), dto.pagerNumber(), null);

        Order order = new Order();
        order.setPagerColor(dto.pagerColor());
        order.setPagerNumber(dto.pagerNumber());
        order.setStatus(OrderStatus.pagado);
        order.setDeliveredAt("No");
        order.setPaymentMethod(dto.paymentMethod());
        order.setCreatedAt(LocalDateTime.now(BOGOTA_ZONE));

        List<OrderItem> items = createOrderItems(order, dto.items());
        order.setItems(items);

        int subtotal = order.getItems().stream().mapToInt(OrderItem::getTotalPrice).sum();
        order.setSubtotal(subtotal);

        int total = applyDiscountIfPresent(order, dto.discountCode(), subtotal);
        order.setTotal(total);

        Order savedOrder = orderRepositoryPort.save(order);

        linkCouponIfPresent(savedOrder);

        return savedOrder;
    }

    @Override
    public List<OrderResponseRecord> getKitchenOrders() {
        List<Order> orders = orderRepositoryPort.findActiveOrdersWithItems(OrderStatus.pagado);

        return orders.stream()
                .map(this::toOrderResponseRecord)
                .toList();
    }

    @Override
    public List<OrderResponseRecord> getAllOrders() {
        List<Order> orders = orderRepositoryPort.findAllWithItems();

        return orders.stream()
                .map(this::toOrderResponseRecord)
                .toList();
    }

    @Override
    public Page<OrderResponseRecord> getAllOrdersPaginated(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> ordersPage = orderRepositoryPort.findAllOrdersOnly(pageable);

        if (ordersPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> orderIds = ordersPage.getContent().stream()
                .map(Order::getIdOrder)
                .toList();

        List<OrderItem> items = orderItemRepositoryPort.findByOrderIds(orderIds);

        var itemsByOrderId = items.stream()
                .collect(Collectors.groupingBy(
                        oi -> oi.getOrder().getIdOrder()
                ));

        ordersPage.getContent().forEach(order -> {
            List<OrderItem> orderItems = itemsByOrderId.getOrDefault(
                    order.getIdOrder(),
                    new ArrayList<>()
            );
            order.setItems(orderItems);
        });

        return ordersPage.map(this::toOrderResponseRecord);
    }

    @Override
    public List<OrderResponseRecord> getAllOrdersKeyset(Long afterId, int size) {
        Pageable pageable = PageRequest.of(0, size);
        List<Order> orders = orderRepositoryPort.findOrdersAfter(afterId, pageable);

        if (orders.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> orderIds = orders.stream()
                .map(Order::getIdOrder)
                .toList();

        List<OrderItem> items = orderItemRepositoryPort.findByOrderIds(orderIds);

        var itemsByOrderId = items.stream()
                .collect(Collectors.groupingBy(
                        oi -> oi.getOrder().getIdOrder()
                ));

        orders.forEach(order -> {
            List<OrderItem> orderItems = itemsByOrderId.getOrDefault(
                    order.getIdOrder(),
                    new ArrayList<>()
            );
            order.setItems(orderItems);
        });

        return orders.stream()
                .map(this::toOrderResponseRecord)
                .toList();
    }

    @Override
    public OrderResponseRecord getOrderById(Long orderId) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada con ID: " + orderId));

        return toOrderResponseRecord(order);
    }

    @Override
    public void updateStatus(Long orderId, String newStatus) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        OrderStatus status;
        try {
            status = OrderStatus.fromString(newStatus);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado inválido: " + newStatus + ". Estado permitido: PAGADO");
        }

        order.setStatus(status);
        orderRepositoryPort.save(order);
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
                    String.format("No se puede editar la orden #%d. Han pasado %d minutos desde su creación (máximo permitido: %d minutos)",
                            orderId, minutesSinceCreation, MAX_EDIT_MINUTES),
                    "ORDER_EDIT_TIME_EXCEEDED");
        }

        if (!(order.getPagerColor().equals(dto.pagerColor()) && order.getPagerNumber().equals(dto.pagerNumber()))) {
            validatePagerAvailability(dto.pagerColor(), dto.pagerNumber(), orderId);
        }

        List<OrderItem> previousItems = new ArrayList<>(order.getItems());
        int previousTotal = order.getTotal();

        order.setPagerColor(dto.pagerColor());
        order.setPagerNumber(dto.pagerNumber());

        order.getItems().clear();
        List<OrderItem> newItems = createOrderItems(order, dto.items());
        order.getItems().addAll(newItems);

        int subtotal = order.getItems().stream().mapToInt(OrderItem::getTotalPrice).sum();
        order.setSubtotal(subtotal);

        int total = applyDiscountIfPresent(order, dto.discountCode(), subtotal);
        order.setTotal(total);

        orderRepositoryPort.save(order);

        saveEditHistory(orderId, previousItems, order.getItems(), previousTotal, order.getTotal());
    }

    private void saveEditHistory(Long orderId, List<OrderItem> oldItems, List<OrderItem> newItems,
                                 int oldTotal, int newTotal) {
        LocalDateTime now = LocalDateTime.now(BOGOTA_ZONE);

        for (OrderItem oldItem : oldItems) {
            boolean found = newItems.stream()
                    .anyMatch(n -> n.getProductId().equals(oldItem.getProductId()));

            if (!found) {
                OrderEditHistory history = new OrderEditHistory();
                history.setOrderId(orderId);
                history.setEditType("ITEM_REMOVED");
                history.setProductId(oldItem.getProductId());
                history.setProductName(productClient.getProductById(oldItem.getProductId()).nameProduct());
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
                history.setProductName(productClient.getProductById(newItem.getProductId()).nameProduct());
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
                history.setProductName(productClient.getProductById(newItem.getProductId()).nameProduct());
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
    public List<OrderResponseRecord> getSalesReport() {
        List<Order> orders = orderRepositoryPort.findAll();
        List<OrderResponseRecord> report = new ArrayList<>();
        for (Order order : orders) {
            report.add(toOrderResponseRecord(order));
        }
        return report;
    }

    @Override
    @Transactional
    public void updatePaymentMethod(Long orderId, String paymentMethod) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada con ID: " + orderId));

        if (!List.of("CASH", "CARD", "NEQUI", "QR").contains(paymentMethod)) {
            throw new IllegalArgumentException("Método de pago inválido. Debe ser: CASH, CARD, NEQUI o QR");
        }

        order.setPaymentMethod(paymentMethod);
        orderRepositoryPort.save(order);
    }

    @Override
    @Transactional
    public OrderResponseRecord applyDiscountToOrder(Long orderId, String discountCode) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada con ID: " + orderId));

        if (order.getDiscountCode() != null) {
            throw new IllegalStateException("La orden ya tiene un descuento aplicado: " + order.getDiscountCode());
        }

        int subtotal = order.getSubtotal();

        List<OrderItemDto> itemsForDiscount = order.getItems().stream().map(item -> {
            ProductResponse productDetails = productClient.getProductById(item.getProductId());
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
                    BigDecimal.valueOf(item.getUnitPrice()));
        }).toList();

        ApplyDiscountCommand command = new ApplyDiscountCommand(
                discountCode,
                LocalDateTime.now(BOGOTA_ZONE),
                itemsForDiscount,
                BigDecimal.valueOf(subtotal));

        ApplyDiscountResult discountResult = discountService.applyDiscount(command);

        if (!discountResult.valid()) {
            throw new IllegalArgumentException("Cupón inválido: " + discountResult.message());
        }

        order.setDiscountCode(discountResult.discountCode());
        order.setDiscountAmount(discountResult.discountAmount().intValue());
        if (discountResult.discountPercentage() != null) {
            order.setDiscountPercentage(discountResult.discountPercentage().doubleValue());
        }

        int total = discountResult.newSubtotal().intValue();
        order.setTotal(total);

        Order savedOrder = orderRepositoryPort.save(order);

        LinkOrderCouponCommand linkCommand = new LinkOrderCouponCommand(
                savedOrder.getIdOrder(),
                savedOrder.getDiscountCode(),
                BigDecimal.valueOf(savedOrder.getSubtotal()),
                BigDecimal.valueOf(savedOrder.getDiscountAmount()),
                BigDecimal.valueOf(savedOrder.getTotal()));

        discountService.linkOrderWithCoupon(linkCommand);

        return toOrderResponseRecord(savedOrder);
    }

    @Override
    @Transactional
    public void markAsDelivered(Long orderId, Integer elapsedSeconds) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada con ID: " + orderId));

        if ("Si".equals(order.getDeliveredAt())) {
            throw new IllegalStateException("La orden ya fue marcada como entregada");
        }

        if (order.getStatus() != OrderStatus.pagado) {
            throw new IllegalStateException("Solo se pueden marcar como entregadas las órdenes en estado PAGADO. Estado actual: " + order.getStatus());
        }

        order.setDeliveredAt("Si");
        order.setElapsedSecondsToDeliver(elapsedSeconds);
        orderRepositoryPort.save(order);
    }

    @Override
    public Page<OrderEditHistory> getOrderEditHistory(Long orderId, String adminPassword, int page, int size) {
        if (!ADMIN_PASSWORD.equals(adminPassword)) {
            throw new AdminPasswordException("Contraseña de administrador incorrecta");
        }

        Pageable pageable = PageRequest.of(page, size);

        if (orderId != null) {
            return orderEditHistoryRepository.findByOrderIdOrderByEditedAtDesc(orderId, pageable);
        }
        return orderEditHistoryRepository.findAllByOrderByEditedAtDesc(pageable);
    }

    @Override
    @Transactional
    public OrderSyncResponse syncOrderIdempotent(String idempotencyKey, OrderRequestRecord dto) {
        Optional<Order> existingOrder = orderRepositoryPort.findByIdempotencyKey(idempotencyKey);

        if (existingOrder.isPresent()) {
            Order order = existingOrder.get();
            log.info("Order with idempotencyKey {} already exists: Order ID {}",
                    idempotencyKey, order.getIdOrder());
            return OrderSyncResponse.alreadyExists(order.getIdOrder());
        }

        try {
            validatePaymentMethod(dto.paymentMethod());
            validatePagerAvailability(dto.pagerColor(), dto.pagerNumber(), null);

            Order order = new Order();
            order.setIdempotencyKey(idempotencyKey);
            order.setPagerColor(dto.pagerColor());
            order.setPagerNumber(dto.pagerNumber());
            order.setStatus(OrderStatus.pagado);
            order.setDeliveredAt("No");
            order.setPaymentMethod(dto.paymentMethod());
            order.setCreatedAt(LocalDateTime.now(BOGOTA_ZONE));

            List<OrderItem> items = createOrderItems(order, dto.items());
            order.setItems(items);

            int subtotal = order.getItems().stream().mapToInt(OrderItem::getTotalPrice).sum();
            order.setSubtotal(subtotal);

            int total = applyDiscountIfPresent(order, dto.discountCode(), subtotal);
            order.setTotal(total);

            Order savedOrder = orderRepositoryPort.save(order);

            linkCouponIfPresent(savedOrder);

            log.info("Order created with idempotencyKey {}: Order ID {}",
                    idempotencyKey, savedOrder.getIdOrder());

            return OrderSyncResponse.created(savedOrder.getIdOrder());

        } catch (IllegalArgumentException | PagerOcupadoException e) {
            log.warn("Validation error creating order with idempotencyKey {}: {}", idempotencyKey, e.getMessage());
            return OrderSyncResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error creating order with idempotencyKey {}: {}", idempotencyKey, e.getMessage(), e);
            return OrderSyncResponse.error("Error al crear orden: " + e.getMessage());
        }
    }

    @Override
    public Order findByIdempotencyKey(String idempotencyKey) {
        return orderRepositoryPort.findByIdempotencyKey(idempotencyKey)
                .orElse(null);
    }

    private void validatePaymentMethod(String paymentMethod) {
        if (paymentMethod == null || !List.of("CASH", "CARD", "NEQUI", "QR").contains(paymentMethod)) {
            throw new IllegalArgumentException("Método de pago inválido. Debe ser: CASH, CARD, NEQUI o QR");
        }
    }

    private void validatePagerAvailability(String pagerColor, String pagerNumber, Long excludeOrderId) {
        Optional<Order> existingPagerOrder = orderRepositoryPort.findByPagerColorAndPagerNumberAndStatusAndDeliveredAt(
                pagerColor, pagerNumber, OrderStatus.pagado, "No");

        if (existingPagerOrder.isPresent() &&
            (excludeOrderId == null || !existingPagerOrder.get().getIdOrder().equals(excludeOrderId))) {
            throw new PagerOcupadoException(
                    String.format("El pager %s %s ya está en uso por la orden #%d",
                            pagerColor, pagerNumber, existingPagerOrder.get().getIdOrder()),
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
            item.setTotalPrice(itemDto.quantity() * itemDto.unitPrice());
            item.setInstructions(itemDto.instructions());
            item.setComboGroup(itemDto.comboGroup());
            return item;
        }).toList();
    }

    private int applyDiscountIfPresent(Order order, String discountCode, int subtotal) {
        if (discountCode == null || discountCode.trim().isEmpty()) {
            return subtotal;
        }

        List<OrderItemDto> itemsForDiscount = order.getItems().stream().map(item -> {
            ProductResponse productDetails = productClient.getProductById(item.getProductId());
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
                    BigDecimal.valueOf(item.getUnitPrice()));
        }).toList();

        ApplyDiscountCommand command = new ApplyDiscountCommand(
                discountCode,
                LocalDateTime.now(BOGOTA_ZONE),
                itemsForDiscount,
                BigDecimal.valueOf(subtotal));

        ApplyDiscountResult discountResult = discountService.applyDiscount(command);

        if (discountResult.valid()) {
            order.setDiscountCode(discountResult.discountCode());
            order.setDiscountAmount(discountResult.discountAmount().intValue());
            if (discountResult.discountPercentage() != null) {
                order.setDiscountPercentage(discountResult.discountPercentage().doubleValue());
            }
            return discountResult.newSubtotal().intValue();
        }

        return subtotal;
    }

    private void linkCouponIfPresent(Order order) {
        if (order.getDiscountCode() != null) {
            LinkOrderCouponCommand linkCommand = new LinkOrderCouponCommand(
                    order.getIdOrder(),
                    order.getDiscountCode(),
                    BigDecimal.valueOf(order.getSubtotal()),
                    BigDecimal.valueOf(order.getDiscountAmount()),
                    BigDecimal.valueOf(order.getTotal()));
            discountService.linkOrderWithCoupon(linkCommand);
        }
    }

    private OrderResponseRecord toOrderResponseRecord(Order order) {
        List<OrderItemResponseRecord> items = order.getItems().stream()
                .map(this::toOrderItemResponseRecord)
                .toList();

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
                order.getDeliveredAt(),
                order.getElapsedSecondsToDeliver(),
                items
        );
    }

    private OrderItemResponseRecord toOrderItemResponseRecord(OrderItem item) {
        String productName = productClient.getProductById(item.getProductId()).nameProduct();

        return new OrderItemResponseRecord(
                item.getProductId(),
                productName,
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice(),
                item.getInstructions(),
                item.getComboGroup()
        );
    }
}
