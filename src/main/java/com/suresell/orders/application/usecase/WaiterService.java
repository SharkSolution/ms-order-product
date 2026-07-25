package com.suresell.orders.application.usecase;

import com.suresell.orders.application.dto.OrderRequestRecord;
import com.suresell.orders.application.dto.WaiterDtos.CloseShiftRequest;
import com.suresell.orders.application.dto.WaiterDtos.CreateWaiterRequest;
import com.suresell.orders.application.dto.WaiterDtos.MenuCategoryDto;
import com.suresell.orders.application.dto.WaiterDtos.MenuProductDto;
import com.suresell.orders.application.dto.WaiterDtos.OpenShiftRequest;
import com.suresell.orders.application.dto.WaiterDtos.ShiftSummaryResponse;
import com.suresell.orders.application.dto.WaiterDtos.WaiterOrderItem;
import com.suresell.orders.application.dto.WaiterDtos.WaiterOrderRequest;
import com.suresell.orders.application.dto.WaiterDtos.WaiterOrderResponse;
import com.suresell.orders.domain.model.MenuProduct;
import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.Waiter;
import com.suresell.orders.domain.model.WaiterSession;
import com.suresell.orders.domain.port.in.OrderPort;
import com.suresell.orders.infrastructure.persistence.MenuCategoryRepository;
import com.suresell.orders.infrastructure.persistence.MenuProductRepository;
import com.suresell.orders.infrastructure.persistence.OrderRepository;
import com.suresell.orders.infrastructure.persistence.WaiterRepository;
import com.suresell.orders.infrastructure.persistence.WaiterSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Módulo meseros del backend multi-tenant (F4 Inc.3, docs/200). Mismo
 * comportamiento que MobileWaiterHandler/WaiterShiftHandler del ms-order-waiter
 * legacy, scopeado por tenant vía RLS. La creación de órdenes REUSA el flujo del
 * POS (OrderPort: numeración por-tenant, tracking de cocina, descuentos) y le
 * añade idempotencia + autoría del mesero.
 */
@Service
public class WaiterService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WaiterService.class);
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    public static final String CASH = "CASH";

    private final WaiterRepository waiterRepository;
    private final WaiterSessionRepository sessionRepository;
    private final OrderRepository orderRepository;
    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuProductRepository menuProductRepository;
    private final OrderPort orderPort;

    public WaiterService(WaiterRepository waiterRepository,
                         WaiterSessionRepository sessionRepository,
                         OrderRepository orderRepository,
                         MenuCategoryRepository menuCategoryRepository,
                         MenuProductRepository menuProductRepository,
                         OrderPort orderPort) {
        this.waiterRepository = waiterRepository;
        this.sessionRepository = sessionRepository;
        this.orderRepository = orderRepository;
        this.menuCategoryRepository = menuCategoryRepository;
        this.menuProductRepository = menuProductRepository;
        this.orderPort = orderPort;
    }

    // ------------------------------------------------------------------
    // Meseros y sesiones
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Waiter> getActiveWaiters() {
        return waiterRepository.findByActiveTrueOrderByNameAsc();
    }

    /** Lista completa para el admin (incluye desactivados). */
    @Transactional(readOnly = true)
    public List<Waiter> getAllWaiters() {
        return waiterRepository.findAll(org.springframework.data.domain.Sort.by("name"));
    }

    @Transactional
    public Waiter createWaiter(CreateWaiterRequest request) {
        if (request == null || request.name() == null || request.name().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del mesero es obligatorio");
        }
        Waiter waiter = new Waiter();
        waiter.setName(request.name().trim());
        waiter.setActive(true);
        waiter.setDailySaleGoal(request.dailySaleGoal());
        waiter.setDefaultCashBase(request.defaultCashBase());
        return waiterRepository.save(waiter);
    }

    /** Edición parcial desde el admin (F5): null = campo sin cambio. */
    @Transactional
    public Waiter updateWaiter(Long id, com.suresell.orders.application.dto.WaiterDtos.UpdateWaiterRequest request) {
        Waiter waiter = waiterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mesero no encontrado: " + id));
        if (request == null) {
            return waiter;
        }
        if (request.name() != null && !request.name().trim().isEmpty()) {
            waiter.setName(request.name().trim());
        }
        if (request.active() != null) {
            waiter.setActive(request.active());
        }
        if (request.dailySaleGoal() != null) {
            waiter.setDailySaleGoal(request.dailySaleGoal());
        }
        if (request.defaultCashBase() != null) {
            waiter.setDefaultCashBase(request.defaultCashBase());
        }
        return waiterRepository.save(waiter);
    }

    /**
     * Login de mesero (selección, sin clave — como el legacy). Si hay una sesión
     * ACTIVE con turno operativo (base de caja declarada) se REUTILIZA para no
     * destruir el turno; una ACTIVE sin turno se cierra y se abre una fresca.
     */
    @Transactional
    public WaiterSession login(Long waiterId) {
        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new IllegalArgumentException("Mesero no encontrado: " + waiterId));

        var existing = sessionRepository
                .findFirstByWaiterIdAndStatusOrderByLoginTimeDesc(waiterId, WaiterSession.STATUS_ACTIVE);
        if (existing.isPresent()) {
            WaiterSession session = existing.get();
            if (session.getOpeningCashBase() != null) {
                return session;
            }
            session.setStatus(WaiterSession.STATUS_CLOSED);
            session.setLogoutTime(LocalDateTime.now(BOGOTA_ZONE));
            sessionRepository.save(session);
        }
        return newSession(waiter, null);
    }

    @Transactional
    public void logout(UUID sessionId) {
        WaiterSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesión no encontrada: " + sessionId));
        session.setStatus(WaiterSession.STATUS_CLOSED);
        session.setLogoutTime(LocalDateTime.now(BOGOTA_ZONE));
        sessionRepository.save(session);
    }

    // ------------------------------------------------------------------
    // Turnos (base de caja / cierre con efectivo declarado)
    // ------------------------------------------------------------------

    @Transactional
    public WaiterSession openShift(OpenShiftRequest request) {
        if (request == null || request.waiterId() == null || request.openingCashBase() == null) {
            throw new IllegalArgumentException("waiterId y openingCashBase son obligatorios");
        }
        Waiter waiter = waiterRepository.findById(request.waiterId())
                .orElseThrow(() -> new IllegalArgumentException("Mesero no encontrado: " + request.waiterId()));

        var existing = sessionRepository
                .findFirstByWaiterIdAndStatusOrderByLoginTimeDesc(waiter.getId(), WaiterSession.STATUS_ACTIVE);
        if (existing.isPresent()) {
            WaiterSession session = existing.get();
            if (session.getOpeningCashBase() != null) {
                throw new IllegalStateException("El mesero ya tiene un turno abierto");
            }
            session.setOpeningCashBase(request.openingCashBase());
            return sessionRepository.save(session);
        }
        return newSession(waiter, request.openingCashBase());
    }

    @Transactional(readOnly = true)
    public ShiftSummaryResponse getShiftSummary(UUID sessionId) {
        WaiterSession session = requireSession(sessionId);
        return buildSummary(session, session.getDeclaredCash());
    }

    @Transactional
    public ShiftSummaryResponse closeShift(UUID sessionId, CloseShiftRequest request) {
        if (request == null || request.declaredCash() == null) {
            throw new IllegalArgumentException("declaredCash es obligatorio");
        }
        WaiterSession session = requireSession(sessionId);
        ShiftSummaryResponse summary = buildSummary(session, request.declaredCash());
        session.setDeclaredCash(request.declaredCash());
        session.setExpectedCash(summary.expectedCash());
        session.setDifference(summary.difference());
        session.setStatus(WaiterSession.STATUS_CLOSED);
        LocalDateTime now = LocalDateTime.now(BOGOTA_ZONE);
        session.setClosedAt(now);
        session.setLogoutTime(now);
        sessionRepository.save(session);
        return buildSummary(session, request.declaredCash());
    }

    // ------------------------------------------------------------------
    // Menú y órdenes
    // ------------------------------------------------------------------

    /** Menú anidado con los mismos nombres de campo del legacy (id/name/products). */
    @Transactional(readOnly = true)
    public List<MenuCategoryDto> getMenu() {
        Map<String, List<MenuProduct>> byCategory = menuProductRepository.findAllWithCategory().stream()
                .filter(p -> p.getCategory() != null)
                .collect(Collectors.groupingBy(p -> p.getCategory().getIdCategory(),
                        LinkedHashMap::new, Collectors.toList()));
        return menuCategoryRepository.findAll().stream()
                .map(c -> new MenuCategoryDto(
                        c.getIdCategory(),
                        c.getNameCategory(),
                        byCategory.getOrDefault(c.getIdCategory(), List.of()).stream()
                                .map(p -> new MenuProductDto(p.getIdProduct(), p.getNameProduct(),
                                        p.getPrice(), p.getActive()))
                                .toList()))
                .toList();
    }

    /**
     * Crea la orden del mesero reutilizando el flujo del POS. Si ya existe una
     * orden con la misma idempotencyKey (reintento del móvil), la devuelve.
     */
    @Transactional
    public WaiterOrderResponse createOrder(WaiterOrderRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("El pedido debe tener items");
        }
        if (hasText(request.idempotencyKey())) {
            var existing = orderRepository.findByIdempotencyKey(request.idempotencyKey().trim());
            if (existing.isPresent()) {
                return toOrderResponse(existing.get());
            }
        }

        // Autoría del mesero: NUNCA se rechaza una venta por una sesión vieja/rota
        // (bug prod 2026-07-22: la app guarda la sesión localmente y una sesión
        // inexistente para RLS tumbaba la orden con 400). Se degrada con la mejor
        // atribución posible:
        //  - sesión ACTIVE → normal.
        //  - sesión CLOSED → se atribuye al mesero y a su sesión ACTIVE más
        //    reciente si existe (para que el turno cuadre).
        //  - sesión inexistente o id malformado → orden sin autoría + warn.
        Long waiterId = null;
        UUID sessionUuid = null;
        if (hasText(request.waiterSessionId())) {
            try {
                UUID requested = UUID.fromString(request.waiterSessionId().trim());
                var sessionOpt = sessionRepository.findById(requested);
                if (sessionOpt.isEmpty()) {
                    log.warn("Orden de mesero con sesión inexistente {} — se crea sin autoría", requested);
                } else {
                    WaiterSession session = sessionOpt.get();
                    waiterId = session.getWaiterId();
                    if (WaiterSession.STATUS_ACTIVE.equals(session.getStatus())) {
                        sessionUuid = session.getId();
                    } else {
                        sessionUuid = sessionRepository
                                .findFirstByWaiterIdAndStatusOrderByLoginTimeDesc(waiterId, WaiterSession.STATUS_ACTIVE)
                                .map(WaiterSession::getId)
                                .orElse(null);
                        log.warn("Orden de mesero con sesión CERRADA {} — reatribuida a mesero {} (sesión activa: {})",
                                requested, waiterId, sessionUuid);
                    }
                }
            } catch (IllegalArgumentException e) {
                log.warn("waiterSessionId malformado '{}' — la orden se crea sin autoría", request.waiterSessionId());
            }
        }

        // N2/D1: la clave viaja DENTRO de la creación para que el dedupe ocurra en
        // el mismo paso que el insert. Antes se tageaba después (check-then-act):
        // dos envíos simultáneos del móvil podían pasar ambos la verificación
        // previa y el segundo insert chocaba contra el índice único.
        String key = hasText(request.idempotencyKey()) ? request.idempotencyKey().trim() : null;
        Order created = orderPort.createOrUpdateOrder(new OrderRequestRecord(
                request.pagerColor(), request.pagerNumber(), request.items(),
                request.discountCode(), request.paymentMethod(), null, key));
        created.setIdempotencyKey(key);
        created.setWaiterId(waiterId);
        created.setWaiterSessionId(sessionUuid);
        orderRepository.tagWaiterOrder(created.getUuidId(), key, waiterId, sessionUuid);
        return toOrderResponse(created);
    }

    public java.util.Optional<WaiterOrderResponse> findByIdempotencyKey(String idempotencyKey) {
        if (!hasText(idempotencyKey)) {
            return java.util.Optional.empty();
        }
        return orderRepository.findByIdempotencyKey(idempotencyKey.trim()).map(this::toOrderResponse);
    }

    /** Historial del día actual (zona Bogotá) con filtros opcionales — como el legacy. */
    @Transactional(readOnly = true)
    public List<WaiterOrderResponse> getHistory(Long idOrder, String pagerNumber, String pagerColor, Long waiterId) {
        LocalDateTime start = LocalDateTime.now(BOGOTA_ZONE).with(LocalTime.MIN);
        LocalDateTime end = LocalDateTime.now(BOGOTA_ZONE).with(LocalTime.MAX);
        return orderRepository.findWaiterHistory(start, end, idOrder, pagerNumber, pagerColor, waiterId)
                .stream().map(this::toOrderResponse).toList();
    }

    // ------------------------------------------------------------------

    private WaiterSession newSession(Waiter waiter, BigDecimal openingCashBase) {
        WaiterSession session = new WaiterSession();
        session.setWaiterId(waiter.getId());
        session.setWaiterName(waiter.getName());
        session.setStatus(WaiterSession.STATUS_ACTIVE);
        session.setLoginTime(LocalDateTime.now(BOGOTA_ZONE));
        session.setOpeningCashBase(openingCashBase);
        return sessionRepository.save(session);
    }

    private WaiterSession requireSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesión no encontrada: " + sessionId));
    }

    private ShiftSummaryResponse buildSummary(WaiterSession session, BigDecimal declaredCash) {
        List<Order> orders = orderRepository.findByWaiterSessionId(session.getId());
        Map<String, BigDecimal> salesByMethod = new LinkedHashMap<>();
        Map<String, Long> ordersByMethod = new LinkedHashMap<>();
        BigDecimal totalSales = BigDecimal.ZERO;
        for (Order o : orders) {
            String method = o.getPaymentMethod() == null ? "OTRO" : o.getPaymentMethod();
            BigDecimal total = o.getTotal() == null ? BigDecimal.ZERO : o.getTotal();
            salesByMethod.merge(method, total, BigDecimal::add);
            ordersByMethod.merge(method, 1L, Long::sum);
            totalSales = totalSales.add(total);
        }
        BigDecimal cashSales = salesByMethod.getOrDefault(CASH, BigDecimal.ZERO);
        BigDecimal base = session.getOpeningCashBase() == null ? BigDecimal.ZERO : session.getOpeningCashBase();
        BigDecimal expectedCash = base.add(cashSales);
        BigDecimal difference = declaredCash == null ? null : declaredCash.subtract(expectedCash);
        BigDecimal dailySaleGoal = waiterRepository.findById(session.getWaiterId())
                .map(Waiter::getDailySaleGoal).orElse(null);
        return new ShiftSummaryResponse(
                session.getId(), session.getWaiterId(), session.getWaiterName(), session.getStatus(),
                session.getLoginTime(), session.getClosedAt(), session.getOpeningCashBase(),
                cashSales, expectedCash, declaredCash, difference,
                salesByMethod, ordersByMethod, totalSales, orders.size(), dailySaleGoal);
    }

    private WaiterOrderResponse toOrderResponse(Order order) {
        // Nombres de producto resueltos por lote DENTRO de la sesión (RLS scopea
        // al tenant): no se cachean en memoria porque los ids legibles pueden
        // repetirse entre negocios.
        Set<String> ids = order.getItems() == null ? Set.of()
                : order.getItems().stream().map(i -> i.getProductId())
                        .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> names = ids.isEmpty() ? Map.of()
                : menuProductRepository.findAllById(ids).stream()
                        .collect(Collectors.toMap(MenuProduct::getIdProduct, MenuProduct::getNameProduct));
        List<WaiterOrderItem> items = order.getItems() == null ? List.of()
                : order.getItems().stream().map(i -> new WaiterOrderItem(
                        i.getProductId(),
                        i.getProductId() == null ? null : names.getOrDefault(i.getProductId(), i.getProductId()),
                        i.getQuantity(), i.getUnitPrice(), i.getTotalPrice()))
                .toList();
        return new WaiterOrderResponse(
                order.getIdOrder(),
                order.getUuidId() == null ? null : order.getUuidId().toString(),
                order.getPagerColor(), order.getPagerNumber(), order.getCreatedAt(),
                order.getStatus() == null ? null : order.getStatus().name(),
                order.getPaymentMethod(), order.getSubtotal(), order.getTotal(),
                order.getWaiterId(), order.getIdempotencyKey(), items);
    }

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
