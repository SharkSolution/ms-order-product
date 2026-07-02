package com.suresell.orders.multitenant;

import org.flywaydb.core.Flyway;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifica que JPA/Hibernate respeta RLS cuando el tenant se fija por transacción
 * con `set_config('app.tenant_id', ?, true)` (LOCAL, scope transacción → seguro
 * con pool de conexiones). Es la mecánica que usará el servicio cloud multi-tenant.
 *
 * Se conecta como `app_user` (no superusuario) para que RLS aplique.
 * Ver docs/40-multitenant.md.
 */
@Testcontainers
class TenantRlsJpaTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    static SessionFactory sf;

    @BeforeAll
    static void setup() {
        // Migraciones como superusuario (crea esquema + rol app_user).
        Flyway.configure()
                .dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        // Hibernate conectado como app_user (RLS aplica).
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", PG.getJdbcUrl())
                .applySetting("hibernate.connection.username", "app_user")
                .applySetting("hibernate.connection.password", "app_pw")
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        sf = new MetadataSources(registry)
                .addAnnotatedClass(TenantOrderEntity.class)
                .buildMetadata()
                .buildSessionFactory();
    }

    @AfterAll
    static void tearDown() {
        if (sf != null) sf.close();
    }

    private void setTenant(Session s, String tenant) {
        s.createNativeQuery("SELECT set_config('app.tenant_id', :t, true)", String.class)
                .setParameter("t", tenant)
                .getSingleResult();
    }

    private void saveUnder(String tenant, long idOrder) {
        try (Session s = sf.openSession()) {
            s.getTransaction().begin();
            setTenant(s, tenant);
            s.persist(new TenantOrderEntity(UUID.randomUUID(), tenant, idOrder, new BigDecimal("10000")));
            s.getTransaction().commit();
        }
    }

    private long countUnder(String tenant) {
        try (Session s = sf.openSession()) {
            s.getTransaction().begin();
            setTenant(s, tenant);
            long c = s.createQuery("select count(o) from TenantOrderEntity o", Long.class).getSingleResult();
            s.getTransaction().commit();
            return c;
        }
    }

    @Test
    void jpaRespetaRlsPorTenant() {
        saveUnder("jpa-a", 1);
        saveUnder("jpa-a", 2);
        saveUnder("jpa-b", 1);

        assertEquals(2L, countUnder("jpa-a"), "Tenant A ve solo sus 2 órdenes vía JPA");
        assertEquals(1L, countUnder("jpa-b"), "Tenant B ve solo su orden vía JPA");
    }

    @Test
    void sinTenantFijadoJpaNoVeNada() {
        saveUnder("jpa-c", 9);
        try (Session s = sf.openSession()) {
            s.getTransaction().begin();
            long c = s.createQuery("select count(o) from TenantOrderEntity o", Long.class).getSingleResult();
            s.getTransaction().commit();
            assertEquals(0L, c, "Sin app.tenant_id fijado, RLS oculta todo también en JPA");
        }
    }
}
