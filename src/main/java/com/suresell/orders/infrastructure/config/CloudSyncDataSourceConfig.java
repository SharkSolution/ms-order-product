package com.suresell.orders.infrastructure.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class CloudSyncDataSourceConfig {

    @Bean(name = "cloudDataSourceProperties")
    @ConfigurationProperties(prefix = "sync.cloud.datasource")
    @ConditionalOnProperty(prefix = "sync.cloud", name = "enabled", havingValue = "true")
    public DataSourceProperties cloudDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "cloudDataSource")
    @ConditionalOnProperty(prefix = "sync.cloud", name = "enabled", havingValue = "true")
    public DataSource cloudDataSource(@Qualifier("cloudDataSourceProperties") DataSourceProperties cloudDataSourceProperties) {
        return cloudDataSourceProperties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "cloudJdbcTemplate")
    @ConditionalOnProperty(prefix = "sync.cloud", name = "enabled", havingValue = "true")
    public JdbcTemplate cloudJdbcTemplate(@Qualifier("cloudDataSource") DataSource cloudDataSource) {
        return new JdbcTemplate(cloudDataSource);
    }

    @Bean(name = "cloudTransactionTemplate")
    @ConditionalOnProperty(prefix = "sync.cloud", name = "enabled", havingValue = "true")
    public TransactionTemplate cloudTransactionTemplate(@Qualifier("cloudDataSource") DataSource cloudDataSource) {
        return new TransactionTemplate(new DataSourceTransactionManager(cloudDataSource));
    }
}
