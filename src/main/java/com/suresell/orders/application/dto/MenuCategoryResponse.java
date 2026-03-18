package com.suresell.orders.application.dto;
import java.util.List;
public record MenuCategoryResponse(
        String idCategory,
        String nameCategory,
        List<MenuProductResponse> products
) {
}
