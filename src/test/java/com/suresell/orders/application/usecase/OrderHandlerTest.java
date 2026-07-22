package com.suresell.orders.application.usecase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.suresell.orders.application.dto.OrderItemRequestRecord;
import com.suresell.orders.application.dto.OrderRequestRecord;
import com.suresell.orders.application.dto.OrderResponseRecord;
import com.suresell.orders.application.dto.ProductResponse;
import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderDeliveryTracking;
import com.suresell.orders.domain.model.SyncOutbox;
import com.suresell.orders.domain.model.OrderItem;
import com.suresell.orders.domain.model.OrderStatus;
import com.suresell.orders.domain.port.in.DiscountPort;
import com.suresell.orders.domain.port.out.OrderDeliveryTrackingRepositoryPort;
import com.suresell.orders.domain.port.out.OrderEditHistoryRepositoryPort;
import com.suresell.orders.domain.port.out.OrderItemRepositoryPort;
import com.suresell.orders.domain.port.out.OrderRepositoryPort;
import com.suresell.orders.domain.port.out.SyncOutboxRepositoryPort;
import com.suresell.orders.domain.port.out.ProductCatalogPort;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
@ExtendWith(MockitoExtension.class)
class OrderHandlerTest {
    @Mock
    private OrderRepositoryPort orderRepositoryPort;
    @Mock
    private OrderDeliveryTrackingRepositoryPort orderDeliveryTrackingRepositoryPort;
    @Mock
    private SyncOutboxRepositoryPort syncOutboxRepositoryPort;
    @Mock
    private OrderItemRepositoryPort orderItemRepositoryPort;
    @Mock
    private ProductCatalogPort productCatalogPort;
    @Mock
    private DiscountPort discountPort;
    @Mock
    private OrderEditHistoryRepositoryPort orderEditHistoryRepositoryPort;
    private ObjectMapper objectMapper;
    private OrderHandler orderHandler;
    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        orderHandler = new OrderHandler(
                orderRepositoryPort,
                orderDeliveryTrackingRepositoryPort,
                syncOutboxRepositoryPort,
                orderItemRepositoryPort,
                productCatalogPort,
                discountPort,
                orderEditHistoryRepositoryPort,
                objectMapper,
                org.mockito.Mockito.mock(com.suresell.orders.infrastructure.persistence.WaiterRepository.class),
                org.mockito.Mockito.mock(com.suresell.orders.infrastructure.persistence.OrderPaymentRepository.class));
    }
    @Test
    void getAllOrdersCallsProductServiceOncePerDistinctProductId() {
        Order order = new Order();
        order.setIdOrder(99L);
        order.setPagerColor("AZUL");
        order.setPagerNumber("3");
        order.setStatus(OrderStatus.pagado);
        order.setPaymentMethod("CASH");
        order.setCreatedAt(LocalDateTime.now());
        order.setSubtotal(BigDecimal.valueOf(10000));
        order.setTotal(BigDecimal.valueOf(10000));
        OrderItem item1 = new OrderItem();
        item1.setOrder(order);
        item1.setProductId("101");
        item1.setQuantity(1);
        item1.setUnitPrice(BigDecimal.valueOf(5000));
        item1.setTotalPrice(BigDecimal.valueOf(5000));
        OrderItem item2 = new OrderItem();
        item2.setOrder(order);
        item2.setProductId("101");
        item2.setQuantity(1);
        item2.setUnitPrice(BigDecimal.valueOf(5000));
        item2.setTotalPrice(BigDecimal.valueOf(5000));
        order.setItems(List.of(item1, item2));
        when(orderRepositoryPort.findAllWithItems()).thenReturn(List.of(order));
        Map<String, ProductResponse> products = new LinkedHashMap<>();
        products.put("101", new ProductResponse("101", "Pizza", "Food"));
        when(productCatalogPort.findProductsByIds(any())).thenReturn(products);
        List<OrderResponseRecord> response = orderHandler.getAllOrders();
        assertEquals(1, response.size());
        assertEquals("Pizza", response.get(0).items().get(0).nameProduct());
        assertEquals("Pizza", response.get(0).items().get(1).nameProduct());
        verify(productCatalogPort, times(1)).findProductsByIds(any());
    }
    @Test
    void createOrUpdateOrderSavesOrderWithCalculatedSubtotalAndTotal() {
        OrderRequestRecord request = new OrderRequestRecord(
                "AZUL",
                "10",
                List.of(
                        new OrderItemRequestRecord("101", 1, BigDecimal.valueOf(5000), null, null),
                        new OrderItemRequestRecord("102", 2, BigDecimal.valueOf(2000), null, null)),
                null,
                "CASH", null);
        when(orderRepositoryPort.findOccupiedPagerOrder(
                "AZUL", "10", OrderStatus.pagado)).thenReturn(Optional.empty());
        when(orderRepositoryPort.save(any(Order.class))).thenAnswer(invocation -> {
            Order toSave = invocation.getArgument(0);
            toSave.setIdOrder(501L);
            return toSave;
        });
        when(orderRepositoryPort.findNumericIdByUuid(any(java.util.UUID.class)))
                .thenReturn(Optional.of(501L));
        when(syncOutboxRepositoryPort.save(any(SyncOutbox.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Order created = orderHandler.createOrUpdateOrder(request);
        assertEquals(BigDecimal.valueOf(9000), created.getSubtotal());
        assertEquals(BigDecimal.valueOf(9000), created.getTotal());
        assertEquals(2, created.getItems().size());
        verify(orderRepositoryPort, times(1)).save(any(Order.class));
        verify(syncOutboxRepositoryPort, times(1)).save(any(SyncOutbox.class));
    }
}
