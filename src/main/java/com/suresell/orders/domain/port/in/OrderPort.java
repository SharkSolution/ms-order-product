package com.suresell.orders.domain.port.in;
import com.suresell.orders.application.dto.PagerAvailabilityResponse;
import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderEditHistory;
import com.suresell.orders.application.dto.OrderRequestRecord;
import com.suresell.orders.application.dto.OrderResponseRecord;
import org.springframework.data.domain.Page;
import java.util.List;
public interface OrderPort {
    Order createOrUpdateOrder(OrderRequestRecord dto);
    List<OrderResponseRecord> getAllOrders();
    Page<OrderResponseRecord> getAllOrdersPaginated(String pagerColor, String pagerNumber, Long idOrder, int page, int size);
    List<OrderResponseRecord> getAllOrdersKeyset(Long afterId, int size);
    OrderResponseRecord getOrderById(Long orderId);
    void updateOrder(Long orderId, OrderRequestRecord dto);
    OrderResponseRecord applyDiscountToOrder(Long orderId, String discountCode);
    Page<OrderEditHistory> getOrderEditHistory(Long orderId, int page, int size);
    PagerAvailabilityResponse getPagerAvailability();
    void markAsDeliveredLocally(Long orderId);
    void releasePager(String color, String number);
}
