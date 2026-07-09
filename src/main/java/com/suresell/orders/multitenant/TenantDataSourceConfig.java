package com.suresell.orders.multitenant;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * Envuelve el DataSource principal de la aplicación en un {@link TenantAwareDataSource}
 * SOLO en el perfil `cloud`. Así RLS recibe `app.tenant_id` en cada conexión.
 *
 * Solo toca el bean `dataSource` (el de la app, que conecta como app_user). El
 * datasource de Flyway (usuario privilegiado, para migraciones) queda intacto.
 * Aditivo: en el arranque local-first por defecto este post-procesador no existe.
 */
@Configuration
@Profile("cloud")
public class TenantDataSourceConfig implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if ("dataSource".equals(beanName)
                && bean instanceof DataSource ds
                && !(bean instanceof TenantAwareDataSource)) {
            return new TenantAwareDataSource(ds);
        }
        return bean;
    }
}
