package com.suresell.orders.application.usecase;
import com.suresell.orders.application.dto.MenuCategoryResponse;
import com.suresell.orders.application.dto.MenuProductResponse;
import com.suresell.orders.domain.model.MenuCategory;
import com.suresell.orders.domain.model.MenuProduct;
import com.suresell.orders.domain.port.in.MenuCatalogPort;
import com.suresell.orders.domain.port.out.ProductCatalogPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
@Service
@Primary
@RequiredArgsConstructor
public class MenuCatalogHandler implements MenuCatalogPort {
    private final ProductCatalogPort productCatalogPort;
    private final CatalogSyncService catalogSyncService;
    @Override
    public List<MenuCategoryResponse> getCategoriesWithProducts() {
        return productCatalogPort.findAllCategoriesWithProducts().stream()
                .map(this::toCategoryResponse)
                .toList();
    }
    @Override
    public List<MenuProductResponse> getProducts() {
        return productCatalogPort.findAllProducts().stream()
                .map(this::toProductResponse)
                .toList();
    }
    @Override
    public void syncCatalog() {
        catalogSyncService.syncCatalogFromCloud();
    }
    private MenuCategoryResponse toCategoryResponse(MenuCategory category) {
        List<MenuProductResponse> products = category.getProducts() == null
                ? List.of()
                : category.getProducts().stream()
                        .map(this::toProductResponse)
                        .toList();
        return new MenuCategoryResponse(category.getIdCategory(), category.getNameCategory(), products);
    }
    private MenuProductResponse toProductResponse(MenuProduct product) {
        String categoryId = product.getCategory() != null ? product.getCategory().getIdCategory() : null;
        String categoryName = product.getCategory() != null ? product.getCategory().getNameCategory() : null;
        return new MenuProductResponse(
                product.getIdProduct(),
                product.getNameProduct(),
                product.getPrice(),
                product.getActive(),
                categoryId,
                categoryName);
    }
}
