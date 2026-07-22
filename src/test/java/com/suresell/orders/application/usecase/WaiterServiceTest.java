package com.suresell.orders.application.usecase;

import com.suresell.orders.application.dto.OrderItemRequestRecord;
import com.suresell.orders.application.dto.WaiterDtos.CloseShiftRequest;
import com.suresell.orders.application.dto.WaiterDtos.CreateWaiterRequest;
import com.suresell.orders.application.dto.WaiterDtos.OpenShiftRequest;
import com.suresell.orders.application.dto.WaiterDtos.ShiftSummaryResponse;
import com.suresell.orders.application.dto.WaiterDtos.WaiterOrderRequest;
import com.suresell.orders.application.dto.WaiterDtos.WaiterOrderResponse;
import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.Waiter;
import com.suresell.orders.domain.model.WaiterSession;
import com.suresell.orders.domain.port.in.OrderPort;
import com.suresell.orders.infrastructure.persistence.MenuCategoryRepository;
import com.suresell.orders.infrastructure.persistence.MenuProductRepository;
import com.suresell.orders.infrastructure.persistence.OrderRepository;
import com.suresell.orders.infrastructure.persistence.WaiterRepository;
import com.suresell.orders.infrastructure.persistence.WaiterSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Módulo meseros (F4 Inc.3): sesiones, turnos con matemática de caja, idempotencia. */
class WaiterServiceTest {

    private WaiterRepository waiterRepository;
    private WaiterSessionRepository sessionRepository;
    private OrderRepository orderRepository;
    private OrderPort orderPort;
    private WaiterService service;

    @BeforeEach
    void setUp() {
        waiterRepository = mock(WaiterRepository.class);
        sessionRepository = mock(WaiterSessionRepository.class);
        orderRepository = mock(OrderRepository.class);
        orderPort = mock(OrderPort.class);
        service = new WaiterService(waiterRepository, sessionRepository, orderRepository,
                mock(MenuCategoryRepository.class), mock(MenuProductRepository.class), orderPort);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(waiterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Waiter waiter(long id) {
        Waiter w = new Waiter();
        w.setId(id);
        w.setName("Angie");
        w.setActive(true);
        return w;
    }

    private WaiterSession activeSession(long waiterId, BigDecimal base) {
        WaiterSession s = new WaiterSession();
        s.setWaiterId(waiterId);
        s.setWaiterName("Angie");
        s.setStatus(WaiterSession.STATUS_ACTIVE);
        s.setLoginTime(LocalDateTime.now());
        s.setOpeningCashBase(base);
        return s;
    }

    private Order order(String method, String total) {
        Order o = new Order();
        o.setPaymentMethod(method);
        o.setTotal(new BigDecimal(total));
        return o;
    }

    @Test
    void crearMeseroExigeNombre() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createWaiter(new CreateWaiterRequest("  ", null)));
        Waiter creado = service.createWaiter(new CreateWaiterRequest("Pedro", null));
        assertEquals("Pedro", creado.getName());
        assertTrue(creado.getActive());
    }

    @Test
    void loginReutilizaSesionConTurnoAbierto() {
        WaiterSession conTurno = activeSession(3L, new BigDecimal("50000"));
        when(waiterRepository.findById(3L)).thenReturn(Optional.of(waiter(3)));
        when(sessionRepository.findFirstByWaiterIdAndStatusOrderByLoginTimeDesc(3L, "ACTIVE"))
                .thenReturn(Optional.of(conTurno));

        WaiterSession result = service.login(3L);

        assertSame(conTurno, result);
        verify(sessionRepository, never()).save(argThat(s -> s != conTurno));
    }

    @Test
    void loginCierraSesionSinTurnoYCreaNueva() {
        WaiterSession sinTurno = activeSession(3L, null);
        when(waiterRepository.findById(3L)).thenReturn(Optional.of(waiter(3)));
        when(sessionRepository.findFirstByWaiterIdAndStatusOrderByLoginTimeDesc(3L, "ACTIVE"))
                .thenReturn(Optional.of(sinTurno));

        WaiterSession result = service.login(3L);

        assertEquals(WaiterSession.STATUS_CLOSED, sinTurno.getStatus());
        assertEquals(WaiterSession.STATUS_ACTIVE, result.getStatus());
        assertNotSame(sinTurno, result);
    }

    @Test
    void abrirTurnoDosVecesFalla() {
        when(waiterRepository.findById(3L)).thenReturn(Optional.of(waiter(3)));
        when(sessionRepository.findFirstByWaiterIdAndStatusOrderByLoginTimeDesc(3L, "ACTIVE"))
                .thenReturn(Optional.of(activeSession(3L, new BigDecimal("50000"))));

        assertThrows(IllegalStateException.class,
                () -> service.openShift(new OpenShiftRequest(3L, new BigDecimal("20000"))));
    }

    @Test
    void cierreDeTurnoCalculaCajaYDiferencia() {
        WaiterSession session = activeSession(3L, new BigDecimal("50000"));
        UUID id = session.getId();
        when(sessionRepository.findById(id)).thenReturn(Optional.of(session));
        when(waiterRepository.findById(3L)).thenReturn(Optional.of(waiter(3)));
        when(orderRepository.findByWaiterSessionId(id)).thenReturn(List.of(
                order("CASH", "30000"), order("CASH", "20000"), order("NEQUI", "15000")));

        ShiftSummaryResponse summary = service.closeShift(id, new CloseShiftRequest(new BigDecimal("95000")));

        assertEquals(new BigDecimal("50000"), summary.cashSales());
        assertEquals(new BigDecimal("100000"), summary.expectedCash()); // base 50k + cash 50k
        assertEquals(new BigDecimal("-5000"), summary.difference());    // declaró 95k
        assertEquals(new BigDecimal("65000"), summary.totalSales());
        assertEquals(3, summary.totalOrders());
        assertEquals(2L, summary.ordersByMethod().get("CASH"));
        assertEquals(WaiterSession.STATUS_CLOSED, session.getStatus());
        assertNotNull(session.getClosedAt());
    }

    @Test
    void ordenConIdempotencyKeyRepetidaDevuelveLaExistente() {
        Order existente = order("CASH", "10000");
        existente.setIdOrder(301900L);
        existente.setIdempotencyKey("k-1");
        when(orderRepository.findByIdempotencyKey("k-1")).thenReturn(Optional.of(existente));

        WaiterOrderResponse resp = service.createOrder(new WaiterOrderRequest(
                "AMARILLO", "5", "CASH",
                List.of(new OrderItemRequestRecord("p1", 1, new BigDecimal("10000"), null, null)),
                null, "k-1", null));

        assertEquals(301900L, resp.idOrder());
        verify(orderPort, never()).createOrUpdateOrder(any());
    }

    @Test
    void ordenNuevaGuardaIdempotenciaYAutoria() {
        WaiterSession session = activeSession(3L, new BigDecimal("50000"));
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(orderRepository.findByIdempotencyKey("k-2")).thenReturn(Optional.empty());
        Order creada = order("CASH", "10000");
        creada.setIdOrder(301901L);
        when(orderPort.createOrUpdateOrder(any())).thenReturn(creada);

        WaiterOrderResponse resp = service.createOrder(new WaiterOrderRequest(
                "AMARILLO", "5", "CASH",
                List.of(new OrderItemRequestRecord("p1", 1, new BigDecimal("10000"), null, null)),
                null, "k-2", session.getId().toString()));

        assertEquals("k-2", creada.getIdempotencyKey());
        assertEquals(3L, creada.getWaiterId());
        assertEquals(session.getId(), creada.getWaiterSessionId());
        assertEquals(301901L, resp.idOrder());
    }

    @Test
    void ordenConSesionCerradaFalla() {
        WaiterSession cerrada = activeSession(3L, null);
        cerrada.setStatus(WaiterSession.STATUS_CLOSED);
        when(sessionRepository.findById(cerrada.getId())).thenReturn(Optional.of(cerrada));

        assertThrows(IllegalStateException.class, () -> service.createOrder(new WaiterOrderRequest(
                "AMARILLO", "5", "CASH",
                List.of(new OrderItemRequestRecord("p1", 1, new BigDecimal("10000"), null, null)),
                null, null, cerrada.getId().toString())));
    }
}
