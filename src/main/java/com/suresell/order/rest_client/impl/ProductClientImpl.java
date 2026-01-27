/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.record.ProductResponse
 *  com.suresell.order.rest_client.ProductClient
 *  com.suresell.order.rest_client.impl.ProductClientImpl
 *  lombok.Generated
 *  org.springframework.beans.factory.annotation.Value
 *  org.springframework.stereotype.Service
 *  org.springframework.web.client.RestTemplate
 */
package com.suresell.order.rest_client.impl;

import com.suresell.order.model.record.ProductResponse;
import com.suresell.order.rest_client.ProductClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductClientImpl implements ProductClient {

    @Value("${products.service.url}")
    private String productServiceUrl;

    private final RestTemplate restTemplate;

    private static final String FALLBACK_PRODUCT_NAME = "Producto no disponible";
    private static final String FALLBACK_CATEGORY = "Sin categoría";

    @Override
    public String getProductName(String productId) {
        try {
            ProductResponse product = getProductDetails(productId);
            return product != null ? product.nameProduct() : FALLBACK_PRODUCT_NAME;
        } catch (ResourceAccessException e) {
            // Timeout o error de conexión
            log.warn("Timeout/Connection error al obtener producto {}: {}", productId, e.getMessage());
            return FALLBACK_PRODUCT_NAME;
        } catch (RestClientException e) {
            // Otros errores HTTP (404, 500, etc)
            log.warn("Error HTTP al obtener producto {}: {}", productId, e.getMessage());
            return FALLBACK_PRODUCT_NAME;
        } catch (Exception e) {
            log.error("Error inesperado al obtener producto {}: {}", productId, e.getMessage());
            return FALLBACK_PRODUCT_NAME;
        }
    }

    @Override
    public String getProductCategory(String productId) {
        try {
            String url = productServiceUrl + "/products/get/" + productId;
            ProductResponse product = restTemplate.getForObject(url, ProductResponse.class);
            return product != null ? product.categoryName() : FALLBACK_CATEGORY;
        } catch (ResourceAccessException e) {
            log.warn("Timeout/Connection error al obtener categoría de producto {}: {}", productId, e.getMessage());
            return FALLBACK_CATEGORY;
        } catch (Exception e) {
            log.warn("Error al obtener categoría de producto {}: {}", productId, e.getMessage());
            return FALLBACK_CATEGORY;
        }
    }

    @Override
    public ProductResponse getProductDetails(String productId) {
        try {
            String url = productServiceUrl + "/products/get/" + productId;
            return restTemplate.getForObject(url, ProductResponse.class);
        } catch (ResourceAccessException e) {
            // Timeout o error de conexión - NO logear como error, es esperado
            log.debug("Timeout al obtener detalles de producto {}", productId);
            return null;
        } catch (Exception e) {
            log.debug("Error al obtener detalles de producto {}: {}", productId, e.getMessage());
            return null;
        }
    }
}
