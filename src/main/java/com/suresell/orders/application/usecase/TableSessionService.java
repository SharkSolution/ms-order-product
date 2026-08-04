package com.suresell.orders.application.usecase;

import com.suresell.orders.domain.model.RestaurantTable;
import com.suresell.orders.domain.model.TableSession;
import com.suresell.orders.domain.model.TableSessionSplit;
import com.suresell.orders.domain.service.DivisionDeCuenta;
import com.suresell.orders.infrastructure.persistence.TableSessionSplitRepository;
import com.suresell.orders.infrastructure.persistence.RestaurantTableRepository;
import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderPayment;
import com.suresell.orders.infrastructure.persistence.OrderPaymentRepository;
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
    /** Splits de la división de cuenta: es de donde el cierre lee lo cobrado. */
    private final OrderPaymentRepository orderPaymentRepository;
    /** Auditoría de las divisiones: única fuente de verdad del ajuste. */
    private final TableSessionSplitRepository splitRepository;

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
     * Un solo medio de pago para toda la mesa. Para repartir entre comensales,
     * ver {@link #cobrarDividido}.
     */
    @Transactional
    public Map<String, Object> cobrar(UUID sesionId, String metodoPago) {
        String metodo = OrderHandler.normalizePaymentMethod(metodoPago);
        if (metodo == null || !List.of("CASH", "CARD", "QR").contains(metodo)) {
            throw new IllegalArgumentException("Método de pago inválido. Use CASH, CARD o QR");
        }
        TableSession sesion = obtenerViva(sesionId);

        List<Order> ordenes = ordenesCobrables(sesionId);
        BigDecimal total = totalDe(ordenes);

        int cobradas = orderRepository.cobrarOrdenesDeLaMesa(sesionId, metodo);

        cerrarSesion(sesion);

        return Map.of(
                "sessionId", sesionId.toString(),
                "tableId", sesion.getTableId(),
                "ordenesCobradas", cobradas,
                "total", total,
                "paymentMethod", metodo);
    }

    /**
     * COBRA LA MESA DIVIDIDA ENTRE N COMENSALES.
     *
     * <p>Es lo último que faltaba del bloque 3 de la V2, y lo que lo trababa no
     * era el código sino una decisión: repartir $10.000 entre 3 deja pesos que
     * no se pueden cobrar. La decisión tomada es que <b>los absorbe el
     * negocio</b>. Nunca se redondea hacia arriba: cobrarle de más a un
     * comensal es inaceptable comercial y fiscalmente.
     *
     * <p>La aritmética vive en {@link DivisionDeCuenta}, aparte y sin
     * dependencias, porque es la parte que tiene que ser imposible de romper.
     * Aquí solo se persiste.
     *
     * <p>Tres cosas que importan de esta implementación:
     *
     * <ul>
     *   <li><b>Los montos los pone el servidor.</b> El cliente dice cuántas
     *       personas son y con qué paga cada una; nunca manda cifras. Así
     *       ninguna caja puede registrar un reparto que no cuadre.
     *   <li><b>Las órdenes quedan como {@code MIXED} siempre</b>, incluso si
     *       todos pagan con el mismo medio. Es obligatorio: el cierre suma las
     *       órdenes no-MIXED por su {@code total}, y en una cuenta dividida lo
     *       cobrado es MENOR que el total (justo por el residuo). Marcarlas
     *       MIXED hace que el cierre las lea desde {@code order_payments}, que
     *       es donde está la cifra real.
     *   <li><b>El residuo se registra</b> en {@code rounding_adjustment} y sale
     *       como línea propia en el cierre. Un descuadre silencioso destruiría
     *       la promesa de un cierre auditable al peso.
     * </ul>
     *
     * @param metodosPorPersona un medio de pago por comensal, en orden
     */
    @Transactional
    public Map<String, Object> cobrarDividido(UUID sesionId, int personas, List<String> metodosPorPersona,
                                              String usuario) {
        List<String> metodos = normalizarMetodos(metodosPorPersona);
        if (metodos.size() != personas) {
            throw new IllegalArgumentException(String.format(
                    "Se esperaba el medio de pago de cada una de las %d personas y llegaron %d",
                    personas, metodos.size()));
        }
        TableSession sesion = obtenerViva(sesionId);

        List<Order> ordenes = ordenesCobrables(sesionId);
        BigDecimal total = totalDe(ordenes);

        DivisionDeCuenta.Reparto reparto = DivisionDeCuenta.repartir(total, personas);
        Map<String, BigDecimal> porMetodo = DivisionDeCuenta.agruparPorMetodo(reparto, metodos);

        // Cada medio se reparte entre las rondas de la mesa de forma exacta, para
        // que el efectivo se siga atribuyendo al mesero que tomó cada ronda.
        List<BigDecimal> pesos = ordenes.stream()
                .map(o -> o.getTotal() == null ? BigDecimal.ZERO : o.getTotal())
                .toList();
        LocalDateTime ahora = LocalDateTime.now(BOGOTA);
        for (Map.Entry<String, BigDecimal> e : porMetodo.entrySet()) {
            List<BigDecimal> partes = DivisionDeCuenta.repartirProporcional(e.getValue(), pesos);
            for (int i = 0; i < ordenes.size(); i++) {
                if (partes.get(i).compareTo(BigDecimal.ZERO) <= 0) {
                    continue; // Un pago en cero no es un pago: no se guarda basura.
                }
                OrderPayment pago = new OrderPayment();
                pago.setOrderUuidId(ordenes.get(i).getUuidId());
                pago.setMethod(e.getKey());
                pago.setAmount(partes.get(i));
                pago.setCreatedAt(ahora);
                orderPaymentRepository.save(pago);
            }
        }

        int cobradas = orderRepository.cobrarOrdenesDeLaMesa(sesionId, "MIXED");

        guardarAuditoriaDeLaDivision(sesionId, reparto, metodos, ahora, usuario);
        cerrarSesion(sesion);

        Map<String, Object> salida = new java.util.LinkedHashMap<>();
        salida.put("sessionId", sesionId.toString());
        salida.put("tableId", sesion.getTableId());
        salida.put("ordenesCobradas", cobradas);
        salida.put("total", total);
        salida.put("paymentMethod", "MIXED");
        salida.put("personas", personas);
        salida.put("porPersona", reparto.base());
        salida.put("cobrado", reparto.cobrado());
        salida.put("ajusteRedondeoNegocio", reparto.residuo());
        salida.put("porMetodo", porMetodo);
        return salida;
    }

    /**
     * DEJA EL RASTRO DE LA DIVISIÓN.
     *
     * <p>Es la única fuente de verdad del ajuste por redondeo: no se guarda una
     * copia del monto en la mesa ni en el cierre. Un mismo número en dos tablas
     * es un número que algún día discrepa, y en dinero eso es un incidente.
     *
     * <p>Guarda además CON QUÉ PAGÓ CADA COMENSAL, porque la pregunta que hace
     * un auditor no es "cuánto se absorbió" sino <b>por qué</b>.
     */
    private void guardarAuditoriaDeLaDivision(UUID sesionId, DivisionDeCuenta.Reparto reparto,
                                              List<String> metodos, LocalDateTime cuando, String usuario) {
        TableSessionSplit auditoria = new TableSessionSplit();
        auditoria.setTableSessionId(sesionId);
        auditoria.setPersonas(reparto.personas());
        auditoria.setTotal(reparto.total());
        auditoria.setPorPersona(reparto.base());
        auditoria.setCobrado(reparto.cobrado());
        auditoria.setAjusteRedondeo(reparto.residuo());
        auditoria.setDetallePorPersona(detalleEnJson(reparto, metodos));
        auditoria.setCreatedAt(cuando);
        auditoria.setCreatedBy(usuario);
        splitRepository.save(auditoria);
    }

    /**
     * {@code [{"persona":1,"metodo":"CASH","monto":3333}, ...]}.
     *
     * <p>Se arma a mano en vez de con Jackson porque son tres campos de tipos
     * conocidos y controlados por el servidor —el método ya viene normalizado a
     * CASH/CARD/QR y el monto es un entero—, así que no hay nada que escapar ni
     * una dependencia que justificar.
     */
    private String detalleEnJson(DivisionDeCuenta.Reparto reparto, List<String> metodos) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < metodos.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"persona\":").append(i + 1)
                .append(",\"metodo\":\"").append(metodos.get(i))
                .append("\",\"monto\":").append(reparto.base().toPlainString())
                .append('}');
        }
        return json.append(']').toString();
    }

    /**
     * Lo que el negocio dejó de cobrar por redondeo en la ventana del turno,
     * SUMADO AL VUELO desde la auditoría de divisiones.
     *
     * <p>Lo usa el cierre de caja para mostrarlo como línea propia. Es
     * determinista —una división ya cobrada no cambia— así que reabrir un cierre
     * viejo da siempre el mismo número. Devuelve cero, nunca nulo, para que un
     * turno sin mesas divididas no rompa el cierre.
     */
    public BigDecimal ajustePorRedondeoEntre(LocalDateTime desde, LocalDateTime hasta) {
        BigDecimal suma = splitRepository.sumaAjustePorRedondeo(desde, hasta);
        return suma == null ? BigDecimal.ZERO : suma;
    }

    /** El detalle de las divisiones del turno, para explicar el ajuste del cierre. */
    public List<TableSessionSplit> divisionesEntre(LocalDateTime desde, LocalDateTime hasta) {
        return splitRepository.entre(desde, hasta);
    }

    /**
     * Cuánto paga cada comensal, SIN cobrar. Es lo que el cajero le dice a la
     * mesa antes de tocar nada.
     */
    public Map<String, Object> previsualizarDivision(UUID sesionId, int personas) {
        sessionRepository.findById(sesionId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la cuenta de mesa"));
        BigDecimal total = totalDe(ordenesCobrables(sesionId));
        DivisionDeCuenta.Reparto reparto = DivisionDeCuenta.repartir(total, personas);

        Map<String, Object> salida = new java.util.LinkedHashMap<>();
        salida.put("sessionId", sesionId.toString());
        salida.put("total", reparto.total());
        salida.put("personas", reparto.personas());
        salida.put("porPersona", reparto.base());
        salida.put("cobrado", reparto.cobrado());
        salida.put("ajusteRedondeoNegocio", reparto.residuo());
        return salida;
    }

    /**
     * Órdenes de la cuenta que de verdad se van a cobrar. Sin consumo no hay
     * nada que cobrar.
     *
     * <p>Se filtra por estado {@code abierta} PORQUE ES EL MISMO CONJUNTO que
     * actualiza {@code cobrarOrdenesDeLaMesa}. Si el total se calculara sobre
     * un conjunto y el cobro se aplicara sobre otro, la mesa cobraría una cifra
     * y la caja registraría otra.
     *
     * <p>Hoy toda orden ligada a una mesa nace {@code abierta} (lo garantiza
     * {@code OrderHandler}), así que este filtro no cambia nada. Está para que
     * siga sin cambiar nada el día que eso deje de ser cierto: con plata de por
     * medio, depender de un invariante que vive en otra clase es cuestión de
     * tiempo. Las órdenes borradas ya quedan fuera por el {@code SQLRestriction}
     * de la entidad.
     */
    private List<Order> ordenesCobrables(UUID sesionId) {
        List<Order> ordenes = orderRepository.findByTableSessionId(sesionId).stream()
                .filter(o -> com.suresell.orders.domain.model.OrderStatus.abierta.equals(o.getStatus()))
                .toList();
        if (ordenes.isEmpty()) {
            throw new IllegalStateException(
                    "La mesa no tiene consumo registrado; no hay nada que cobrar");
        }
        return ordenes;
    }

    private BigDecimal totalDe(List<Order> ordenes) {
        return ordenes.stream()
                .map(Order::getTotal)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void cerrarSesion(TableSession sesion) {
        sesion.setStatus(TableSession.CERRADA);
        sesion.setClosedAt(LocalDateTime.now(BOGOTA));
        sessionRepository.save(sesion);
    }

    /**
     * Normaliza y valida los medios elegidos por cada comensal.
     *
     * Se normaliza igual que en el multipago de orden: un POS viejo que mande
     * NEQUI queda como QR y el cierre no revive una categoría eliminada.
     */
    private List<String> normalizarMetodos(List<String> crudos) {
        if (crudos == null || crudos.isEmpty()) {
            throw new IllegalArgumentException("Falta el medio de pago de cada persona");
        }
        return crudos.stream().map(m -> {
            String norm = OrderHandler.normalizePaymentMethod(m);
            if (norm == null || !List.of("CASH", "CARD", "QR").contains(norm)) {
                throw new IllegalArgumentException("Método de pago inválido en la división: " + m);
            }
            return norm;
        }).toList();
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
