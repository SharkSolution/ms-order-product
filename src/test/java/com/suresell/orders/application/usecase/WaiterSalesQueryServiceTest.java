package com.suresell.orders.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.suresell.orders.application.dto.WaiterSalesDtos.WaiterSalesItem;
import com.suresell.orders.application.dto.WaiterSalesDtos.WaiterSalesResponse;
import com.suresell.orders.infrastructure.persistence.OrderPaymentRepository;
import com.suresell.orders.infrastructure.persistence.OrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Ventas por mesero del cierre. Son los números con los que la cajera RECIBE
 * dinero físico de cada mesero, así que se verifican al peso.
 */
class WaiterSalesQueryServiceTest {

    private OrderRepository orderRepository;
    private OrderPaymentRepository orderPaymentRepository;
    private WaiterSalesQueryService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderPaymentRepository = mock(OrderPaymentRepository.class);
        service = new WaiterSalesQueryService(orderRepository, orderPaymentRepository);
        when(orderPaymentRepository.sumSplitsByWaiterAndMethod(any(), any())).thenReturn(List.of());
    }

    private WaiterSalesItem mesero(WaiterSalesResponse r, String nombre) {
        return r.waiters().stream().filter(w -> nombre.equals(w.waiterName())).findFirst().orElseThrow();
    }

    @Test
    void agrupaPorMeseroConSuDesglosePorMetodo() {
        when(orderRepository.contarOrdenesPorMesero(any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{3L, "Gabriela", 4L},
                new Object[]{2L, "Shark", 2L}));
        when(orderRepository.sumarPorMeseroYMetodo(any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{3L, "CASH", new BigDecimal("50000")},
                new Object[]{3L, "CARD", new BigDecimal("30000")},
                new Object[]{2L, "CASH", new BigDecimal("20000")}));

        WaiterSalesResponse r = service.ventasDelDia(LocalDate.of(2026, 7, 27));

        assertEquals(2, r.waiters().size());
        WaiterSalesItem gabriela = mesero(r, "Gabriela");
        assertEquals(4L, gabriela.ordersCount());
        assertEquals(0, new BigDecimal("80000").compareTo(gabriela.total()));
        assertEquals(0, new BigDecimal("50000").compareTo(gabriela.breakdown().get("CASH")));
        // Ordenados de mayor a menor venta.
        assertEquals("Gabriela", r.waiters().get(0).waiterName());
        assertEquals(0, new BigDecimal("100000").compareTo(r.grandTotal()));
        assertEquals(6L, r.totalOrders());
    }

    /**
     * Una venta MIXED debe repartirse por sus splits. Si cayera entera bajo la
     * etiqueta "MIXED", la cajera no vería su parte en efectivo — que es
     * exactamente el dinero que tiene que recibir.
     */
    @Test
    void laVentaMixtaSeRepartePorSusSplits() {
        when(orderRepository.contarOrdenesPorMesero(any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{3L, "Gabriela", 1L}));
        when(orderRepository.sumarPorMeseroYMetodo(any(), any())).thenReturn(List.of());
        when(orderPaymentRepository.sumSplitsByWaiterAndMethod(any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{3L, "CASH", new BigDecimal("12000")},
                new Object[]{3L, "CARD", new BigDecimal("8000")}));

        WaiterSalesItem gabriela = mesero(service.ventasDelDia(LocalDate.of(2026, 7, 27)), "Gabriela");

        assertEquals(0, new BigDecimal("12000").compareTo(gabriela.breakdown().get("CASH")));
        assertEquals(0, new BigDecimal("8000").compareTo(gabriela.breakdown().get("CARD")));
        assertNull(gabriela.breakdown().get("MIXED"), "la mixta no debe quedar como categoria propia");
        assertEquals(0, new BigDecimal("20000").compareTo(gabriela.total()));
        // La orden se cuenta UNA vez aunque tenga dos splits.
        assertEquals(1L, gabriela.ordersCount());
    }

    /** Lo rotulado NEQUI (APK viejo o histórico) se pliega en QR, como en el cierre. */
    @Test
    void nequiSePliegaEnQr() {
        when(orderRepository.contarOrdenesPorMesero(any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{3L, "Gabriela", 2L}));
        when(orderRepository.sumarPorMeseroYMetodo(any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{3L, "NEQUI", new BigDecimal("15000")},
                new Object[]{3L, "QR", new BigDecimal("5000")}));

        WaiterSalesItem gabriela = mesero(service.ventasDelDia(LocalDate.of(2026, 7, 27)), "Gabriela");

        assertEquals(0, new BigDecimal("20000").compareTo(gabriela.breakdown().get("QR")));
        assertNull(gabriela.breakdown().get("NEQUI"));
    }

    /** Las ventas de caja van aparte, no mezcladas con los meseros. */
    @Test
    void lasVentasDeCajaVanEnUnassigned() {
        when(orderRepository.contarOrdenesPorMesero(any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{null, null, 5L},
                new Object[]{3L, "Gabriela", 1L}));
        when(orderRepository.sumarPorMeseroYMetodo(any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{null, "CASH", new BigDecimal("90000")},
                new Object[]{3L, "CASH", new BigDecimal("10000")}));

        WaiterSalesResponse r = service.ventasDelDia(LocalDate.of(2026, 7, 27));

        assertEquals(1, r.waiters().size(), "caja no debe aparecer como un mesero mas");
        assertNotNull(r.unassigned());
        assertEquals(5L, r.unassigned().ordersCount());
        assertEquals(0, new BigDecimal("90000").compareTo(r.unassigned().total()));
    }

    /** Un día sin ventas de caja no debe inventar la fila "Sin asignar". */
    @Test
    void sinVentasDeCajaNoHayUnassigned() {
        when(orderRepository.contarOrdenesPorMesero(any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{3L, "Gabriela", 1L}));
        when(orderRepository.sumarPorMeseroYMetodo(any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{3L, "CASH", new BigDecimal("10000")}));

        assertNull(service.ventasDelDia(LocalDate.of(2026, 7, 27)).unassigned());
    }
}
