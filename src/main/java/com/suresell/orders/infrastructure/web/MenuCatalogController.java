package com.suresell.orders.infrastructure.web;
import com.suresell.orders.application.dto.MenuCategoryResponse;
import com.suresell.orders.application.dto.MenuProductResponse;
import com.suresell.orders.domain.port.in.MenuCatalogPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/menu")
@Tag(name = "Menu Catalog", description = "Catálogo local de categorías y productos")
public class MenuCatalogController {
    private final MenuCatalogPort menuCatalogPort;
    public MenuCatalogController(MenuCatalogPort menuCatalogPort) {
        this.menuCatalogPort = menuCatalogPort;
    }
    @GetMapping("/categories-with-products")
    @Operation(summary = "Listar categorías con sus productos")
    public List<MenuCategoryResponse> getCategoriesWithProducts() {
        return menuCatalogPort.getCategoriesWithProducts();
    }
    @GetMapping("/products")
    @Operation(summary = "Listar productos del catálogo")
    public List<MenuProductResponse> getProducts() {
        return menuCatalogPort.getProducts();
    }
    @PostMapping("/sync")
    @Operation(summary = "Forzar sincronización de catálogo con la nube", description = "Descarga y actualiza categorías y productos desde PostgreSQL a SQLite local.")
    public void syncCatalog() {
        menuCatalogPort.syncCatalog();
    }
}
