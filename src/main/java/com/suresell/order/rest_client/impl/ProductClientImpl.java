package com.suresell.order.rest_client.impl;

import com.suresell.order.model.record.ProductResponse;
import com.suresell.order.rest_client.ProductClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class ProductClientImpl implements ProductClient {

    @Value("${products.service.url}")
    private String productServiceUrl;

    private final RestTemplate restTemplate;

    @Override
    public String getProductName(String productId) {
        try {
            String url = productServiceUrl + "/products/get/" + productId;
            ProductResponse product = restTemplate.getForObject(url, ProductResponse.class);
            return product != null ? product.nameProduct() : "Producto desconocido";
        } catch (Exception e) {
            return "Producto desconocido";
        }
    }

}
