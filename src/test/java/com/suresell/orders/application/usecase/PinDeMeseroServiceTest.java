package com.suresell.orders.application.usecase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.suresell.orders.domain.model.Waiter;
import com.suresell.orders.infrastructure.persistence.WaiterRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * PIN DEL MESERO (#20).
 *
 * <p>Lo que protege: que cualquiera con el teléfono en la mano cierre el turno
 * de otro declarando el efectivo que quiera. El faltante se lo cobran al mesero.
 */
class PinDeMeseroServiceTest {

    private WaiterRepository repositorio;
    private PinDeMeseroService servicio;

    @BeforeEach
    void setUp() {
        repositorio = mock(WaiterRepository.class);
        // Coste bajo a propósito: BCrypt por defecto hace estos tests lentísimos.
        servicio = new PinDeMeseroService(repositorio, new BCryptPasswordEncoder(4));
        when(repositorio.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Waiter mesero(Long id) {
        Waiter w = new Waiter();
        w.setId(id);
        w.setName("Angie");
        w.setActive(true);
        when(repositorio.findById(id)).thenReturn(Optional.of(w));
        return w;
    }

    private Waiter meseroConPin(Long id, String pin) {
        Waiter w = mesero(id);
        servicio.configurar(id, null, pin);
        return w;
    }

    // ------------------------------------------------------------------
    // Que se pueda desplegar sin dejar a nadie afuera.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("un mesero SIN clave configurada entra directo, como siempre")
    void sinPinSeEntraDirecto() {
        Waiter w = mesero(1L);
        assertFalse(w.tienePin());
        assertDoesNotThrow(() -> servicio.verificar(w, null));
        assertDoesNotThrow(() -> servicio.verificar(w, "0000"));
    }

    // ------------------------------------------------------------------
    // Con clave puesta.
    // ------------------------------------------------------------------

    @Test
    void conLaClaveCorrectaEntra() {
        Waiter w = meseroConPin(2L, "1234");
        assertTrue(w.tienePin());
        assertDoesNotThrow(() -> servicio.verificar(w, "1234"));
    }

    @Test
    void conLaClaveEquivocadaNoEntra() {
        Waiter w = meseroConPin(3L, "1234");
        assertThrows(PinDeMeseroService.PinIncorrectoException.class,
                () -> servicio.verificar(w, "9999"));
    }

    @Test
    @DisplayName("teniendo clave, NO mandarla tampoco entra")
    void conClavePuestaNoAlcanzaConOmitirla() {
        Waiter w = meseroConPin(4L, "1234");
        assertThrows(PinDeMeseroService.PinIncorrectoException.class,
                () -> servicio.verificar(w, null));
        assertThrows(PinDeMeseroService.PinIncorrectoException.class,
                () -> servicio.verificar(w, "  "));
    }

    @Test
    @DisplayName("el PIN NUNCA se guarda en claro")
    void elPinSeGuardaHasheado() {
        Waiter w = meseroConPin(5L, "1234");
        assertNotEquals("1234", w.getPinHash());
        assertTrue(w.getPinHash().startsWith("$2"), "Tiene que ser un hash BCrypt");
        assertFalse(w.getPinHash().contains("1234"));
    }

    // ------------------------------------------------------------------
    // Configurarlo: lo hace el mesero, no el administrador.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("la primera vez no hace falta clave anterior: no hay ninguna")
    void laPrimeraVezNoPideAnterior() {
        mesero(6L);
        assertDoesNotThrow(() -> servicio.configurar(6L, null, "4321"));
    }

    @Test
    @DisplayName("cambiarla exige saber la anterior")
    void cambiarlaExigeLaAnterior() {
        meseroConPin(7L, "1111");

        assertThrows(PinDeMeseroService.PinIncorrectoException.class,
                () -> servicio.configurar(7L, "0000", "2222"),
                "Sin esto, cualquiera que agarre el teléfono le cambia la clave a otro "
                        + "y lo deja por fuera de su propio turno");

        assertDoesNotThrow(() -> servicio.configurar(7L, "1111", "2222"));
    }

    @Test
    @DisplayName("solo se aceptan 4 dígitos")
    void soloCuatroDigitos() {
        mesero(8L);
        for (String malo : new String[] {"123", "12345", "abcd", "12a4", "", null, "  "}) {
            assertThrows(IllegalArgumentException.class,
                    () -> servicio.configurar(8L, null, malo),
                    "Debería rechazar: " + malo);
        }
        assertDoesNotThrow(() -> servicio.configurar(8L, null, "0000"));
    }

    // ------------------------------------------------------------------
    // Lo que hace que 4 dígitos alcancen.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("tras varios intentos fallidos se bloquea y deja de responder")
    void demasiadosIntentosBloquean() {
        Waiter w = meseroConPin(9L, "1234");

        for (int i = 0; i < PinDeMeseroService.INTENTOS_PERMITIDOS; i++) {
            assertThrows(PinDeMeseroService.PinIncorrectoException.class,
                    () -> servicio.verificar(w, "0000"));
        }
        // A partir de acá ni la clave BUENA pasa: es lo que hace inviable
        // probar las 10.000 combinaciones.
        var e = assertThrows(PinDeMeseroService.DemasiadosIntentosException.class,
                () -> servicio.verificar(w, "1234"));
        assertTrue(e.getMessage().contains("Esperá"));
    }

    @Test
    @DisplayName("acertar borra los fallos: un error de dedo no acumula")
    void acertarLimpiaElContador() {
        Waiter w = meseroConPin(10L, "1234");

        for (int i = 0; i < PinDeMeseroService.INTENTOS_PERMITIDOS - 1; i++) {
            assertThrows(PinDeMeseroService.PinIncorrectoException.class,
                    () -> servicio.verificar(w, "0000"));
        }
        assertDoesNotThrow(() -> servicio.verificar(w, "1234"));

        // El contador quedó en cero: se puede volver a fallar sin bloquearse.
        assertThrows(PinDeMeseroService.PinIncorrectoException.class,
                () -> servicio.verificar(w, "0000"));
        assertDoesNotThrow(() -> servicio.verificar(w, "1234"));
    }

    // ------------------------------------------------------------------
    // El olvido, que es lo que de verdad pasa en un local.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("el administrador puede quitarla si el mesero la olvidó")
    void elAdministradorPuedeQuitarla() {
        Waiter w = meseroConPin(11L, "1234");

        servicio.quitar(11L);

        assertFalse(w.tienePin());
        assertDoesNotThrow(() -> servicio.verificar(w, null),
                "Un mesero que olvidó la clave no puede quedarse sin trabajar");
    }

    @Test
    void quitarlaTambienDesbloquea() {
        Waiter w = meseroConPin(12L, "1234");
        for (int i = 0; i < PinDeMeseroService.INTENTOS_PERMITIDOS; i++) {
            assertThrows(PinDeMeseroService.PinIncorrectoException.class,
                    () -> servicio.verificar(w, "0000"));
        }
        servicio.quitar(12L);
        assertDoesNotThrow(() -> servicio.verificar(w, null));
    }

    @Test
    void unMeseroQueNoExisteEsUnError() {
        when(repositorio.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> servicio.configurar(99L, null, "1234"));
        assertThrows(IllegalArgumentException.class, () -> servicio.quitar(99L));
    }
}
