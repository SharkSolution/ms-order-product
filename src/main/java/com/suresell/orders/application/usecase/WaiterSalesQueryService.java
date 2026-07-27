package com.suresell.orders.application.usecase;

import com.suresell.orders.application.dto.WaiterSalesDtos.WaiterSalesItem;
import com.suresell.orders.application.dto.WaiterSalesDtos.WaiterSalesResponse;
import com.suresell.orders.infrastructure.persistence.OrderPaymentRepository;
import com.suresell.orders.infrastructure.persistence.OrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ventas por mesero del día, para el cierre de caja del POS.
 *
 * <p>El POS pedía este dato a <b>ms-core-app</b>, que lee la base LEGACY. Tras
 * el cutover eso dejó de tener sentido: las ventas del día se escriben en la
 * base de V2, así que la pantalla mostraba solo lo vendido ANTES del corte y no
 * volvía a moverse. Este servicio calcula lo mismo sobre V2.
 *
 * <p>Mantiene el contrato que ya consume el POS (`waiters`, `unassigned`,
 * `breakdown` por método) con dos mejoras respecto al legacy:
 * <ul>
 *   <li>Las órdenes <b>MIXED</b> se reparten por sus splits reales. En el legacy
 *       caían enteras bajo la etiqueta "MIXED" y su parte en efectivo no se
 *       veía — justo el número que la cajera usa para recibir el dinero.</li>
 *   <li>Lo rotulado <b>NEQUI</b> (histórico o de un APK viejo) se pliega en QR,
 *       igual que hace el cierre de caja.</li>
 * </ul>
 */
@Service
public class WaiterSalesQueryService {

    private static final String SIN_ASIGNAR = "Sin asignar";

    private final OrderRepository orderRepository;
    private final OrderPaymentRepository orderPaymentRepository;

    public WaiterSalesQueryService(OrderRepository orderRepository,
                                   OrderPaymentRepository orderPaymentRepository) {
        this.orderRepository = orderRepository;
        this.orderPaymentRepository = orderPaymentRepository;
    }

    @Transactional(readOnly = true)
    public WaiterSalesResponse ventasDelDia(LocalDate fecha) {
        LocalDateTime inicio = fecha.atStartOfDay();
        LocalDateTime fin = fecha.atTime(LocalTime.MAX);

        Map<Long, Acumulado> porMesero = new LinkedHashMap<>();
        Acumulado sinAsignar = new Acumulado(null, SIN_ASIGNAR);

        // 1) Conteo de órdenes por mesero. Va aparte para no multiplicarlo por
        //    cada método de pago (una orden mixta tiene varias filas de monto).
        for (Object[] fila : orderRepository.contarOrdenesPorMesero(inicio, fin)) {
            Long idMesero = (Long) fila[0];
            String nombre = (String) fila[1];
            long cuantas = ((Number) fila[2]).longValue();
            destino(porMesero, sinAsignar, idMesero, nombre).sumarOrdenes(cuantas);
        }

        // 2) Montos de las órdenes de un solo método.
        for (Object[] fila : orderRepository.sumarPorMeseroYMetodo(inicio, fin)) {
            Long idMesero = (Long) fila[0];
            String metodo = normalizar((String) fila[1]);
            BigDecimal monto = monto(fila[2]);
            destino(porMesero, sinAsignar, idMesero, null).sumarMonto(metodo, monto);
        }

        // 3) Splits de las órdenes MIXED.
        for (Object[] fila : orderPaymentRepository.sumSplitsByWaiterAndMethod(inicio, fin)) {
            Long idMesero = (Long) fila[0];
            String metodo = normalizar((String) fila[1]);
            BigDecimal monto = monto(fila[2]);
            destino(porMesero, sinAsignar, idMesero, null).sumarMonto(metodo, monto);
        }

        Map<String, BigDecimal> totalPorMetodo = new HashMap<>();
        BigDecimal granTotal = BigDecimal.ZERO;
        long totalOrdenes = 0L;
        for (Acumulado a : todos(porMesero, sinAsignar)) {
            a.desglose.forEach((m, v) -> totalPorMetodo.merge(m, v, BigDecimal::add));
            granTotal = granTotal.add(a.total());
            totalOrdenes += a.ordenes;
        }

        List<WaiterSalesItem> meseros = porMesero.values().stream()
                .map(Acumulado::toDto)
                .sorted(Comparator.comparing(WaiterSalesItem::total).reversed())
                .toList();

        return new WaiterSalesResponse(
                fecha, granTotal, totalOrdenes, totalPorMetodo,
                meseros, sinAsignar.vacio() ? null : sinAsignar.toDto());
    }

    private List<Acumulado> todos(Map<Long, Acumulado> porMesero, Acumulado sinAsignar) {
        List<Acumulado> lista = new java.util.ArrayList<>(porMesero.values());
        if (!sinAsignar.vacio()) {
            lista.add(sinAsignar);
        }
        return lista;
    }

    private Acumulado destino(Map<Long, Acumulado> porMesero, Acumulado sinAsignar,
                              Long idMesero, String nombre) {
        if (idMesero == null) {
            return sinAsignar;
        }
        Acumulado a = porMesero.computeIfAbsent(idMesero, id -> new Acumulado(id, nombre));
        if (a.nombre == null && nombre != null) {
            a.nombre = nombre;
        }
        return a;
    }

    /** NEQUI se pliega en QR, igual que en el cierre de caja. */
    private static String normalizar(String metodo) {
        if (metodo == null || metodo.isBlank()) {
            return "UNKNOWN";
        }
        String m = metodo.trim().toUpperCase();
        return "NEQUI".equals(m) ? "QR" : m;
    }

    private static BigDecimal monto(Object valor) {
        if (valor == null) {
            return BigDecimal.ZERO;
        }
        return valor instanceof BigDecimal bd ? bd : new BigDecimal(valor.toString());
    }

    private static final class Acumulado {
        private final Long id;
        private String nombre;
        private final Map<String, BigDecimal> desglose = new HashMap<>();
        private long ordenes = 0L;

        Acumulado(Long id, String nombre) {
            this.id = id;
            this.nombre = nombre;
        }

        void sumarOrdenes(long cuantas) {
            ordenes += cuantas;
        }

        void sumarMonto(String metodo, BigDecimal monto) {
            desglose.merge(metodo, monto, BigDecimal::add);
        }

        BigDecimal total() {
            return desglose.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        boolean vacio() {
            return ordenes == 0L && desglose.isEmpty();
        }

        WaiterSalesItem toDto() {
            return new WaiterSalesItem(id, nombre == null ? SIN_ASIGNAR : nombre,
                    ordenes, total(), Map.copyOf(desglose));
        }
    }
}
