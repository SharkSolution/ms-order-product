package com.suresell.orders.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderDeliveryTracking;
import com.suresell.orders.domain.model.OrderStatus;
import com.suresell.orders.domain.model.RestaurantTable;
import com.suresell.orders.domain.model.TableSession;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * N3/#1 y #2 — Las dos consultas nuevas de cocina, ejecutadas DE VERDAD contra
 * Postgres. Los tests unitarios las mockean, así que no dirían nada si la JPQL
 * estuviera mal traducida o si los tipos del {@code Object[]} no cuadraran.
 *
 * <p>Sin Flyway: el esquema lo genera Hibernate desde las entidades. Aquí se
 * verifica el comportamiento de las consultas, no las migraciones.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "sync.cloud.enabled=false"
})
class CocinaMesaYReaperturaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TableSessionRepository tableSessionRepository;

    @Autowired
    private OrderDeliveryTrackingRepository trackingRepository;

    @Autowired
    private EntityManager em;

    /** N3/#1 — la comanda debe poder decir SU mesa, no la del pager. */
    @Test
    void findMesaPorSesionDevuelveElNumeroDeCadaMesa() {
        UUID sesion23 = sembrarMesaConCuenta(23, "Terraza");
        UUID sesion7 = sembrarMesaConCuenta(7, null);
        em.flush();
        em.clear();

        List<Object[]> filas = tableSessionRepository.findMesaPorSesion(List.of(sesion23, sesion7));

        assertEquals(2, filas.size());
        // Los tipos importan: el mapeo hace cast a (UUID, Integer, String).
        for (Object[] fila : filas) {
            UUID id = (UUID) fila[0];
            Integer numero = (Integer) fila[1];
            String etiqueta = (String) fila[2];
            if (id.equals(sesion23)) {
                assertEquals(23, numero);
                assertEquals("Terraza", etiqueta);
            } else {
                assertEquals(7, numero);
                assertEquals(null, etiqueta);
            }
        }
    }

    /** N3/#2 — la ronda nueva devuelve la comanda a la cola de cocina. */
    @Test
    void reabrirParaCocinaDevuelveLaComandaEntregadaALaCola() {
        UUID uuidOrden = sembrarOrdenEntregada();
        em.flush();
        em.clear();

        int reabiertas = trackingRepository.reabrirParaCocina(uuidOrden);

        assertEquals(1, reabiertas);
        em.clear();
        OrderDeliveryTracking t = trackingRepository.findById(uuidOrden).orElseThrow();
        assertFalse(t.getDelivered());
        assertTrue(t.getPreparationDurationSeconds() == null);
    }

    /** Reabrir algo que YA estaba en la cola no debe contar como cambio. */
    @Test
    void reabrirEsIdempotenteSobreUnaComandaQueSigueEnLaCola() {
        UUID uuidOrden = sembrarOrdenEntregada();
        em.flush();
        em.clear();

        assertEquals(1, trackingRepository.reabrirParaCocina(uuidOrden));
        em.clear();
        assertEquals(0, trackingRepository.reabrirParaCocina(uuidOrden));
    }

    // ------------------------------------------------------------------

    private UUID sembrarMesaConCuenta(int numero, String etiqueta) {
        RestaurantTable mesa = new RestaurantTable();
        mesa.setTenantId("t1");
        mesa.setNumber(numero);
        mesa.setLabel(etiqueta);
        mesa.setActive(true);
        em.persist(mesa);
        em.flush();

        TableSession sesion = new TableSession();
        sesion.setTenantId("t1");
        sesion.setTableId(mesa.getId());
        sesion.setStatus("ABIERTA");
        sesion.setOpenedAt(LocalDateTime.now());
        em.persist(sesion);
        return sesion.getId();
    }

    private UUID sembrarOrdenEntregada() {
        Order orden = new Order();
        orden.setUuidId(UUID.randomUUID());
        orden.setTenantId("t1");
        orden.setPagerColor("AMARILLO");
        orden.setPagerNumber("1");
        orden.setPaymentMethod("CASH");
        orden.setStatus(OrderStatus.abierta);
        orden.setCreatedAt(LocalDateTime.now());
        orden.setSubtotal(BigDecimal.TEN);
        orden.setTotal(BigDecimal.TEN);
        orden.setSynced(true);
        em.persist(orden);
        em.flush();

        OrderDeliveryTracking tracking = new OrderDeliveryTracking();
        tracking.setOrder(orden);
        tracking.setOrderId(orden.getIdOrder());
        tracking.setOrderIdUuid(orden.getUuidId());
        tracking.setTenantId("t1");
        tracking.setDelivered(true);
        tracking.setPagerReturned(false);
        tracking.setPreparationDurationSeconds(300);
        em.persist(tracking);
        return orden.getUuidId();
    }
}
