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

    private TableSessionService service;

    @BeforeEach
    void setUp() {
        service = new TableSessionService(sessionRepository, tableRepository, orderRepository);
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
        o1.setTotal(new java.math.BigDecimal("12000"));
        com.suresell.orders.domain.model.Order o2 = new com.suresell.orders.domain.model.Order();
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

    /** Lo que consulta el cierre de caja para bloquearse. */
    @Test
    void pendientesDeCobroDevuelveLasCuentasVivas() {
        TableSession viva = new TableSession();
        viva.setStatus(TableSession.ABIERTA);
        when(sessionRepository.findVivas()).thenReturn(List.of(viva));

        assertEquals(1, service.pendientesDeCobro().size());
    }
}
