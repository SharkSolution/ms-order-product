package com.suresell.orders.application.usecase;

import com.suresell.orders.domain.model.Waiter;
import com.suresell.orders.infrastructure.persistence.WaiterRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * PIN DEL MESERO (PLAN-UX-MESEROS #20).
 *
 * <p>La app no verificaba nada: se tocaba un nombre y se operaba como esa
 * persona, incluido cerrar su turno declarando cuánto efectivo hay. El faltante
 * se lo cobran al mesero, no a quien tocó el nombre.
 *
 * <p><b>El PIN lo pone el propio mesero</b>, no el administrador. Una clave que
 * otro conoce no protege al mesero de nada, que es exactamente el problema.
 *
 * <p>Cuatro dígitos es corto a propósito: tiene que poder teclearse con una
 * bandeja en la otra mano. Lo que lo hace suficiente no es la longitud sino que
 * el hash no se pueda leer y que los intentos estén limitados — probar las
 * 10.000 combinaciones con esta ventana toma más de una jornada, y el mesero ve
 * su turno bloqueado mucho antes.
 */
@Service
public class PinDeMeseroService {

    /** Cuatro dígitos, ni más ni menos. */
    private static final java.util.regex.Pattern FORMATO =
            java.util.regex.Pattern.compile("^\\d{4}$");

    /** Intentos fallidos seguidos antes de bloquear. */
    static final int INTENTOS_PERMITIDOS = 5;

    /** Cuánto dura el bloqueo. */
    static final Duration ESPERA = Duration.ofMinutes(1);

    private final WaiterRepository waiterRepository;
    private final BCryptPasswordEncoder encoder;

    /**
     * Intentos fallidos por mesero.
     *
     * <p>En memoria a propósito: un reinicio del servicio limpia los bloqueos, y
     * eso está bien — el enemigo es el compañero de turno, no una botnet. Una
     * tabla para esto sería costo de esquema y de sincronización sin ganar nada.
     */
    private final Map<Long, Intentos> fallidos = new ConcurrentHashMap<>();

    private record Intentos(int cuantos, Instant ultimo) {
    }

    // @Autowired explícito: hay dos constructores —el de Spring y el que usan
    // los tests con un coste de BCrypt bajo— y sin la marca Spring no sabe cuál.
    @org.springframework.beans.factory.annotation.Autowired
    public PinDeMeseroService(WaiterRepository waiterRepository) {
        this(waiterRepository, new BCryptPasswordEncoder());
    }

    PinDeMeseroService(WaiterRepository waiterRepository, BCryptPasswordEncoder encoder) {
        this.waiterRepository = waiterRepository;
        this.encoder = encoder;
    }

    /** El mesero se equivocó demasiadas veces seguidas. */
    public static class DemasiadosIntentosException extends RuntimeException {
        public DemasiadosIntentosException(String mensaje) {
            super(mensaje);
        }
    }

    /** El PIN no coincide. */
    public static class PinIncorrectoException extends RuntimeException {
        public PinIncorrectoException(String mensaje) {
            super(mensaje);
        }
    }

    /**
     * Comprueba el PIN antes de dejar entrar.
     *
     * <p>Si el mesero todavía no configuró uno, se entra sin PIN: es el
     * comportamiento de siempre y hace que la feature se pueda desplegar sin
     * dejar a nadie afuera.
     */
    public void verificar(Waiter mesero, String pin) {
        if (!mesero.tienePin()) {
            return;
        }
        exigirQueNoEsteBloqueado(mesero.getId());

        if (pin == null || pin.isBlank()) {
            registrarFallo(mesero.getId());
            throw new PinIncorrectoException("Este mesero tiene clave. Escribila para entrar.");
        }
        if (!encoder.matches(pin.trim(), mesero.getPinHash())) {
            registrarFallo(mesero.getId());
            throw new PinIncorrectoException("Clave incorrecta.");
        }
        fallidos.remove(mesero.getId());
    }

    /**
     * Configura o cambia el PIN.
     *
     * <p>Si ya tenía uno, hay que saber el anterior. Sin eso, cualquiera que
     * agarre el teléfono con la lista abierta podría cambiarle la clave a otro
     * y dejarlo por fuera de su propio turno.
     */
    public Waiter configurar(Long meseroId, String pinActual, String pinNuevo) {
        Waiter mesero = waiterRepository.findById(meseroId)
                .orElseThrow(() -> new IllegalArgumentException("Mesero no encontrado: " + meseroId));

        if (mesero.tienePin()) {
            verificar(mesero, pinActual);
        }
        if (pinNuevo == null || !FORMATO.matcher(pinNuevo.trim()).matches()) {
            throw new IllegalArgumentException("La clave debe ser de 4 dígitos.");
        }
        mesero.setPinHash(encoder.encode(pinNuevo.trim()));
        fallidos.remove(meseroId);
        return waiterRepository.save(mesero);
    }

    /**
     * Quita el PIN. Solo el administrador, y para un caso concreto: el mesero
     * lo olvidó y no puede trabajar.
     */
    public Waiter quitar(Long meseroId) {
        Waiter mesero = waiterRepository.findById(meseroId)
                .orElseThrow(() -> new IllegalArgumentException("Mesero no encontrado: " + meseroId));
        mesero.setPinHash(null);
        fallidos.remove(meseroId);
        return waiterRepository.save(mesero);
    }

    private void exigirQueNoEsteBloqueado(Long meseroId) {
        Intentos i = fallidos.get(meseroId);
        if (i == null || i.cuantos() < INTENTOS_PERMITIDOS) {
            return;
        }
        Duration transcurrido = Duration.between(i.ultimo(), Instant.now());
        if (transcurrido.compareTo(ESPERA) < 0) {
            long faltan = ESPERA.minus(transcurrido).toSeconds() + 1;
            throw new DemasiadosIntentosException(
                    "Demasiados intentos. Esperá " + faltan + " segundos.");
        }
        // Cumplida la espera, se arranca de cero.
        fallidos.remove(meseroId);
    }

    private void registrarFallo(Long meseroId) {
        fallidos.merge(meseroId, new Intentos(1, Instant.now()),
                (viejo, nuevo) -> new Intentos(viejo.cuantos() + 1, nuevo.ultimo()));
    }
}
