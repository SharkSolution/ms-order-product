package com.suresell.orders.application.usecase;

import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Evalúa si la fecha que declara un dispositivo es creíble — y <b>nunca rechaza
 * la venta por ella</b>.
 *
 * <h3>La deriva es un dato, no un error</h3>
 *
 * El POS corre en el equipo del local. Un equipo con la pila de la BIOS agotada,
 * o recién reinstalado, tiene el reloj mal; eso es ordinario en el retail. Si el
 * servidor rechazara esas ventas, un negocio dejaría de facturar por un problema
 * de hardware que no sabe que tiene.
 *
 * <p>Así que la fecha se acepta siempre y se <b>marca</b> cuando no es creíble.
 * Marcar y seguir es lo que permite, más adelante, separar las series limpias de
 * las sucias sin haber perdido ninguna venta por el camino.
 *
 * <h3>Los dos casos que se marcan, y el que NO</h3>
 *
 * <ul>
 *   <li><b>Reloj adelantado</b> — {@code ocurrido_en} posterior a
 *       {@code registrado_en}. Imposible físicamente: nada puede ocurrir después
 *       de que el servidor lo supo. Se marca siempre, sin tolerancia más allá de
 *       un pequeño margen por desincronización normal.</li>
 *   <li><b>Reloj muy atrasado</b> — más de {@link #maximoDiasDeAtraso} días
 *       antes. Se marca.</li>
 *   <li><b>Atraso dentro de la ventana</b> — <b>NO se marca.</b> Es el caso
 *       normal del local-first: una venta que estuvo días en la cola porque no
 *       había internet. Marcarla sería declarar sospechosa la operación que este
 *       modelo existe para poder registrar.</li>
 * </ul>
 */
@Log4j2
@Component
public class CorduraDelRelojDelDispositivo {

    /**
     * Cuántos días puede llevar una venta en la cola sin que resulte sospechosa.
     * Configurable porque depende del negocio: un local con internet estable no
     * debería pasar de horas; uno rural puede pasar días.
     */
    @Value("${orders.reloj.maximo-dias-de-atraso:7}")
    private int maximoDiasDeAtraso = 7;

    /**
     * Margen para relojes adelantados. No es tolerancia al fraude: es que un
     * dispositivo bien sincronizado puede ir unos segundos por delante del
     * servidor sin que eso signifique nada.
     */
    @Value("${orders.reloj.margen-de-adelanto-segundos:120}")
    private long margenDeAdelantoSegundos = 120;

    /** Qué pasó con la fecha del dispositivo. Enum CERRADO, sin valor "otro". */
    public enum Veredicto {
        /** No mandó fecha. No es un problema: es un cliente viejo. */
        sin_fecha,
        /** Creíble. Incluye el atraso normal de una venta que esperó en la cola. */
        creible,
        /** Posterior al momento en que el servidor la recibió. Imposible. */
        adelantado,
        /** Más atrasado de lo que cualquier cola justifica. */
        muy_atrasado
    }

    public Veredicto evaluar(OffsetDateTime ocurridoEn, OffsetDateTime registradoEn) {
        if (ocurridoEn == null) {
            return Veredicto.sin_fecha;
        }
        if (ocurridoEn.isAfter(registradoEn.plusSeconds(margenDeAdelantoSegundos))) {
            return Veredicto.adelantado;
        }
        if (ocurridoEn.isBefore(registradoEn.minus(Duration.ofDays(maximoDiasDeAtraso)))) {
            return Veredicto.muy_atrasado;
        }
        return Veredicto.creible;
    }

    /**
     * Evalúa y deja constancia. Devuelve el veredicto para que el llamador lo
     * guarde; el log es un extra, no el registro — esa es la lección de
     * `FALLBACK-SILENCIOSO.md`: lo que no queda en el dato, no ocurrió.
     */
    public Veredicto evaluarYRegistrar(OffsetDateTime ocurridoEn, OffsetDateTime registradoEn,
                                       java.util.UUID terminalId) {
        Veredicto veredicto = evaluar(ocurridoEn, registradoEn);
        if (veredicto == Veredicto.adelantado || veredicto == Veredicto.muy_atrasado) {
            log.warn("Reloj del terminal {} fuera de rango ({}): ocurrido_en={} registrado_en={}. "
                            + "La venta se acepta y queda marcada.",
                    terminalId, veredicto, ocurridoEn, registradoEn);
        }
        return veredicto;
    }

    public int getMaximoDiasDeAtraso() {
        return maximoDiasDeAtraso;
    }

    /**
     * Fija la ventana sin pasar por la configuración de Spring. Público para que
     * los tests puedan comprobar que la ventana de verdad es configurable —
     * probarlo con el valor por defecto no demostraría nada.
     */
    public void configurar(int dias, long margenSegundos) {
        this.maximoDiasDeAtraso = dias;
        this.margenDeAdelantoSegundos = margenSegundos;
    }
}
