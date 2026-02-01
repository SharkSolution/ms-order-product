package com.suresell.order.config;

import org.springframework.context.annotation.Configuration;

/**
 * Configuración de datasource.
 * Usa solo PostgreSQL (AWS) como datasource principal.
 * Las órdenes offline se persisten en DiskCache (JSON) sin base de datos adicional.
 */
@Configuration
public class DataSourceConfig {
    // Spring Boot auto-configura PostgreSQL desde application.yml
}
