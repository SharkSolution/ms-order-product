package com.suresell.orders.infrastructure.client.adapter;

import com.suresell.orders.application.dto.ProductResponse;
import com.suresell.orders.domain.port.out.ProductClientPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ProductClientAdapter implements ProductClientPort {

    private final RestTemplate restTemplate;
    private final String productServiceBaseUrl;

    public ProductClientAdapter(RestTemplate restTemplate, @Value("${app.product-service.base-url}") String productServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.productServiceBaseUrl = productServiceBaseUrl;
    }

    @Override
    public ProductResponse getProductById(String productId) {
        String url = productServiceBaseUrl + "/products/" + productId;
        // In a real scenario, this would handle exceptions, not found, etc.
        return restTemplate.getForObject(url, ProductResponse.class);
    }
}
