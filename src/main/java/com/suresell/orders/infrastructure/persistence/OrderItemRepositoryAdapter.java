package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderItem;
import com.suresell.orders.domain.port.out.OrderItemRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
@Component
@RequiredArgsConstructor
public class OrderItemRepositoryAdapter implements OrderItemRepositoryPort {
    private final OrderItemRepository orderItemRepository;
    @Override
    public OrderItem save(OrderItem orderItem) {
        return orderItemRepository.save(orderItem);
    }
    @Override
    public void deleteByOrder(Order order) {
        orderItemRepository.deleteByOrder(order);
    }
    @Override
    public List<OrderItem> findByOrderIds(List<Long> orderIds) {
        return orderItemRepository.findByOrderIds(orderIds);
    }
}
