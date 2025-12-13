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
import lombok.Generated;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ProductClientImpl
implements ProductClient {
    @Value(value="${products.service.url}")
    private String productServiceUrl;
    private final RestTemplate restTemplate;

    public String getProductName(String productId) {
        try {
            ProductResponse product = this.getProductDetails(productId);
            return product != null ? product.nameProduct() : "Producto desconocido";
        }
        catch (Exception e) {
            return "Producto desconocido";
        }
    }

    public String getProductCategory(String productId) {
        try {
            String url = this.productServiceUrl + "/products/get/" + productId;
            ProductResponse product = (ProductResponse)this.restTemplate.getForObject(url, ProductResponse.class, new Object[0]);
            return product != null ? product.categoryName() : null;
        }
        catch (Exception e) {
            return null;
        }
    }

    public ProductResponse getProductDetails(String productId) {
        try {
            String url = this.productServiceUrl + "/products/get/" + productId;
            return (ProductResponse)this.restTemplate.getForObject(url, ProductResponse.class, new Object[0]);
        }
        catch (Exception e) {
            return null;
        }
    }

    @Generated
    public ProductClientImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
}

