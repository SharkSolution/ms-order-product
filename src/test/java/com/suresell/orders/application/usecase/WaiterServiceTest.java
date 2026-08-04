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
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.times;
import com.suresell.orders.application.dto.OrderRequestRecord;
class WaiterServiceTest {

    private WaiterRepository waiterRepository;
    private WaiterSessionRepository sessionRepository;
    private OrderRepository orderRepository;
    private OrderPort orderPort;
    private SiteService siteService;
    private TableSessionService tableSessionService;
    private WaiterService service;

    @BeforeEach
    void setUp() {
        waiterRepository = mock(WaiterRepository.class);
        sessionRepository = mock(WaiterSessionRepository.class);
        orderRepository = mock(OrderRepository.class);
        orderPort = mock(OrderPort.class);
        siteService = mock(SiteService.class);
        tableSessionService = mock(TableSessionService.class);
        service = new WaiterService(waiterRepository, sessionRepository, orderRepository,
                mock(MenuCategoryRepository.class), mock(MenuProductRepository.class), orderPort,
                siteService, tableSessionService);
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
                () -> service.createWaiter(new CreateWaiterRequest("  ", null, null)));
        Waiter creado = service.createWaiter(new CreateWaiterRequest("Pedro", null, null));
        assertEquals("Pedro", creado.getName());
        assertTrue(creado.getActive());
    }

    @Test
    void updateWaiterEsParcial() {
        Waiter w = waiter(3);
        when(waiterRepository.findById(3L)).thenReturn(java.util.Optional.of(w));

        service.updateWaiter(3L, new com.suresell.orders.application.dto.WaiterDtos.UpdateWaiterRequest(
                null, false, null, new BigDecimal("40000")));

        assertEquals("Angie", w.getName());            // sin cambio
        assertEquals(false, w.getActive());            // desactivado
        assertEquals(new BigDecimal("40000"), w.getDefaultCashBase());
        verify(waiterRepository).save(w);
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
                null, "k-1", null, null, null));

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
                null, "k-2", session.getId().toString(), null, null));

        assertEquals("k-2", creada.getIdempotencyKey());
        assertEquals(3L, creada.getWaiterId());
        assertEquals(session.getId(), creada.getWaiterSessionId());
        assertEquals(301901L, resp.idOrder());
    }

    // Bug prod 2026-07-22 ("perfil pony"): una sesión vieja/rota NUNCA tumba la venta.

    @Test
    void ordenConSesionInexistenteSeCreaSinAutoria() {
        UUID fantasma = UUID.randomUUID();
        when(sessionRepository.findById(fantasma)).thenReturn(Optional.empty());
        Order creada = order("CASH", "10000");
        creada.setIdOrder(301902L);
        when(orderPort.createOrUpdateOrder(any())).thenReturn(creada);

        WaiterOrderResponse resp = service.createOrder(new WaiterOrderRequest(
                "AMARILLO", "5", "CASH",
                List.of(new OrderItemRequestRecord("p1", 1, new BigDecimal("10000"), null, null)),
                null, null, fantasma.toString(), null, null));

        assertEquals(301902L, resp.idOrder());
        assertNull(creada.getWaiterId());
        assertNull(creada.getWaiterSessionId());
    }

    @Test
    void ordenConSesionCerradaSeReatribuyeALaActivaDelMesero() {
        WaiterSession cerrada = activeSession(3L, null);
        cerrada.setStatus(WaiterSession.STATUS_CLOSED);
        WaiterSession activa = activeSession(3L, new BigDecimal("50000"));
        when(sessionRepository.findById(cerrada.getId())).thenReturn(Optional.of(cerrada));
        when(sessionRepository.findFirstByWaiterIdAndStatusOrderByLoginTimeDesc(3L, "ACTIVE"))
                .thenReturn(Optional.of(activa));
        Order creada = order("CASH", "10000");
        creada.setIdOrder(301903L);
        when(orderPort.createOrUpdateOrder(any())).thenReturn(creada);

        WaiterOrderResponse resp = service.createOrder(new WaiterOrderRequest(
                "AMARILLO", "5", "CASH",
                List.of(new OrderItemRequestRecord("p1", 1, new BigDecimal("10000"), null, null)),
                null, null, cerrada.getId().toString(), null, null));

        assertEquals(301903L, resp.idOrder());
        assertEquals(3L, creada.getWaiterId());
        assertEquals(activa.getId(), creada.getWaiterSessionId());
    }

    @Test
    void ordenConSesionMalformadaSeCreaSinAutoria() {
        Order creada = order("CASH", "10000");
        creada.setIdOrder(301904L);
        when(orderPort.createOrUpdateOrder(any())).thenReturn(creada);

        WaiterOrderResponse resp = service.createOrder(new WaiterOrderRequest(
                "AMARILLO", "5", "CASH",
                List.of(new OrderItemRequestRecord("p1", 1, new BigDecimal("10000"), null, null)),
                null, null, "no-es-un-uuid", null, null));

        assertEquals(301904L, resp.idOrder());
        assertNull(creada.getWaiterId());
    }

    // --- Mesa exacta y multipago desde la app de meseros ---------------------

    /**
     * Antes la app mandaba siempre el MISMO rastreador quemado y la cocina veía
     * todas las comandas iguales: no había forma de saber a qué mesa iba cada
     * plato. Ahora el número de mesa liga el pedido a la cuenta de esa mesa.
     */
    @Test
    void elPedidoSeLigaALaMesaIndicada() {
        when(siteService.enModoRestaurante()).thenReturn(true);
        java.util.UUID cuenta = java.util.UUID.randomUUID();
        com.suresell.orders.domain.model.TableSession sesion =
                new com.suresell.orders.domain.model.TableSession();
        sesion.setId(cuenta);
        when(tableSessionService.abrirOReusar(eq(23), anyString())).thenReturn(sesion);
        prepararCreacion();

        service.createOrder(new WaiterOrderRequest(
                "AMARILLO", "1", "CASH",
                List.of(new OrderItemRequestRecord("p1", 1, new BigDecimal("10000"), null, null)),
                null, "k-mesa", null, 23, null));

        ArgumentCaptor<OrderRequestRecord> captor = ArgumentCaptor.forClass(OrderRequestRecord.class);
        verify(orderPort).createOrUpdateOrder(captor.capture());
        assertEquals(cuenta.toString(), captor.getValue().tableSessionId());
    }

    /** Las rondas siguientes caen en la MISMA cuenta, no abren otra. */
    @Test
    void lasRondasSiguientesReusanLaCuentaDeLaMesa() {
        when(siteService.enModoRestaurante()).thenReturn(true);
        java.util.UUID cuenta = java.util.UUID.randomUUID();
        com.suresell.orders.domain.model.TableSession sesion =
                new com.suresell.orders.domain.model.TableSession();
        sesion.setId(cuenta);
        when(tableSessionService.abrirOReusar(eq(23), anyString())).thenReturn(sesion);
        prepararCreacion();

        service.createOrder(new WaiterOrderRequest("AMARILLO", "1", "CASH",
                List.of(new OrderItemRequestRecord("p1", 1, new BigDecimal("10000"), null, null)),
                null, "k-1", null, 23, null));
        service.createOrder(new WaiterOrderRequest("AMARILLO", "1", "CASH",
                List.of(new OrderItemRequestRecord("p2", 1, new BigDecimal("5000"), null, null)),
                null, "k-2", null, 23, null));

        ArgumentCaptor<OrderRequestRecord> captor = ArgumentCaptor.forClass(OrderRequestRecord.class);
        verify(orderPort, times(2)).createOrUpdateOrder(captor.capture());
        assertEquals(captor.getAllValues().get(0).tableSessionId(),
                captor.getAllValues().get(1).tableSessionId());
    }

    /** En Plazoleta no hay mesas: el número se ignora y el pedido va con rastreador. */
    @Test
    void enPlazoletaSeIgnoraLaMesa() {
        when(siteService.enModoRestaurante()).thenReturn(false);
        prepararCreacion();

        service.createOrder(new WaiterOrderRequest("AMARILLO", "5", "CASH",
                List.of(new OrderItemRequestRecord("p1", 1, new BigDecimal("10000"), null, null)),
                null, "k-plaz", null, 23, null));

        ArgumentCaptor<OrderRequestRecord> captor = ArgumentCaptor.forClass(OrderRequestRecord.class);
        verify(orderPort).createOrUpdateOrder(captor.capture());
        assertNull(captor.getValue().tableSessionId());
        verify(tableSessionService, never()).abrirOReusar(any(), anyString());
    }

    /** Sin número de mesa se comporta como antes: rastreador. */
    @Test
    void sinMesaSigueSiendoUnPedidoConRastreador() {
        when(siteService.enModoRestaurante()).thenReturn(true);
        prepararCreacion();

        service.createOrder(new WaiterOrderRequest("AMARILLO", "5", "CASH",
                List.of(new OrderItemRequestRecord("p1", 1, new BigDecimal("10000"), null, null)),
                null, "k-sinmesa", null, null, null));

        ArgumentCaptor<OrderRequestRecord> captor = ArgumentCaptor.forClass(OrderRequestRecord.class);
        verify(orderPort).createOrUpdateOrder(captor.capture());
        assertNull(captor.getValue().tableSessionId());
    }

    /** Multipago: las porciones llegan al flujo del POS, que ya sabe repartirlas. */
    @Test
    void elMultipagoLlegaAlFlujoDelPos() {
        when(siteService.enModoRestaurante()).thenReturn(false);
        prepararCreacion();
        var splits = List.of(
                new OrderRequestRecord.PaymentSplitRecord("CASH", new BigDecimal("4000")),
                new OrderRequestRecord.PaymentSplitRecord("QR", new BigDecimal("6000")));

        service.createOrder(new WaiterOrderRequest("AMARILLO", "5", "MIXED",
                List.of(new OrderItemRequestRecord("p1", 1, new BigDecimal("10000"), null, null)),
                null, "k-mixto", null, null, splits));

        ArgumentCaptor<OrderRequestRecord> captor = ArgumentCaptor.forClass(OrderRequestRecord.class);
        verify(orderPort).createOrUpdateOrder(captor.capture());
        assertEquals(2, captor.getValue().payments().size());
        assertEquals("MIXED", captor.getValue().paymentMethod());
    }

    private void prepararCreacion() {
        when(orderPort.createOrUpdateOrder(any())).thenAnswer(inv -> {
            Order o = new Order();
            o.setUuidId(java.util.UUID.randomUUID());
            o.setIdOrder(500L);
            o.setItems(List.of());
            return o;
        });
    }

}
