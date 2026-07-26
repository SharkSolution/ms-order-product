package com.suresell.orders.application.usecase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.suresell.orders.domain.model.Site;
import com.suresell.orders.infrastructure.persistence.SiteRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Modo de POS por sede (Inc. 1 del modo Restaurante). */
@ExtendWith(MockitoExtension.class)
class SiteServiceTest {

    @Mock
    private SiteRepository repository;

    private SiteService service;

    @BeforeEach
    void setUp() {
        service = new SiteService(repository);
    }

    private Site sede(String modo) {
        Site s = new Site();
        s.setId(1L);
        s.setName("Principal");
        s.setCode("PRINCIPAL");
        s.setPosMode(modo);
        s.setIsDefault(true);
        s.setActive(true);
        return s;
    }

    /**
     * Un negocio SIN sede configurada tiene que comportarse como siempre. Es la
     * garantía de que esta migración no cambia nada para quien ya opera.
     */
    @Test
    void sinSedeConfiguradaElModoEsPlazoleta() {
        when(repository.findFirstByIsDefaultTrue()).thenReturn(Optional.empty());

        assertEquals(Site.MODO_PLAZOLETA, service.modoEfectivo());
        assertFalse(service.enModoRestaurante());
    }

    @Test
    void elModoSaleDeLaSedePorDefecto() {
        when(repository.findFirstByIsDefaultTrue())
                .thenReturn(Optional.of(sede(Site.MODO_RESTAURANTE)));

        assertEquals(Site.MODO_RESTAURANTE, service.modoEfectivo());
        assertTrue(service.enModoRestaurante());
    }

    @Test
    void cambiarModoAceptaSoloLosDosValores() {
        when(repository.findById(1L)).thenReturn(Optional.of(sede(Site.MODO_PLAZOLETA)));
        when(repository.save(any(Site.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals(Site.MODO_RESTAURANTE,
                service.cambiarModo(1L, "restaurante").getPosMode());

        assertThrows(IllegalArgumentException.class, () -> service.cambiarModo(1L, "BUFFET"));
    }

    /** El código de la sede se normaliza: es la base de la numeración futura. */
    @Test
    void crearNormalizaElCodigoDeLaSede() {
        when(repository.findByCode("SEDE-CHICO")).thenReturn(Optional.empty());
        when(repository.findFirstByIsDefaultTrue()).thenReturn(Optional.of(sede(Site.MODO_PLAZOLETA)));
        when(repository.save(any(Site.class))).thenAnswer(i -> i.getArgument(0));

        Site creada = service.crear("Sede Chicó", "sede chicó", Site.MODO_RESTAURANTE);

        // Sin tildes y sin guiones colgando: este código va en la numeración.
        assertEquals("SEDE-CHICO", creada.getCode());
        assertEquals(Site.MODO_RESTAURANTE, creada.getPosMode());
        // No es la primera sede, así que no debe quedar como la de por defecto.
        assertFalse(creada.getIsDefault());
    }
}
