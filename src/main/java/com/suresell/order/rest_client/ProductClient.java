package com.suresell.orders.infrastructure.rest_client;
import com.suresell.orders.application.dto.ProductResponse;
public interface ProductClient {
    public String getProductName(String var1);
    public String getProductCategory(String var1);
    public ProductResponse getProductDetails(String var1);
}
