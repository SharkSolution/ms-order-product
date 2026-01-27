/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.config.RestTemplateConfig
 *  org.springframework.context.annotation.Bean
 *  org.springframework.context.annotation.Configuration
 *  org.springframework.web.client.RestTemplate
 */
package com.suresell.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // Timeouts agresivos - si el producto no responde rápido, usamos fallback
        // Con cache Caffeine, los misses son raros después del warmup
        factory.setConnectTimeout(500);   // 500ms para establecer conexión
        factory.setReadTimeout(1000);     // 1 segundo para leer respuesta

        return new RestTemplate(factory);
    }
}
