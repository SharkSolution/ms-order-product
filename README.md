# ms-order-product

Microservicio Spring Boot para gestionar órdenes de restaurante, catálogo local, descuentos y cierre diario de caja.

## Qué hace actualmente

- Crea y edita órdenes.
- Consulta historial de órdenes con paginación.
- Consulta una orden por ID.
- Aplica cupones de descuento a órdenes.
- Gestiona cupones (crear, actualizar, listar, desactivar, eliminar).
- Consulta catálogo local de categorías y productos.
- Ejecuta cierre diario de caja y exporta historial a Excel.

## Modelo actual de órdenes

- La orden se registra con `status = pagado`.
- El estado de entrega se maneja en la tabla `order_delivery_tracking`.
- `delivered = false` cuando no existe registro o cuando la columna `delivered` está en `false`.
- `delivered = true` cuando `order_delivery_tracking.delivered = true`.
- También se expone `preparationDurationSeconds` para la duración de preparación.

## Endpoints principales

### Órdenes (`/orders`)
- `POST /orders/create`
- `PUT /orders/{orderId}`
- `GET /orders/historial`
- `GET /orders/{orderId}`
- `PATCH /orders/{orderId}/apply-discount`
- `GET /orders/edit-history`

### Catálogo (`/api/menu`)
- `GET /api/menu/categories-with-products`
- `GET /api/menu/products`

### Descuentos (`/api/discounts`)
- `POST /api/discounts/apply`
- `POST /api/discounts/link-order`
- `GET /api/discounts/active`
- `GET /api/discounts?status=all|active|inactive|expired`
- `POST /api/discounts`
- `PUT /api/discounts/{id}`
- `PATCH /api/discounts/{id}/deactivate`
- `DELETE /api/discounts/{id}`

### Cierres diarios (`/api/closures`)
- `GET /api/closures/preview`
- `POST /api/closures`
- `GET /api/closures/history`
- `GET /api/closures/export/excel`

## Arquitectura

Estructura basada en arquitectura hexagonal:
- `application`: casos de uso y DTOs.
- `domain`: modelos y puertos.
- `infrastructure`: controladores web, persistencia y configuración.
- `shared`: excepciones y utilidades compartidas.

## Configuración

Archivo principal: `src/main/resources/application.yml`

Variables relevantes:
- `server.port`
- `spring.datasource.*`
- `spring.jpa.*`
- `coupon.admin.password`

## Ejecución local

```bash
./gradlew bootRun
```

## Swagger / OpenAPI

- UI: `http://localhost:8081/swagger-ui.html`
- JSON: `http://localhost:8081/v3/api-docs`

## Pruebas

```bash
./gradlew test
```

## Build

```bash
./gradlew clean build
```
