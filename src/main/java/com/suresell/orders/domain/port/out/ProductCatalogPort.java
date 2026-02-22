package com.suresell.orders.domain.port.out;

import com.suresell.orders.application.dto.ProductResponse;
import com.suresell.orders.domain.model.MenuCategory;
import com.suresell.orders.domain.model.MenuProduct;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ProductCatalogPort {
    Map<String, ProductResponse> findProductsByIds(Set<String> productIds);

    List<MenuCategory> findAllCategoriesWithProducts();

    List<MenuProduct> findAllProducts();
}
