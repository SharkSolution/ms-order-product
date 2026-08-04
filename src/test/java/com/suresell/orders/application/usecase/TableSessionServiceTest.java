package com.suresell.orders.application.usecase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.suresell.orders.domain.model.RestaurantTable;
import com.suresell.orders.domain.model.TableSession;
import com.suresell.orders.infrastructure.persistence.RestaurantTableRepository;
import com.suresell.orders.infrastructure.persistence.TableSessionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** Cuentas de mesa (Inc. 3 y 4 del modo Restaurante). */
@ExtendWith(MockitoExtension.class)
class TableSessionServiceTest {

    @Mock private TableSessionRepository sessionRepository;
    @Mock private RestaurantTableRepository tableRepository;
    @Mock private com.suresell.orders.infrastructure.persistence.OrderRepository orderRepository;
    @Mock private com.suresell.orders.infrastructure.persistence.OrderPaymentRepository orderPaymentRepository;
    @Mock private com.suresell.orders.infrastructure.persistence.TableSessionSplitRepository splitRepository;

    private TableSessionService service;

    @BeforeEach
    void setUp() {
        service = new TableSessionService(
                sessionRepository, tableRepository, orderRepository, orderPaymentRepository,
                splitRepository);
    }

    private RestaurantTable mesa(int numero, boolean activa) {
        RestaurantTable m = new RestaurantTable();
        m.setId(10L);
        m.setNumber(numero);
        m.setActive(activa);
        return m;
    }

    @Test
    void abrirUnaMesaCreaLaCuentaEnEstadoAbierta() {
        when(tableRepository.findByNumber(5)).thenReturn(Optional.of(mesa(5, true)));
        when(sessionRepository.saveAndFlush(any(TableSession.class))).thenAnswer(i -> i.getArgument(0));

        TableSession s = service.abrir(5, "cajero1");

        assertEquals(TableSession.ABIERTA, s.getStatus());
        assertEquals(10L, s.getTableId());
        assertNotNull(s.getOpenedAt());
    }

    /**
     * La unicidad la garantiza el índice único parcial de V25, NO un chequeo
     * previo (sería check-then-act y dos cajas lo atravesarían). Aquí se
     * verifica que el choque contra la BD se traduzca a un mensaje entendible.
     */
    @Test
    void dosCajasAbriendoLaMismaMesaChocanContraLaBaseYSeExplicaBien() {
        when(tableRepository.findByNumber(5)).thenReturn(Optional.of(mesa(5, true)));
        // Se usa saveAndFlush para que la violación salte DENTRO del try/catch:
        // con save() a secas se postergaría al commit y saldría como 500.
        when(sessionRepository.saveAndFlush(any(TableSession.class)))
                .thenThrow(new DataIntegrityViolationException("ux_table_session_abierta"));

        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> service.abrir(5, "cajero2"));
        assertTrue(e.getMessage().contains("ya tiene una cuenta abierta"));
    }

    @Test
    void noSePuedeAbrirUnaMesaInactiva() {
        when(tableRepository.findByNumber(9)).thenReturn(Optional.of(mesa(9, false)));

        assertThrows(IllegalArgumentException.class, () -> service.abrir(9, "cajero1"));
    }

    @Test
    void cerrarDosVecesLaMismaCuentaNoSePermite() {
        TableSession cerrada = new TableSession();
        cerrada.setStatus(TableSession.CERRADA);
        when(sessionRepository.findById(any())).thenReturn(Optional.of(cerrada));

        assertThrows(IllegalStateException.class,
                () -> service.cerrar(java.util.UUID.randomUUID()));
    }

    /**
     * El cobro es de la SESIÓN: una sola operación pasa TODAS las órdenes de la
     * mesa de `abierta` a `pagado` y cierra la cuenta. Así el cierre de caja las
     * ve como venta normal del día sin tocar su lógica.
     */
    @Test
    void cobrarLaMesaSumaTodoElConsumoYCierraLaCuenta() {
        java.util.UUID id = java.util.UUID.randomUUID();
        TableSession viva = new TableSession();
        viva.setId(id);
        viva.setTableId(7L);
        viva.setStatus(TableSession.ABIERTA);
        when(sessionRepository.findById(id)).thenReturn(Optional.of(viva));

        com.suresell.orders.domain.model.Order o1 = new com.suresell.orders.domain.model.Order();
        o1.setStatus(com.suresell.orders.domain.model.OrderStatus.abierta);
        o1.setTotal(new java.math.BigDecimal("12000"));
        com.suresell.orders.domain.model.Order o2 = new com.suresell.orders.domain.model.Order();
        o2.setStatus(com.suresell.orders.domain.model.OrderStatus.abierta);
        o2.setTotal(new java.math.BigDecimal("8500"));
        when(orderRepository.findByTableSessionId(id)).thenReturn(List.of(o1, o2));
        when(orderRepository.cobrarOrdenesDeLaMesa(id, "CASH")).thenReturn(2);
        when(sessionRepository.save(any(TableSession.class))).thenAnswer(i -> i.getArgument(0));

        var r = service.cobrar(id, "Efectivo");   // etiqueta en español: se normaliza

        assertEquals(new java.math.BigDecimal("20500"), r.get("total"));
        assertEquals(2, r.get("ordenesCobradas"));
        assertEquals("CASH", r.get("paymentMethod"));
        assertEquals(TableSession.CERRADA, viva.getStatus());
    }

    @Test
    void noSePuedeCobrarUnaMesaSinConsumo() {
        java.util.UUID id = java.util.UUID.randomUUID();
        TableSession viva = new TableSession();
        viva.setId(id);
        viva.setStatus(TableSession.ABIERTA);
        when(sessionRepository.findById(id)).thenReturn(Optional.of(viva));
        when(orderRepository.findByTableSessionId(id)).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> service.cobrar(id, "CASH"));
    }

    // ------------------------------------------------------------------
    // DIVISIÓN DE CUENTA entre N comensales.
    //
    // La aritmética se prueba a fondo en DivisionDeCuentaTest. Aquí se prueba
    // lo que ESTE servicio agrega: que lo cobrado se persista, que el residuo
    // quede registrado y que el total de la mesa no se toque.
    // ------------------------------------------------------------------

    /** Arma una mesa con dos rondas que suman $10.000. */
    private java.util.UUID mesaConConsumoDe10000() {
        java.util.UUID id = java.util.UUID.randomUUID();
        TableSession viva = new TableSession();
        viva.setId(id);
        viva.setTableId(7L);
        viva.setStatus(TableSession.ABIERTA);
        when(sessionRepository.findById(id)).thenReturn(Optional.of(viva));

        com.suresell.orders.domain.model.Order o1 = new com.suresell.orders.domain.model.Order();
        o1.setUuidId(java.util.UUID.randomUUID());
        o1.setStatus(com.suresell.orders.domain.model.OrderStatus.abierta);
        o1.setTotal(new java.math.BigDecimal("6000"));
        com.suresell.orders.domain.model.Order o2 = new com.suresell.orders.domain.model.Order();
        o2.setUuidId(java.util.UUID.randomUUID());
        o2.setStatus(com.suresell.orders.domain.model.OrderStatus.abierta);
        o2.setTotal(new java.math.BigDecimal("4000"));
        when(orderRepository.findByTableSessionId(id)).thenReturn(List.of(o1, o2));
        // lenient: la previsualización usa esta misma mesa y NO guarda nada —
        // que no guarde es justamente lo que ese test verifica.
        org.mockito.Mockito.lenient()
                .when(sessionRepository.save(any(TableSession.class)))
                .thenAnswer(i -> i.getArgument(0));
        return id;
    }

    /**
     * EL CASO QUE TRABÓ LA DECISIÓN: $10.000 entre 3. Cada uno paga $3.333 y el
     * peso que sobra lo asume el negocio, nunca el comensal.
     */
    @Test
    void dividirEntreTresCobraDeMenosYRegistraElAjuste() {
        java.util.UUID id = mesaConConsumoDe10000();
        when(orderRepository.cobrarOrdenesDeLaMesa(id, "MIXED")).thenReturn(2);

        var r = service.cobrarDividido(id, 3, List.of("CASH", "CASH", "CARD"), "cajero1");

        assertEquals(new java.math.BigDecimal("10000"), r.get("total"));
        assertEquals(new java.math.BigDecimal("3333"), r.get("porPersona"));
        assertEquals(new java.math.BigDecimal("9999"), r.get("cobrado"));
        assertEquals(new java.math.BigDecimal("1"), r.get("ajusteRedondeoNegocio"));
        assertEquals("MIXED", r.get("paymentMethod"));
    }

    /**
     * EL INVARIANTE, sobre el servicio y no solo sobre la aritmética: lo que se
     * guarda en order_payments + el ajuste tiene que dar el total exacto.
     */
    @Test
    void loGuardadoEnOrderPaymentsMasElAjusteDaElTotalExacto() {
        java.util.UUID id = mesaConConsumoDe10000();
        when(orderRepository.cobrarOrdenesDeLaMesa(id, "MIXED")).thenReturn(2);

        var r = service.cobrarDividido(id, 3, List.of("CASH", "CASH", "CARD"), "cajero1");

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.suresell.orders.domain.model.OrderPayment.class);
        org.mockito.Mockito.verify(orderPaymentRepository, org.mockito.Mockito.atLeastOnce())
                .save(captor.capture());

        java.math.BigDecimal guardado = captor.getAllValues().stream()
                .map(com.suresell.orders.domain.model.OrderPayment::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        assertEquals(0, guardado.add((java.math.BigDecimal) r.get("ajusteRedondeoNegocio"))
                        .compareTo(new java.math.BigDecimal("10000")),
                "Los pagos guardados + el ajuste deben dar el total de la mesa");
        // Nunca un pago en cero: sería basura en la tabla de pagos.
        captor.getAllValues().forEach(p ->
                assertTrue(p.getAmount().compareTo(java.math.BigDecimal.ZERO) > 0));
    }

    /** El total de la mesa es la fuente de verdad: la división no lo toca. */
    @Test
    void dividirNoModificaElTotalDeLasOrdenes() {
        java.util.UUID id = java.util.UUID.randomUUID();
        TableSession viva = new TableSession();
        viva.setId(id);
        viva.setStatus(TableSession.ABIERTA);
        when(sessionRepository.findById(id)).thenReturn(Optional.of(viva));

        com.suresell.orders.domain.model.Order o1 = new com.suresell.orders.domain.model.Order();
        o1.setUuidId(java.util.UUID.randomUUID());
        o1.setStatus(com.suresell.orders.domain.model.OrderStatus.abierta);
        o1.setTotal(new java.math.BigDecimal("7000"));
        when(orderRepository.findByTableSessionId(id)).thenReturn(List.of(o1));
        when(sessionRepository.save(any(TableSession.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.cobrarOrdenesDeLaMesa(id, "MIXED")).thenReturn(1);

        service.cobrarDividido(id, 3, List.of("CASH", "CASH", "CASH"), "cajero1");

        assertEquals(new java.math.BigDecimal("7000"), o1.getTotal());
    }

    /**
     * EL RASTRO DE LA DIVISIÓN, que es la única fuente de verdad del ajuste.
     *
     * No hay copia del monto en la mesa ni en el cierre: el cierre lo suma al
     * vuelo de acá. Se verifica también que quede CON QUÉ pagó cada comensal,
     * que es lo que un auditor necesita para entender el porqué.
     */
    @Test
    void laDivisionDejaSuRastroDeAuditoriaCompleto() {
        java.util.UUID id = mesaConConsumoDe10000();
        when(orderRepository.cobrarOrdenesDeLaMesa(id, "MIXED")).thenReturn(2);

        service.cobrarDividido(id, 3, List.of("CASH", "CASH", "CARD"), "cajero1");

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.suresell.orders.domain.model.TableSessionSplit.class);
        org.mockito.Mockito.verify(splitRepository).save(captor.capture());
        var a = captor.getValue();

        assertEquals(id, a.getTableSessionId());
        assertEquals(3, a.getPersonas());
        assertEquals(0, new java.math.BigDecimal("10000").compareTo(a.getTotal()));
        assertEquals(0, new java.math.BigDecimal("3333").compareTo(a.getPorPersona()));
        assertEquals(0, new java.math.BigDecimal("9999").compareTo(a.getCobrado()));
        assertEquals(0, new java.math.BigDecimal("1").compareTo(a.getAjusteRedondeo()));
        assertEquals("cajero1", a.getCreatedBy());
        assertNotNull(a.getCreatedAt());

        // El invariante, sostenido también por la fila de auditoría.
        assertEquals(0, a.getCobrado().add(a.getAjusteRedondeo()).compareTo(a.getTotal()));

        assertEquals("[{\"persona\":1,\"metodo\":\"CASH\",\"monto\":3333},"
                   + "{\"persona\":2,\"metodo\":\"CASH\",\"monto\":3333},"
                   + "{\"persona\":3,\"metodo\":\"CARD\",\"monto\":3333}]",
                a.getDetallePorPersona(),
                "El detalle por persona es lo que responde POR QUÉ, no solo cuánto");

        // Y la mesa queda cerrada, sin llevar copia del monto.
        var mesa = org.mockito.ArgumentCaptor.forClass(TableSession.class);
        org.mockito.Mockito.verify(sessionRepository).save(mesa.capture());
        assertEquals(TableSession.CERRADA, mesa.getValue().getStatus());
    }

    /**
     * Las órdenes quedan MIXED SIEMPRE, incluso si todos pagan igual. Si no, el
     * cierre las sumaría por su `total` —que es mayor que lo cobrado— y el
     * residuo aparecería como faltante de caja.
     */
    @Test
    void aunPagandoTodosIgualLasOrdenesQuedanMixed() {
        java.util.UUID id = mesaConConsumoDe10000();
        when(orderRepository.cobrarOrdenesDeLaMesa(id, "MIXED")).thenReturn(2);

        var r = service.cobrarDividido(id, 3, List.of("CASH", "CASH", "CASH"), "cajero1");

        assertEquals("MIXED", r.get("paymentMethod"));
        org.mockito.Mockito.verify(orderRepository).cobrarOrdenesDeLaMesa(id, "MIXED");
    }

    @Test
    void unMedioDePagoInvalidoEnLaDivisionSeRechaza() {
        java.util.UUID id = java.util.UUID.randomUUID();
        TableSession viva = new TableSession();
        viva.setId(id);
        viva.setStatus(TableSession.ABIERTA);

        assertThrows(IllegalArgumentException.class,
                () -> service.cobrarDividido(id, 2, List.of("CASH", "BITCOIN"), "cajero1"));
    }

    @Test
    void faltarElMedioDeAlgunaPersonaSeRechazaAntesDeTocarNada() {
        java.util.UUID id = java.util.UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> service.cobrarDividido(id, 3, List.of("CASH", "CARD"), "cajero1"));
        org.mockito.Mockito.verifyNoInteractions(orderPaymentRepository, splitRepository);
    }

    /** La previsualización dice la cifra SIN cobrar: no cierra la cuenta. */
    @Test
    void previsualizarNoCobraNiCierraLaMesa() {
        java.util.UUID id = mesaConConsumoDe10000();

        var r = service.previsualizarDivision(id, 3);

        assertEquals(new java.math.BigDecimal("3333"), r.get("porPersona"));
        assertEquals(new java.math.BigDecimal("1"), r.get("ajusteRedondeoNegocio"));
        org.mockito.Mockito.verifyNoInteractions(orderPaymentRepository, splitRepository);
        org.mockito.Mockito.verify(orderRepository, org.mockito.Mockito.never())
                .cobrarOrdenesDeLaMesa(any(), any());
    }

    /**
     * SE COBRA EXACTAMENTE EL MISMO CONJUNTO QUE SE SUMA.
     *
     * <p>El total se calcula en Java y el cobro se aplica con un UPDATE que
     * filtra por estado `abierta`. Si los dos conjuntos no fueran el mismo, a
     * la mesa se le cobraría una cifra y en la caja quedaría registrada otra.
     * Hoy toda orden de mesa nace `abierta`, así que esto no cambia nada — el
     * test está para que siga sin cambiar nada cuando eso deje de ser cierto.
     */
    @Test
    void unaOrdenQueYaNoEstaAbiertaNoEntraNiEnElTotalNiEnLosPagos() {
        java.util.UUID id = java.util.UUID.randomUUID();
        TableSession viva = new TableSession();
        viva.setId(id);
        viva.setStatus(TableSession.ABIERTA);
        when(sessionRepository.findById(id)).thenReturn(Optional.of(viva));

        com.suresell.orders.domain.model.Order abierta = new com.suresell.orders.domain.model.Order();
        abierta.setUuidId(java.util.UUID.randomUUID());
        abierta.setStatus(com.suresell.orders.domain.model.OrderStatus.abierta);
        abierta.setTotal(new java.math.BigDecimal("10000"));
        // El UPDATE de cobro NO la tocaría: tampoco puede sumar al total.
        com.suresell.orders.domain.model.Order yaPagada = new com.suresell.orders.domain.model.Order();
        yaPagada.setUuidId(java.util.UUID.randomUUID());
        yaPagada.setStatus(com.suresell.orders.domain.model.OrderStatus.pagado);
        yaPagada.setTotal(new java.math.BigDecimal("99999"));

        when(orderRepository.findByTableSessionId(id)).thenReturn(List.of(abierta, yaPagada));
        when(sessionRepository.save(any(TableSession.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.cobrarOrdenesDeLaMesa(id, "MIXED")).thenReturn(1);

        var r = service.cobrarDividido(id, 3, List.of("CASH", "CASH", "CARD"), "cajero1");

        assertEquals(new java.math.BigDecimal("10000"), r.get("total"),
                "La orden que el UPDATE no toca no puede sumar al total cobrado");

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.suresell.orders.domain.model.OrderPayment.class);
        org.mockito.Mockito.verify(orderPaymentRepository, org.mockito.Mockito.atLeastOnce())
                .save(captor.capture());
        captor.getAllValues().forEach(p -> assertEquals(abierta.getUuidId(), p.getOrderUuidId(),
                "Un pago colgado de una orden que no queda MIXED se perdería en el cierre"));
    }

    /** Lo que consulta el cierre de caja para bloquearse. */
    @Test
    void pendientesDeCobroDevuelveLasCuentasVivas() {
        TableSession viva = new TableSession();
        viva.setStatus(TableSession.ABIERTA);
        when(sessionRepository.findVivas()).thenReturn(List.of(viva));

        assertEquals(1, service.pendientesDeCobro().size());
    }
}
