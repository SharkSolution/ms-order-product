package com.suresell.orders.domain.port.in;

import com.suresell.orders.application.dto.MenuCategoryResponse;
import com.suresell.orders.application.dto.MenuProductResponse;
import java.util.List;

public interface MenuCatalogPort {
    List<MenuCategoryResponse> getCategoriesWithProducts();

    List<MenuProductResponse> getProducts();
}
