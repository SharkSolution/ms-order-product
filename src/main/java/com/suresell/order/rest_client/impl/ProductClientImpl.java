package com.suresell.orders.infrastructure.rest_client;

import com.suresell.orders.application.dto.ProductResponse;
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
            return FALLBACK_PRODUCT_NAME;
        } catch (RestClientException e) {
            return FALLBACK_PRODUCT_NAME;
        } catch (Exception e) {
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
            return FALLBACK_CATEGORY;
        } catch (Exception e) {
            return FALLBACK_CATEGORY;
        }
    }

    @Override
    public ProductResponse getProductDetails(String productId) {
        try {
            String url = productServiceUrl + "/products/get/" + productId;
            return restTemplate.getForObject(url, ProductResponse.class);
        } catch (ResourceAccessException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
