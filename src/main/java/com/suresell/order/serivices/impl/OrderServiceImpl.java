package com.suresell.order.serivices.impl;

import com.suresell.order.exception.AdminPasswordException;
import com.suresell.order.exception.OrderEditNotAllowedException;
import com.suresell.order.exception.PagerOcupadoException;
import com.suresell.order.mapper.OrderMapper;
import com.suresell.order.model.entity.Order;
import com.suresell.order.model.entity.OrderEditHistory;
import com.suresell.order.model.entity.OrderItem;
import com.suresell.order.model.enums.OrderStatus;
import com.suresell.order.model.record.ApplyDiscountCommand;
import com.suresell.order.model.record.ApplyDiscountResult;
import com.suresell.order.model.record.LinkOrderCouponCommand;
import com.suresell.order.model.record.OrderItemDto;
import com.suresell.order.model.record.OrderItemResponseRecord;
import com.suresell.order.model.record.OrderRequestRecord;
import com.suresell.order.model.record.OrderResponseRecord;
import com.suresell.order.model.record.ProductResponse;
import com.suresell.order.repository.OrderEditHistoryRepository;
import com.suresell.order.repository.OrderRepository;
import com.suresell.order.rest_client.ProductClient;
import com.suresell.order.serivices.DiscountService;
import com.suresell.order.serivices.OrderService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final OrderMapper orderMapper;
    private final DiscountService discountService;
    private final OrderEditHistoryRepository orderEditHistoryRepository;

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private static final int MAX_EDIT_MINUTES = 7;
    private static final String ADMIN_PASSWORD = "Admin2025*";

    @Override
    @Transactional
    public void createOrUpdateOrder(OrderRequestRecord dto) {
        if (!List.of("CASH", "CARD", "NEQUI", "QR").contains(dto.paymentMethod())) {
            throw new IllegalArgumentException("Método de pago inválido. Debe ser: CASH, CARD, NEQUI o QR");
        }

        Optional<Order> existingPagerOrder = orderRepository.findByPagerColorAndPagerNumberAndStatusAndDeliveredAt(
                dto.pagerColor(), dto.pagerNumber(), OrderStatus.pagado, "No");

        if (existingPagerOrder.isPresent()) {
            throw new PagerOcupadoException(
                    String.format("El pager %s %s ya está en uso por la orden #%d",
                            dto.pagerColor(), dto.pagerNumber(), existingPagerOrder.get().getIdOrder()),
                    "PAGER_OCUPADO");
        }

        Order order = new Order();
        order.setPagerColor(dto.pagerColor());
        order.setPagerNumber(dto.pagerNumber());
        order.setStatus(OrderStatus.pagado);
        order.setDeliveredAt("No");
        order.setPaymentMethod(dto.paymentMethod());
        order.setCreatedAt(LocalDateTime.now(BOGOTA_ZONE));

        List<OrderItem> items = dto.items().stream().map(itemDto -> {
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

        order.setItems(items);

        int subtotal = order.getItems().stream().mapToInt(OrderItem::getTotalPrice).sum();
        order.setSubtotal(subtotal);
        int total = subtotal;

        if (dto.discountCode() != null && !dto.discountCode().trim().isEmpty()) {
            List<OrderItemDto> itemsForDiscount = order.getItems().stream().map(item -> {
                ProductResponse productDetails = productClient.getProductDetails(item.getProductId());
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
                    dto.discountCode(),
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
                total = discountResult.newSubtotal().intValue();
            }
        }

        order.setTotal(total);
        Order savedOrder = orderRepository.save(order);

        if (savedOrder.getDiscountCode() != null) {
            LinkOrderCouponCommand linkCommand = new LinkOrderCouponCommand(
                    savedOrder.getIdOrder(),
                    savedOrder.getDiscountCode(),
                    BigDecimal.valueOf(savedOrder.getSubtotal()),
                    BigDecimal.valueOf(savedOrder.getDiscountAmount()),
                    BigDecimal.valueOf(savedOrder.getTotal()));
            discountService.linkOrderWithCoupon(linkCommand);
        }
    }

    @Override
    public List<OrderResponseRecord> getKitchenOrders() {
        return orderRepository.findActiveOrders(OrderStatus.pagado).stream()
                .map(order -> new OrderResponseRecord(
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
                        order.getItems().stream()
                                .map(item -> new OrderItemResponseRecord(
                                        item.getProductId(),
                                        productClient.getProductName(item.getProductId()),
                                        item.getQuantity(),
                                        item.getUnitPrice(),
                                        item.getTotalPrice(),
                                        item.getInstructions(),
                                        item.getComboGroup()))
                                .toList()))
                .toList();
    }

    @Override
    public List<OrderResponseRecord> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(orderMapper::toOrderResponse)
                .toList();
    }

    @Override
    public OrderResponseRecord getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada con ID: " + orderId));

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
                order.getItems().stream()
                        .map(item -> new OrderItemResponseRecord(
                                item.getProductId(),
                                productClient.getProductName(item.getProductId()),
                                item.getQuantity(),
                                item.getUnitPrice(),
                                item.getTotalPrice(),
                                item.getInstructions(),
                                item.getComboGroup()))
                        .toList());
    }

    @Override
    public void updateStatus(Long orderId, String newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        OrderStatus status;
        try {
            status = OrderStatus.fromString(newStatus);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado inválido: " + newStatus + ". Estado permitido: PAGADO");
        }

        order.setStatus(status);
        orderRepository.save(order);
    }

    @Override
    @Transactional
    public void updateOrder(Long orderId, OrderRequestRecord dto) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada con ID: " + orderId));

        // Validar tiempo de edición (máximo 7 minutos desde la creación)
        LocalDateTime now = LocalDateTime.now(BOGOTA_ZONE);
        long minutesSinceCreation = ChronoUnit.MINUTES.between(order.getCreatedAt(), now);

        if (minutesSinceCreation > MAX_EDIT_MINUTES) {
            throw new OrderEditNotAllowedException(
                    String.format("No se puede editar la orden #%d. Han pasado %d minutos desde su creación (máximo permitido: %d minutos)",
                            orderId, minutesSinceCreation, MAX_EDIT_MINUTES),
                    "ORDER_EDIT_TIME_EXCEEDED");
        }

        // Validar pager no ocupado por otra orden
        if (!(order.getPagerColor().equals(dto.pagerColor()) && order.getPagerNumber().equals(dto.pagerNumber()))) {
            Optional<Order> existingPagerOrder = orderRepository.findByPagerColorAndPagerNumberAndStatusAndDeliveredAt(
                    dto.pagerColor(), dto.pagerNumber(), OrderStatus.pagado, "No");

            if (existingPagerOrder.isPresent() && !existingPagerOrder.get().getIdOrder().equals(orderId)) {
                throw new PagerOcupadoException(
                        String.format("El pager %s %s ya está en uso", dto.pagerColor(), dto.pagerNumber()),
                        "PAGER_OCUPADO");
            }
        }

        // Guardar estado anterior para auditoría
        List<OrderItem> previousItems = new ArrayList<>(order.getItems());
        int previousTotal = order.getTotal();

        // Actualizar datos básicos
        order.setPagerColor(dto.pagerColor());
        order.setPagerNumber(dto.pagerNumber());

        // Actualizar items
        order.getItems().clear();
        List<OrderItem> newItems = dto.items().stream().map(itemDto -> {
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
        order.getItems().addAll(newItems);

        // Recalcular totales
        int subtotal = order.getItems().stream().mapToInt(OrderItem::getTotalPrice).sum();
        order.setSubtotal(subtotal);
        int total = subtotal;

        // Aplicar descuento si existe
        if (dto.discountCode() != null && !dto.discountCode().trim().isEmpty()) {
            List<OrderItemDto> itemsForDiscount = order.getItems().stream().map(item -> {
                ProductResponse productDetails = productClient.getProductDetails(item.getProductId());
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
                    dto.discountCode(),
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
                total = discountResult.newSubtotal().intValue();
            } else {
                order.setDiscountCode(null);
                order.setDiscountAmount(0);
                order.setDiscountPercentage(null);
            }
        } else {
            order.setDiscountCode(null);
            order.setDiscountAmount(0);
            order.setDiscountPercentage(null);
        }

        order.setTotal(total);
        orderRepository.save(order);

        // Registrar cambios en auditoría
        saveEditHistory(orderId, previousItems, order.getItems(), previousTotal, order.getTotal());
    }

    /**
     * Registra los cambios realizados en una orden para auditoría.
     */
    private void saveEditHistory(Long orderId, List<OrderItem> oldItems, List<OrderItem> newItems,
                                 int oldTotal, int newTotal) {
        LocalDateTime now = LocalDateTime.now(BOGOTA_ZONE);

        // Detectar items eliminados
        for (OrderItem oldItem : oldItems) {
            boolean found = newItems.stream()
                    .anyMatch(n -> n.getProductId().equals(oldItem.getProductId()));

            if (!found) {
                OrderEditHistory history = new OrderEditHistory();
                history.setOrderId(orderId);
                history.setEditType("ITEM_REMOVED");
                history.setProductId(oldItem.getProductId());
                history.setProductName(productClient.getProductName(oldItem.getProductId()));
                history.setOldQuantity(oldItem.getQuantity());
                history.setNewQuantity(0);
                history.setOldTotal(oldTotal);
                history.setNewTotal(newTotal);
                history.setEditedAt(now);
                orderEditHistoryRepository.save(history);
            }
        }

        // Detectar items agregados o cantidad cambiada
        for (OrderItem newItem : newItems) {
            OrderItem oldItem = oldItems.stream()
                    .filter(o -> o.getProductId().equals(newItem.getProductId()))
                    .findFirst()
                    .orElse(null);

            if (oldItem == null) {
                // Item fue agregado
                OrderEditHistory history = new OrderEditHistory();
                history.setOrderId(orderId);
                history.setEditType("ITEM_ADDED");
                history.setProductId(newItem.getProductId());
                history.setProductName(productClient.getProductName(newItem.getProductId()));
                history.setOldQuantity(0);
                history.setNewQuantity(newItem.getQuantity());
                history.setOldTotal(oldTotal);
                history.setNewTotal(newTotal);
                history.setEditedAt(now);
                orderEditHistoryRepository.save(history);
            } else if (oldItem.getQuantity() != newItem.getQuantity()) {
                // Cantidad cambió
                OrderEditHistory history = new OrderEditHistory();
                history.setOrderId(orderId);
                history.setEditType("ITEM_QUANTITY_CHANGED");
                history.setProductId(newItem.getProductId());
                history.setProductName(productClient.getProductName(newItem.getProductId()));
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
        List<Order> orders = orderRepository.findAll();
        List<OrderResponseRecord> report = new ArrayList<>();
        for (Order order : orders) {
            report.add(orderMapper.toOrderResponse(order));
        }
        return report;
    }

    @Override
    @Transactional
    public void updatePaymentMethod(Long orderId, String paymentMethod) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada con ID: " + orderId));

        if (!List.of("CASH", "CARD", "NEQUI", "QR").contains(paymentMethod)) {
            throw new IllegalArgumentException("Método de pago inválido. Debe ser: CASH, CARD, NEQUI o QR");
        }

        order.setPaymentMethod(paymentMethod);
        orderRepository.save(order);
    }

    @Override
    @Transactional
    public OrderResponseRecord applyDiscountToOrder(Long orderId, String discountCode) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada con ID: " + orderId));

        if (order.getDiscountCode() != null) {
            throw new IllegalStateException("La orden ya tiene un descuento aplicado: " + order.getDiscountCode());
        }

        int subtotal = order.getSubtotal();

        List<OrderItemDto> itemsForDiscount = order.getItems().stream().map(item -> {
            ProductResponse productDetails = productClient.getProductDetails(item.getProductId());
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

        Order savedOrder = orderRepository.save(order);

        LinkOrderCouponCommand linkCommand = new LinkOrderCouponCommand(
                savedOrder.getIdOrder(),
                savedOrder.getDiscountCode(),
                BigDecimal.valueOf(savedOrder.getSubtotal()),
                BigDecimal.valueOf(savedOrder.getDiscountAmount()),
                BigDecimal.valueOf(savedOrder.getTotal()));

        discountService.linkOrderWithCoupon(linkCommand);

        return new OrderResponseRecord(
                savedOrder.getIdOrder(),
                savedOrder.getPagerColor(),
                savedOrder.getPagerNumber(),
                savedOrder.getCreatedAt(),
                savedOrder.getSubtotal(),
                savedOrder.getTotal(),
                savedOrder.getStatus().getDisplayName(),
                savedOrder.getPaymentMethod(),
                savedOrder.getDiscountCode(),
                savedOrder.getDiscountPercentage(),
                savedOrder.getDiscountAmount(),
                savedOrder.getDeliveredAt(),
                savedOrder.getElapsedSecondsToDeliver(),
                savedOrder.getItems().stream()
                        .map(item -> new OrderItemResponseRecord(
                                item.getProductId(),
                                productClient.getProductName(item.getProductId()),
                                item.getQuantity(),
                                item.getUnitPrice(),
                                item.getTotalPrice(),
                                item.getInstructions(),
                                item.getComboGroup()))
                        .toList());
    }

    @Override
    @Transactional
    public void markAsDelivered(Long orderId, Integer elapsedSeconds) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada con ID: " + orderId));

        if ("Si".equals(order.getDeliveredAt())) {
            throw new IllegalStateException("La orden ya fue marcada como entregada");
        }

        if (order.getStatus() != OrderStatus.pagado) {
            throw new IllegalStateException("Solo se pueden marcar como entregadas las órdenes en estado PAGADO. Estado actual: " + order.getStatus());
        }

        order.setDeliveredAt("Si");
        order.setElapsedSecondsToDeliver(elapsedSeconds);
        orderRepository.save(order);
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
}
