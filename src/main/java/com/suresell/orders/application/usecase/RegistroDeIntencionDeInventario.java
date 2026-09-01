package com.suresell.orders.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderItem;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Escribe la intención de descontar inventario, en la MISMA transacción que la
 * venta.
 *
 * <h2>Por qué no es una llamada al servicio de inventario</h2>
 *
 * Porque una llamada entre servicios dentro de una venta solo tiene dos
 * finales, y los dos son malos: o se cae la venta cuando el otro servicio no
 * responde, o se traga el error. Este proyecto ya pagó el segundo:
 * {@code ExecuteDailyClosureUseCase} llamaba a {@code /qr-payments} sin JWT,
 * recibió 401 durante <b>tres semanas</b> y los cierres se cuadraron con el
 * valor manual del cajero sin que quedara rastro en ninguna parte.
 *
 * <p>Aquí el fallo tiene cuerpo: una fila {@code PENDIENTE} que envejece, que
 * se puede consultar y alarmar. Esa es la diferencia, no la latencia.
 *
 * <h2>La decisión incómoda, dicha en voz alta</h2>
 *
 * Este INSERT va dentro de la transacción de la venta. Si falla, <b>la venta
 * falla</b>. Se eligió a conciencia: una venta que se registra sin su intención
 * deja el inventario mintiendo para siempre, y nadie lo nota hasta el
 * inventario físico de dentro de tres meses.
 *
 * <p>Para que esa elección sea defendible, el INSERT es lo más simple que
 * puede ser: una fila, con valores que ya están calculados y en memoria, sin
 * llamadas a nada. Y hay dos salidas si aun así molestara: el interruptor
 * {@code inventario.intenciones.enabled} — que nace <b>apagado</b>, así que
 * esto se despliega a oscuras — y el rebote por idempotencia, que se trata
 * como éxito y no como error.
 */
@Service
public class RegistroDeIntencionDeInventario {

    private static final Logger log =
            LoggerFactory.getLogger(RegistroDeIntencionDeInventario.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final boolean habilitado;

    public RegistroDeIntencionDeInventario(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            @Value("${inventario.intenciones.enabled:false}") boolean habilitado) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.habilitado = habilitado;
    }

    /**
     * Registra la intención correspondiente a una venta ya guardada.
     *
     * @param orden la orden persistida, con su {@code idOrder} numérico ya asignado
     * @param items las líneas vendidas
     */
    public void registrar(Order orden, List<OrderItem> items) {
        if (!habilitado) {
            return;
        }
        if (items == null || items.isEmpty()) {
            // Una venta sin líneas no descuenta nada. No es un error: es una
            // orden vacía, y ya hay otras validaciones para eso.
            return;
        }

        String lineas;
        try {
            lineas = objectMapper.writeValueAsString(items.stream()
                    .map(i -> Map.of(
                            "producto_id", String.valueOf(i.getProductId()),
                            "cantidad", i.getQuantity()))
                    .toList());
        } catch (JsonProcessingException e) {
            // Serializar un mapa de dos claves no puede fallar por datos; si
            // falla, es un defecto del código y hay que verlo, no tragarlo.
            throw new IllegalStateException(
                    "no se pudo serializar las líneas de la orden " + orden.getIdOrder(), e);
        }

        // La clave de idempotencia lleva la orden y nada más: una venta produce
        // UNA intención. Si el POS reenvía la orden, la orden ya rebota antes
        // por su propia idempotencia; si algo llegara hasta aquí dos veces, el
        // índice único lo para.
        String clave = "orden-" + orden.getIdOrder();

        // `ocurrido_en` de la orden si existe -- una venta tomada sin cobertura
        // trae la hora del dispositivo -- y si no, el instante del registro.
        // Que el movimiento nazca con la fecha del HECHO y no con la del
        // procesamiento es lo que evita descontar inventario en el día
        // equivocado.
        OffsetDateTime ocurrido = orden.getOcurridoEn() != null
                ? orden.getOcurridoEn()
                : OffsetDateTime.now();

        try {
            jdbc.update("""
                    INSERT INTO public.inventario_intenciones
                        (orden_id, orden_uuid, ocurrido_en, usuario_id, terminal_id,
                         lineas, idempotency_key)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)""",
                    orden.getIdOrder(),
                    orden.getUuidId(),
                    java.sql.Timestamp.from(ocurrido.toInstant()),
                    orden.getCreatedBy() == null ? null : String.valueOf(orden.getCreatedBy()),
                    orden.getTerminalId() == null ? null : orden.getTerminalId().toString(),
                    lineas,
                    clave);
        } catch (DuplicateKeyException e) {
            // Ya estaba registrada. Es el resultado que se buscaba, no un
            // fallo: reprocesar no debe duplicar (regla 12) y tampoco debe
            // romper la venta.
            log.info("La intención de inventario de la orden {} ya estaba registrada", clave);
        }
    }
}
