package com.suresell.order.serivices.impl;

import com.suresell.order.exception.MesaDuplicadaException;
import com.suresell.order.mapper.OrderMapper;
import com.suresell.order.model.record.OrderItemRequestRecord;
import com.suresell.order.model.record.OrderItemResponseRecord;
import com.suresell.order.model.record.OrderRequestRecord;
import com.suresell.order.model.entity.Order;
import com.suresell.order.model.entity.OrderItem;
import com.suresell.order.model.record.OrderResponseRecord;
import com.suresell.order.repository.OrderItemRepository;
import com.suresell.order.repository.OrderRepository;
import com.suresell.order.rest_client.ProductClient;
import com.suresell.order.serivices.OrderService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

  private final OrderRepository orderRepository;
  private final ProductClient productClient;
  private final OrderMapper orderMapper;

  @Override
  @Transactional
  public void createOrUpdateOrder(OrderRequestRecord dto) {
    Optional<Order> existingOrderOpt =
        orderRepository.findByTableNumberAndStatus(dto.tableNumber(), "Pendiente");

    if (existingOrderOpt.isPresent()) {
      throw new MesaDuplicadaException(
        "Ya existe una orden pendiente para la mesa " + dto.tableNumber(),
        "MESA_DUPLICADA"
      );
    }

    Order order;
    if (existingOrderOpt.isPresent()) {
      order = existingOrderOpt.get();

      for (OrderItemRequestRecord itemDto : dto.items()) {
        Optional<OrderItem> existingItem =
            order.getItems().stream()
                .filter(i -> i.getProductId().equals(itemDto.productId()))
                .findFirst();

        if (existingItem.isPresent()) {
          OrderItem item = existingItem.get();
          item.setQuantity(item.getQuantity() + itemDto.quantity());
          item.setUnitPrice(itemDto.unitPrice());
          item.setTotalPrice(item.getQuantity() * item.getUnitPrice());
        } else {
          OrderItem newItem = new OrderItem();
          newItem.setOrder(order);
          newItem.setProductId(itemDto.productId());
          newItem.setQuantity(itemDto.quantity());
          newItem.setUnitPrice(itemDto.unitPrice());
          newItem.setTotalPrice(itemDto.quantity() * itemDto.unitPrice());
          newItem.setInstructions(itemDto.instructions());

          order.getItems().add(newItem);
        }
      }

    } else {
      order = new Order();
      order.setTableNumber(dto.tableNumber());
      order.setStatus("Pendiente");
      order.setCreatedAt(LocalDateTime.now());

      List<OrderItem> items =
          dto.items().stream()
              .map(
                  itemDto -> {
                    OrderItem item = new OrderItem();
                    item.setOrder(order);
                    item.setProductId(itemDto.productId());
                    item.setQuantity(itemDto.quantity());
                    item.setUnitPrice(itemDto.unitPrice());
                    item.setTotalPrice(itemDto.quantity() * itemDto.unitPrice());
                    item.setInstructions(itemDto.instructions());
                    return item;
                  })
              .toList();

      order.setItems(items);
    }

    int subtotal = order.getItems().stream().mapToInt(OrderItem::getTotalPrice).sum();
    int tax = (int) (subtotal * 0.10);
    order.setSubtotal(subtotal);
    order.setTax(tax);
    order.setTotal(subtotal + tax);

    orderRepository.save(order);
  }

  @Override
  public List<OrderResponseRecord> getKitchenOrders() {
    return orderRepository.findByStatus("Pendiente").stream()
        .map(
            order ->
                new OrderResponseRecord(
                    order.getIdOrder(),
                    order.getTableNumber(),
                    order.getCreatedAt(),
                    order.getSubtotal(),
                    order.getTax(),
                    order.getTotal(),
                    order.getStatus(),
                    order.getItems().stream()
                        .map(
                            item ->
                                new OrderItemResponseRecord(
                                    item.getProductId(),
                                    productClient.getProductName(item.getProductId()),
                                    item.getQuantity(),
                                    item.getUnitPrice(),
                                    item.getTotalPrice(),
                                    item.getInstructions()))
                        .toList()))
        .toList();
  }

  @Override
  public List<OrderResponseRecord> getAllOrders() {
    return orderRepository.findAll().stream().map(orderMapper::toOrderResponse).toList();
  }

  @Override
  public OrderResponseRecord getOrderById(Long orderId) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new RuntimeException("Orden no encontrada con ID: " + orderId));

    return new OrderResponseRecord(
        order.getIdOrder(),
        order.getTableNumber(),
        order.getCreatedAt(),
        order.getSubtotal(),
        order.getTax(),
        order.getTotal(),
        order.getStatus(),
        order.getItems().stream()
            .map(
                item ->
                    new OrderItemResponseRecord(
                        item.getProductId(),
                        productClient.getProductName(item.getProductId()),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getTotalPrice(),
                        item.getInstructions()))
            .toList());
  }

  @Override
  public void updateStatus(Long orderId, String newStatus) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new RuntimeException("Order not found"));
    order.setStatus(newStatus);
    orderRepository.save(order);
  }

  @Override
  @Transactional
  public void updateOrder(Long orderId, OrderRequestRecord dto) {
    Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Orden no encontrada con ID: " + orderId));

    order.setTableNumber(dto.tableNumber());

    order.getItems().clear();

    List<OrderItem> newItems = dto.items().stream()
            .map(itemDto -> {
              OrderItem item = new OrderItem();
              item.setOrder(order);
              item.setProductId(itemDto.productId());
              item.setQuantity(itemDto.quantity());
              item.setUnitPrice(itemDto.unitPrice());
              item.setTotalPrice(itemDto.quantity() * itemDto.unitPrice());
              item.setInstructions(itemDto.instructions());
              return item;
            })
            .toList();

    order.getItems().addAll(newItems);

    int subtotal = order.getItems().stream().mapToInt(OrderItem::getTotalPrice).sum();
    int tax = (int) (subtotal * 0.10);
    order.setSubtotal(subtotal);
    order.setTax(tax);
    order.setTotal(subtotal + tax);

    orderRepository.save(order);
  }
}
