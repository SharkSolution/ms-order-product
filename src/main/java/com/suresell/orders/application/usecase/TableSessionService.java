package com.suresell.orders.application.usecase;

import com.suresell.orders.domain.model.RestaurantTable;
import com.suresell.orders.domain.model.TableSession;
import com.suresell.orders.infrastructure.persistence.RestaurantTableRepository;
import com.suresell.orders.domain.model.Order;
import com.suresell.orders.infrastructure.persistence.OrderRepository;
import com.suresell.orders.infrastructure.persistence.TableSessionRepository;
import java.math.BigDecimal;
import java.util.Map;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Cuentas de mesa (Inc. 3 y 4 del modo Restaurante).
 *
 * El cobro es de la SESIÓN, no de cada orden: al cerrar la mesa todas sus
 * órdenes pasan a `pagado` de una, así el cierre de caja sigue funcionando sin
 * cambiar su lógica.
 */
@Service
@RequiredArgsConstructor
public class TableSessionService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final TableSessionRepository sessionRepository;
    private final RestaurantTableRepository tableRepository;
    private final OrderRepository orderRepository;

    public List<TableSession> vivas() {
        return sessionRepository.findVivas();
    }

    /**
     * Cuenta viva de una mesa; si no tiene, la abre.
     *
     * <p>Lo usa la app de meseros: el mesero toma el pedido en la mesa y no
     * debería tener que acordarse de "abrir la mesa" antes. En la práctica el
     * primer pedido ES la apertura, y las rondas siguientes se acumulan en la
     * misma cuenta para cobrarlas todas juntas al final.
     */
    @Transactional
    public TableSession abrirOReusar(Integer numeroMesa, String usuario) {
        RestaurantTable mesa = tableRepository.findByNumber(numeroMesa)
                .orElseThrow(() -> new IllegalArgumentException("No existe la mesa " + numeroMesa));
        return sessionRepository.findVivas().stream()
                .filter(s -> mesa.getId().equals(s.getTableId()))
                .findFirst()
                .orElseGet(() -> abrir(numeroMesa, usuario));
    }

    /**
     * Abre la cuenta de una mesa.
     *
     * La unicidad la garantiza el ÍNDICE ÚNICO PARCIAL de V25, no este código:
     * si dos cajas abren la misma mesa a la vez, la segunda choca contra la BD
     * y aquí se traduce a un mensaje claro. Un chequeo previo sería
     * check-then-act y las dos pasarían.
     */
    @Transactional
    public TableSession abrir(Integer numeroMesa, String usuario) {
        RestaurantTable mesa = tableRepository.findByNumber(numeroMesa)
                .orElseThrow(() -> new IllegalArgumentException("No existe la mesa " + numeroMesa));
        if (!Boolean.TRUE.equals(mesa.getActive())) {
            throw new IllegalArgumentException("La mesa " + numeroMesa + " está inactiva");
        }
        TableSession sesion = new TableSession();
        sesion.setId(UUID.randomUUID());
        sesion.setTableId(mesa.getId());
        sesion.setSiteId(mesa.getSiteId());
        sesion.setStatus(TableSession.ABIERTA);
        sesion.setOpenedAt(LocalDateTime.now(BOGOTA));
        sesion.setOpenedBy(usuario);
        try {
            // saveAndFlush, NO save: con @Transactional el INSERT se posterga al
            // commit y la violación del índice único saltaría DESPUÉS de este
            // try/catch, saliendo como 500 en vez del 409 con mensaje claro.
            return sessionRepository.saveAndFlush(sesion);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(
                    "La mesa " + numeroMesa + " ya tiene una cuenta abierta");
        }
    }

    /** Marca quién está cobrando. Lock suave: avisa, no impide. */
    @Transactional
    public TableSession reclamarParaCobro(UUID sesionId, String usuario) {
        TableSession sesion = obtenerViva(sesionId);
        sesion.setStatus(TableSession.COBRANDO);
        sesion.setClaimedBy(usuario);
        sesion.setClaimedAt(LocalDateTime.now(BOGOTA));
        return sessionRepository.save(sesion);
    }

    @Transactional
    public TableSession cerrar(UUID sesionId) {
        TableSession sesion = obtenerViva(sesionId);
        sesion.setStatus(TableSession.CERRADA);
        sesion.setClosedAt(LocalDateTime.now(BOGOTA));
        return sessionRepository.save(sesion);
    }

    /** Resumen de lo consumido en la mesa, para mostrarlo ANTES de cobrar. */
    public Map<String, Object> resumen(UUID sesionId) {
        TableSession sesion = sessionRepository.findById(sesionId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la cuenta de mesa"));
        List<Order> ordenes = orderRepository.findByTableSessionId(sesionId);
        BigDecimal total = ordenes.stream()
                .map(Order::getTotal).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of(
                "sessionId", sesionId.toString(),
                "tableId", sesion.getTableId(),
                "status", sesion.getStatus(),
                "ordenes", ordenes.size(),
                "total", total);
    }

    /**
     * COBRA LA MESA COMPLETA (Inc. 4).
     *
     * El cobro es de la SESIÓN, no de cada orden: se suma todo lo consumido y se
     * cobra una sola vez. Todas las órdenes de la cuenta pasan de `abierta` a
     * `pagado` en un mismo movimiento, así el cierre de caja las ve como una
     * venta normal del día sin tocar su lógica.
     *
     * LIMITACIÓN CONSCIENTE: por ahora un solo medio de pago para toda la mesa.
     * El multipago (V13) reparte por ORDEN, y repartir proporcionalmente entre
     * varias órdenes genera descuadres de redondeo — con plata de por medio eso
     * se diseña, no se improvisa. La división de cuenta va por el mismo camino.
     */
    @Transactional
    public Map<String, Object> cobrar(UUID sesionId, String metodoPago) {
        String metodo = OrderHandler.normalizePaymentMethod(metodoPago);
        if (metodo == null || !List.of("CASH", "CARD", "QR").contains(metodo)) {
            throw new IllegalArgumentException("Método de pago inválido. Use CASH, CARD o QR");
        }
        TableSession sesion = obtenerViva(sesionId);

        List<Order> ordenes = orderRepository.findByTableSessionId(sesionId);
        if (ordenes.isEmpty()) {
            throw new IllegalStateException(
                    "La mesa no tiene consumo registrado; no hay nada que cobrar");
        }
        BigDecimal total = ordenes.stream()
                .map(Order::getTotal)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int cobradas = orderRepository.cobrarOrdenesDeLaMesa(sesionId, metodo);

        sesion.setStatus(TableSession.CERRADA);
        sesion.setClosedAt(LocalDateTime.now(BOGOTA));
        sessionRepository.save(sesion);

        return Map.of(
                "sessionId", sesionId.toString(),
                "tableId", sesion.getTableId(),
                "ordenesCobradas", cobradas,
                "total", total,
                "paymentMethod", metodo);
    }

    private TableSession obtenerViva(UUID sesionId) {
        TableSession sesion = sessionRepository.findById(sesionId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la cuenta de mesa"));
        if (!sesion.estaViva()) {
            throw new IllegalStateException("Esa cuenta de mesa ya está cerrada");
        }
        return sesion;
    }

    /**
     * Cuántas mesas quedan sin cobrar. Lo usa el cierre de caja para BLOQUEARSE:
     * cerrar caja con mesas abiertas dejaría consumo sin cobrar fuera del cuadre.
     */
    public List<TableSession> pendientesDeCobro() {
        return sessionRepository.findVivas();
    }
}
