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

    private TableSessionService service;

    @BeforeEach
    void setUp() {
        service = new TableSessionService(sessionRepository, tableRepository);
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

    /** Lo que consulta el cierre de caja para bloquearse. */
    @Test
    void pendientesDeCobroDevuelveLasCuentasVivas() {
        TableSession viva = new TableSession();
        viva.setStatus(TableSession.ABIERTA);
        when(sessionRepository.findVivas()).thenReturn(List.of(viva));

        assertEquals(1, service.pendientesDeCobro().size());
    }
}
