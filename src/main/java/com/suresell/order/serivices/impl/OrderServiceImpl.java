/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.exception.PagerOcupadoException
 *  com.suresell.order.mapper.OrderMapper
 *  com.suresell.order.model.entity.Order
 *  com.suresell.order.model.entity.OrderItem
 *  com.suresell.order.model.enums.OrderStatus
 *  com.suresell.order.model.record.ApplyDiscountCommand
 *  com.suresell.order.model.record.ApplyDiscountResult
 *  com.suresell.order.model.record.LinkOrderCouponCommand
 *  com.suresell.order.model.record.OrderItemDto
 *  com.suresell.order.model.record.OrderItemResponseRecord
 *  com.suresell.order.model.record.OrderRequestRecord
 *  com.suresell.order.model.record.OrderResponseRecord
 *  com.suresell.order.model.record.ProductResponse
 *  com.suresell.order.repository.OrderRepository
 *  com.suresell.order.rest_client.ProductClient
 *  com.suresell.order.serivices.DiscountService
 *  com.suresell.order.serivices.OrderService
 *  com.suresell.order.serivices.impl.OrderServiceImpl
 *  jakarta.transaction.Transactional
 *  lombok.Generated
 *  org.springframework.stereotype.Service
 */
package com.suresell.order.serivices.impl;

import com.suresell.order.exception.PagerOcupadoException;
import com.suresell.order.mapper.OrderMapper;
import com.suresell.order.model.entity.Order;
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
import com.suresell.order.repository.OrderRepository;
import com.suresell.order.rest_client.ProductClient;
import com.suresell.order.serivices.DiscountService;
import com.suresell.order.serivices.OrderService;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Generated;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl
implements OrderService {
    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final OrderMapper orderMapper;
    private final DiscountService discountService;

    @Transactional
    public void createOrUpdateOrder(OrderRequestRecord dto) {
        if (!List.of("CASH", "CARD", "NEQUI", "QR").contains(dto.paymentMethod())) {
            throw new IllegalArgumentException("M\u00e9todo de pago inv\u00e1lido. Debe ser: CASH, CARD, NEQUI o QR");
        }
        Optional existingPagerOrder = this.orderRepository.findByPagerColorAndPagerNumberAndStatusAndDeliveredAt(dto.pagerColor(), dto.pagerNumber(), OrderStatus.PAGADO, "No");
        if (existingPagerOrder.isPresent()) {
            throw new PagerOcupadoException(String.format("El pager %s #%d ya est\u00e1 en uso por la orden #%d", dto.pagerColor(), dto.pagerNumber(), ((Order)existingPagerOrder.get()).getIdOrder()), "PAGER_OCUPADO");
        }
        Order order = new Order();
        order.setPagerColor(dto.pagerColor());
        order.setPagerNumber(dto.pagerNumber());
        order.setStatus(OrderStatus.PAGADO);
        order.setDeliveredAt("No");
        order.setPaymentMethod(dto.paymentMethod());
        order.setCreatedAt(LocalDateTime.now());
        List<OrderItem> items = dto.items().stream().map(itemDto -> {
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(itemDto.productId());
            item.setQuantity(itemDto.quantity());
            item.setUnitPrice(itemDto.unitPrice());
            item.setTotalPrice(itemDto.quantity() * itemDto.unitPrice());
            item.setInstructions(itemDto.instructions());
            return item;
        }).toList();
        order.setItems(items);
        int subtotal = order.getItems().stream().mapToInt(OrderItem::getTotalPrice).sum();
        order.setSubtotal(subtotal);
        int total = subtotal;
        if (dto.discountCode() != null && !dto.discountCode().trim().isEmpty()) {
            List<OrderItemDto> itemsForDiscount = order.getItems().stream().map(item -> {
                ProductResponse productDetails = this.productClient.getProductDetails(item.getProductId());
                Long productIdLong = null;
                try {
                    productIdLong = Long.parseLong(item.getProductId());
                }
                catch (NumberFormatException numberFormatException) {
                    // empty catch block
                }
                return new OrderItemDto(item.getProductId(), productIdLong, productDetails != null ? productDetails.nameProduct() : null, productDetails != null ? productDetails.categoryName() : null, Integer.valueOf(item.getQuantity()), BigDecimal.valueOf(item.getUnitPrice()));
            }).toList();
            ApplyDiscountCommand command = new ApplyDiscountCommand(dto.discountCode(), LocalDateTime.now(), itemsForDiscount, BigDecimal.valueOf(subtotal));
            ApplyDiscountResult discountResult = this.discountService.applyDiscount(command);
            if (discountResult.valid().booleanValue()) {
                order.setDiscountCode(discountResult.discountCode());
                order.setDiscountAmount(Integer.valueOf(discountResult.discountAmount().intValue()));
                if (discountResult.discountPercentage() != null) {
                    order.setDiscountPercentage(Double.valueOf(discountResult.discountPercentage().doubleValue()));
                }
                total = discountResult.newSubtotal().intValue();
            }
        }
        order.setTotal(total);
        Order savedOrder = (Order)this.orderRepository.save((Object)order);
        if (savedOrder.getDiscountCode() != null) {
            LinkOrderCouponCommand linkCommand = new LinkOrderCouponCommand(savedOrder.getIdOrder(), savedOrder.getDiscountCode(), BigDecimal.valueOf(savedOrder.getSubtotal()), BigDecimal.valueOf(savedOrder.getDiscountAmount().intValue()), BigDecimal.valueOf(savedOrder.getTotal()));
            this.discountService.linkOrderWithCoupon(linkCommand);
        }
    }

    public List<OrderResponseRecord> getKitchenOrders() {
        return this.orderRepository.findActiveOrders().stream().map(order -> new OrderResponseRecord(order.getIdOrder(), order.getPagerColor(), order.getPagerNumber(), order.getCreatedAt(), order.getSubtotal(), order.getTotal(), order.getStatus().getDisplayName(), order.getPaymentMethod(), order.getDiscountCode(), order.getDiscountPercentage(), order.getDiscountAmount(), order.getDeliveredAt(), order.getItems().stream().map(item -> new OrderItemResponseRecord(item.getProductId(), this.productClient.getProductName(item.getProductId()), item.getQuantity(), item.getUnitPrice(), item.getTotalPrice(), item.getInstructions())).toList())).toList();
    }

    public List<OrderResponseRecord> getAllOrders() {
        return this.orderRepository.findAll().stream().map(arg_0 -> ((OrderMapper)this.orderMapper).toOrderResponse(arg_0)).toList();
    }

    public OrderResponseRecord getOrderById(Long orderId) {
        Order order = (Order)this.orderRepository.findById((Object)orderId).orElseThrow(() -> new RuntimeException("Orden no encontrada con ID: " + orderId));
        return new OrderResponseRecord(order.getIdOrder(), order.getPagerColor(), order.getPagerNumber(), order.getCreatedAt(), order.getSubtotal(), order.getTotal(), order.getStatus().getDisplayName(), order.getPaymentMethod(), order.getDiscountCode(), order.getDiscountPercentage(), order.getDiscountAmount(), order.getDeliveredAt(), order.getItems().stream().map(item -> new OrderItemResponseRecord(item.getProductId(), this.productClient.getProductName(item.getProductId()), item.getQuantity(), item.getUnitPrice(), item.getTotalPrice(), item.getInstructions())).toList());
    }

    public void updateStatus(Long orderId, String newStatus) {
        OrderStatus status;
        Order order = (Order)this.orderRepository.findById((Object)orderId).orElseThrow(() -> new RuntimeException("Order not found"));
        try {
            status = OrderStatus.fromString((String)newStatus);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado inv\u00e1lido: " + newStatus + ". Estado permitido: PAGADO");
        }
        order.setStatus(status);
        this.orderRepository.save((Object)order);
    }

    @Transactional
    public void updateOrder(Long orderId, OrderRequestRecord dto) {
        Optional existingPagerOrder;
        Order order = (Order)this.orderRepository.findById((Object)orderId).orElseThrow(() -> new RuntimeException("Orden no encontrada con ID: " + orderId));
        if (!(order.getPagerColor().equals((Object)dto.pagerColor()) && order.getPagerNumber().equals(dto.pagerNumber()) || !(existingPagerOrder = this.orderRepository.findByPagerColorAndPagerNumberAndStatusAndDeliveredAt(dto.pagerColor(), dto.pagerNumber(), OrderStatus.PAGADO, "No")).isPresent() || ((Order)existingPagerOrder.get()).getIdOrder().equals(orderId))) {
            throw new PagerOcupadoException(String.format("El pager %s #%d ya est\u00e1 en uso", dto.pagerColor(), dto.pagerNumber()), "PAGER_OCUPADO");
        }
        order.setPagerColor(dto.pagerColor());
        order.setPagerNumber(dto.pagerNumber());
        order.getItems().clear();
        List<OrderItem> newItems = dto.items().stream().map(itemDto -> {
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(itemDto.productId());
            item.setQuantity(itemDto.quantity());
            item.setUnitPrice(itemDto.unitPrice());
            item.setTotalPrice(itemDto.quantity() * itemDto.unitPrice());
            item.setInstructions(itemDto.instructions());
            return item;
        }).toList();
        order.getItems().addAll(newItems);
        int subtotal = order.getItems().stream().mapToInt(OrderItem::getTotalPrice).sum();
        order.setSubtotal(subtotal);
        int total = subtotal;
        if (dto.discountCode() != null && !dto.discountCode().trim().isEmpty()) {
            List<OrderItemDto> itemsForDiscount = order.getItems().stream().map(item -> {
                ProductResponse productDetails = this.productClient.getProductDetails(item.getProductId());
                Long productIdLong = null;
                try {
                    productIdLong = Long.parseLong(item.getProductId());
                }
                catch (NumberFormatException numberFormatException) {
                    // empty catch block
                }
                return new OrderItemDto(item.getProductId(), productIdLong, productDetails != null ? productDetails.nameProduct() : null, productDetails != null ? productDetails.categoryName() : null, Integer.valueOf(item.getQuantity()), BigDecimal.valueOf(item.getUnitPrice()));
            }).toList();
            ApplyDiscountCommand command = new ApplyDiscountCommand(dto.discountCode(), LocalDateTime.now(), itemsForDiscount, BigDecimal.valueOf(subtotal));
            ApplyDiscountResult discountResult = this.discountService.applyDiscount(command);
            if (discountResult.valid().booleanValue()) {
                order.setDiscountCode(discountResult.discountCode());
                order.setDiscountAmount(Integer.valueOf(discountResult.discountAmount().intValue()));
                if (discountResult.discountPercentage() != null) {
                    order.setDiscountPercentage(Double.valueOf(discountResult.discountPercentage().doubleValue()));
                }
                total = discountResult.newSubtotal().intValue();
            } else {
                order.setDiscountCode(null);
                order.setDiscountAmount(Integer.valueOf(0));
                order.setDiscountPercentage(null);
            }
        } else {
            order.setDiscountCode(null);
            order.setDiscountAmount(Integer.valueOf(0));
            order.setDiscountPercentage(null);
        }
        order.setTotal(total);
        this.orderRepository.save((Object)order);
    }

    public List<OrderResponseRecord> getSalesReport() {
        List orders = this.orderRepository.findAll();
        ArrayList<OrderResponseRecord> report = new ArrayList<OrderResponseRecord>();
        for (Order order : orders) {
            report.add(this.orderMapper.toOrderResponse(order));
        }
        return report;
    }

    @Transactional
    public void updatePaymentMethod(Long orderId, String paymentMethod) {
        Order order = (Order)this.orderRepository.findById((Object)orderId).orElseThrow(() -> new RuntimeException("Orden no encontrada con ID: " + orderId));
        if (!List.of("CASH", "CARD", "NEQUI", "QR").contains(paymentMethod)) {
            throw new IllegalArgumentException("M\u00e9todo de pago inv\u00e1lido. Debe ser: CASH, CARD, NEQUI o QR");
        }
        order.setPaymentMethod(paymentMethod);
        this.orderRepository.save((Object)order);
    }

    @Transactional
    public OrderResponseRecord applyDiscountToOrder(Long orderId, String discountCode) {
        Order order = (Order)this.orderRepository.findById((Object)orderId).orElseThrow(() -> new RuntimeException("Orden no encontrada con ID: " + orderId));
        if (order.getDiscountCode() != null) {
            throw new IllegalStateException("La orden ya tiene un descuento aplicado: " + order.getDiscountCode());
        }
        int subtotal = order.getSubtotal();
        List<OrderItemDto> itemsForDiscount = order.getItems().stream().map(item -> {
            ProductResponse productDetails = this.productClient.getProductDetails(item.getProductId());
            Long productIdLong = null;
            try {
                productIdLong = Long.parseLong(item.getProductId());
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
            return new OrderItemDto(item.getProductId(), productIdLong, productDetails != null ? productDetails.nameProduct() : null, productDetails != null ? productDetails.categoryName() : null, Integer.valueOf(item.getQuantity()), BigDecimal.valueOf(item.getUnitPrice()));
        }).toList();
        ApplyDiscountCommand command = new ApplyDiscountCommand(discountCode, LocalDateTime.now(), itemsForDiscount, BigDecimal.valueOf(subtotal));
        ApplyDiscountResult discountResult = this.discountService.applyDiscount(command);
        if (!discountResult.valid().booleanValue()) {
            throw new IllegalArgumentException("Cup\u00f3n inv\u00e1lido: " + discountResult.message());
        }
        order.setDiscountCode(discountResult.discountCode());
        order.setDiscountAmount(Integer.valueOf(discountResult.discountAmount().intValue()));
        if (discountResult.discountPercentage() != null) {
            order.setDiscountPercentage(Double.valueOf(discountResult.discountPercentage().doubleValue()));
        }
        int total = discountResult.newSubtotal().intValue();
        order.setTotal(total);
        Order savedOrder = (Order)this.orderRepository.save((Object)order);
        LinkOrderCouponCommand linkCommand = new LinkOrderCouponCommand(savedOrder.getIdOrder(), savedOrder.getDiscountCode(), BigDecimal.valueOf(savedOrder.getSubtotal()), BigDecimal.valueOf(savedOrder.getDiscountAmount().intValue()), BigDecimal.valueOf(savedOrder.getTotal()));
        this.discountService.linkOrderWithCoupon(linkCommand);
        return this.orderMapper.toOrderResponse(savedOrder);
    }

    @Transactional
    public void markAsDelivered(Long orderId) {
        Order order = (Order)this.orderRepository.findById((Object)orderId).orElseThrow(() -> new RuntimeException("Orden no encontrada con ID: " + orderId));
        if ("Si".equals(order.getDeliveredAt())) {
            throw new IllegalStateException("La orden ya fue marcada como entregada");
        }
        if (order.getStatus() != OrderStatus.PAGADO) {
            throw new IllegalStateException("Solo se pueden marcar como entregadas las \u00f3rdenes en estado PAGADO. Estado actual: " + String.valueOf(order.getStatus()));
        }
        order.setDeliveredAt("Si");
        this.orderRepository.save((Object)order);
    }

    @Generated
    public OrderServiceImpl(OrderRepository orderRepository, ProductClient productClient, OrderMapper orderMapper, DiscountService discountService) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.orderMapper = orderMapper;
        this.discountService = discountService;
    }
}

