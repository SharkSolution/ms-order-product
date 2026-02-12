package com.suresell.orders.domain.port.out;

import com.suresell.orders.application.dto.ProductResponse; // Assuming ProductResponse is in DTOs

public interface ProductClientPort {
    ProductResponse getProductById(String productId);
    // Add other methods as needed, based on ProductClientImpl if it's not empty
}
