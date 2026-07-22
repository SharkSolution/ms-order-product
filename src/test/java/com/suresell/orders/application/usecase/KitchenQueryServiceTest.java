package com.suresell.orders.application.usecase;

import com.suresell.orders.application.dto.KitchenOrderDto;
import com.suresell.orders.application.dto.KitchenOrderDto.DeliverRequest;
import com.suresell.orders.application.dto.KitchenOrderDto.KitchenPageDto;
import com.suresell.orders.domain.model.MenuProduct;
import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderDeliveryTracking;
import com.suresell.orders.domain.model.OrderItem;
import com.suresell.orders.infrastructure.persistence.MenuProductRepository;
import com.suresell.orders.infrastructure.persistence.OrderDeliveryTrackingRepository;
import com.suresell.orders.domain.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/** Módulo cocina (F4 Inc.1): mapeo al contrato de la app + entrega. Puro, con mocks. */
class KitchenQueryServiceTest {

    private OrderDeliveryTrackingRepository trackingRepository;
    private MenuProductRepository menuProductRepository;
    private KitchenQueryService service;

    @BeforeEach
    void setUp() {
        trackingRepository = mock(OrderDeliveryTrackingRepository.class);
        menuProductRepository = mock(MenuProductRepository.class);
        service = new KitchenQueryService(trackingRepository, menuProductRepository);
    }

    private OrderDeliveryTracking tracking(UUID uuid, boolean delivered) {
        Order order = new Order();
        order.setUuidId(uuid);
        order.setIdOrder(301858L);
        order.setPagerColor("AMARILLO");
        order.setPagerNumber("7");
        order.setCreatedAt(LocalDateTime.of(2026, 7, 21, 12, 0));
        order.setSynced(true);
        order.setTotal(new BigDecimal("35000"));
        order.setStatus(OrderStatus.pagado);

        OrderItem item = new OrderItem();
        item.setProductId("prod-1");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("17500"));
        item.setInstructions("sin cebolla");
        item.setComboGroup(1);
        item.setOrder(order);
        order.setItems(List.of(item));

        OrderDeliveryTracking t = new OrderDeliveryTracking();
        t.setOrderIdUuid(uuid);
        t.setOrderId(301858L);
        t.setDelivered(delivered);
        t.setPagerReturned(false);
        t.setOrder(order);
        order.setDeliveryTracking(t);
        return t;
    }

    private MenuProduct product(String id, String name) {
        MenuProduct p = new MenuProduct();
        p.setIdProduct(id);
        p.setNameProduct(name);
        return p;
    }

    @Test
    void activasMapeaContratoDeLaApp() {
        UUID uuid = UUID.randomUUID();
        when(trackingRepository.findActiveKitchenOrders()).thenReturn(List.of(tracking(uuid, false)));
        when(menuProductRepository.findAllById(anyCollection()))
                .thenReturn(List.of(product("prod-1", "Hamburguesa Shark")));

        List<KitchenOrderDto> result = service.getActiveOrdersFifo();

        assertEquals(1, result.size());
        KitchenOrderDto dto = result.get(0);
        assertEquals(301858L, dto.orderId());
        assertEquals(uuid.toString(), dto.orderUuid());
        assertEquals("AMARILLO", dto.pagerColor());
        assertEquals("7", dto.pagerNumber());
        assertFalse(dto.tracking().delivered());
        assertEquals(1, dto.items().size());
        assertEquals("Hamburguesa Shark", dto.items().get(0).productName());
        assertEquals(2, dto.items().get(0).quantity());
        assertEquals("sin cebolla", dto.items().get(0).instructions());
        assertNull(dto.waiterId());
    }

    @Test
    void productoSinNombreCaeAlId() {
        when(trackingRepository.findActiveKitchenOrders())
                .thenReturn(List.of(tracking(UUID.randomUUID(), false)));
        when(menuProductRepository.findAllById(anyCollection())).thenReturn(List.of());

        List<KitchenOrderDto> result = service.getActiveOrdersFifo();

        assertEquals("prod-1", result.get(0).items().get(0).productName());
    }

    @Test
    void entregadasPaginaConFormaDePageDeSpring() {
        UUID uuid = UUID.randomUUID();
        when(trackingRepository.findDeliveredKitchenOrders(any()))
                .thenReturn(new PageImpl<>(List.of(tracking(uuid, true)), PageRequest.of(0, 10), 25));
        when(menuProductRepository.findAllById(anyCollection())).thenReturn(List.of());

        KitchenPageDto page = service.getDeliveredOrders(0, 10);

        assertEquals(1, page.content().size());
        assertEquals(25, page.totalElements());
        assertEquals(3, page.totalPages());
        assertEquals(10, page.size());
        assertEquals(0, page.number());
        assertFalse(page.last());
        assertTrue(page.content().get(0).tracking().delivered());
    }

    @Test
    void entregarMarcaTrackingYDeliveredAt() {
        UUID uuid = UUID.randomUUID();
        OrderDeliveryTracking t = tracking(uuid, false);
        when(trackingRepository.findById(uuid)).thenReturn(Optional.of(t));

        service.markDelivered(uuid, new DeliverRequest(340));

        assertTrue(t.getDelivered());
        assertEquals(340, t.getPreparationDurationSeconds());
        assertNotNull(t.getOrder().getDeliveredAt());
        verify(trackingRepository).save(t);
    }

    @Test
    void entregarOrdenInexistenteLanza() {
        UUID uuid = UUID.randomUUID();
        when(trackingRepository.findById(uuid)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.markDelivered(uuid, new DeliverRequest(10)));
    }
}
