package com.suresell.orders.application.dto;

public record MenuProductResponse(
        String idProduct,
        String nameProduct,
        Integer price,
        Boolean active,
        String categoryId,
        String categoryName
) {
}
