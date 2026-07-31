# ms-order-product

> **Antes de agregar o quitar un campo de una respuesta, leé el [contrato de API](../../docs/CONTRATO-API.md).**
> Las apps moviles se distribuyen por APK: no hay forma de garantizar que el local actualizo.


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
- El estado de entrega y del pager se maneja en la tabla `order_delivery_tracking`.
- **Entrega de Comida (`delivered`):** Indica si la cocina ya entregó el pedido.
- **Devolución de Pager (`pager_returned`):** Indica si el cliente ya devolvió el plástico al cajero.
- Un Pager se considera **LIBRE** si `delivered = true` O `pager_returned = true`.
- También se expone `preparationDurationSeconds` para la duración de preparación.

## Sincronización y Gestión de Pagers

Este microservicio implementa una arquitectura **Local-First** diseñada para operar incluso sin conexión a internet estable, sincronizando datos de forma asíncrona con una base de datos central en la nube (PostgreSQL).

### Patrón Outbox (Sincronización de Salida)
- Cada cambio en órdenes, cierres o cupones genera un registro en la tabla `sync_outbox`.
- Un scheduler procesa estos registros y los envía a la nube.
- Si el internet falla, los registros permanecen en `PENDING` y se reintentan automáticamente cuando vuelve la conexión.

### Sincronización Inversa (Estado de Pagers)
- El sistema consulta a la nube cada **7 segundos** buscando actualizaciones en el estado de entrega.
- Esta consulta es **selectiva**: solo pregunta por las órdenes que el POS local tiene marcadas como "ocupadas", optimizando el ancho de banda.
- Permite que cuando la cocina marca un pedido como entregado en la nube, el Pager se libere localmente en el POS de forma casi instantánea.

### Gestión de Pagers y "Botón de Pánico"
Para evitar bloqueos operativos por falta de internet o lentitud de sincronización, se implementó una lógica de **Devolución de Pager** independiente de la **Entrega de Orden**:
- **Pager Ocupado:** Una orden retiene un pager solo si no se ha marcado como entregada Y no se ha marcado el pager como devuelto físicamente.
- **Liberación Manual:** Si un cajero necesita un pager que el sistema aún marca como ocupado, puede liberarlo manualmente. Esto libera el plástico para una nueva orden pero **mantiene la orden original activa** para que la cocina la procese.

## Endpoints principales

### Órdenes (`/orders`)
- `POST /orders/create`
- `PUT /orders/{orderId}`
- `GET /orders/historial`
- `GET /orders/{orderId}`
- `PATCH /orders/{orderId}/apply-discount`
- `GET /orders/edit-history`
- `GET /orders/pager-availability`: Consulta pagers ocupados y disponibles (libera automáticamente si el pager fue devuelto).
- `DELETE /orders/pagers/release?color=AMARILLO&number=5`: **Botón de Pánico**. Libera un pager físicamente indicando solo color y número, sin afectar la preparación en cocina.
- `PUT /orders/{orderId}/deliver`: Marca una orden como entregada y libera el pager asociado (uso por ID).

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
