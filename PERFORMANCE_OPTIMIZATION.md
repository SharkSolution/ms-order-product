# Optimización de Performance - Endpoint /orders/historial

## Problema Identificado

El endpoint `GET /orders/historial` tardaba ~49-50 segundos en responder con solo 1082 registros debido a **TRIPLE problema**:

### 1. N+1 Queries SQL (1,083 queries a la BD)
- **Antes:** `orderRepository.findAll()` ejecutaba:
  - 1 query para obtener 1082 órdenes
  - 1082 queries adicionales para obtener los items de cada orden (relación `@OneToMany` LAZY)
  - **Total: 1,083 queries SQL**

### 2. N+1 HTTP Calls (~3,000-4,000 llamadas)
- **Antes:** `OrderMapper.toOrderItemResponse()` llamaba a `productClient.getProductName()` por cada item
  - Si cada orden tiene 3 items promedio: **1082 × 3 = 3,246 llamadas HTTP síncronas**
  - **Esto era la causa principal de los ~50 segundos**

### 3. Sin Paginación
- Cargaba todas las 1082 órdenes en memoria sin límite

---

## Soluciones Implementadas

### ✅ Solución 1: JOIN FETCH para eliminar N+1 queries SQL

**Archivo:** `OrderRepository.java`

```java
@Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items")
List<Order> findAllWithItems();
```

**Resultado:** Ahora ejecuta **1 sola query** con JOIN para traer órdenes + items.

---

### ✅ Solución 2: Cache de nombres de productos

**Archivo:** `OrderMapper.java`

- Implementa un `ThreadLocal<Map<String, String>>` como cache
- Cachea los nombres de productos durante el procesamiento del lote
- Si 100 órdenes tienen el mismo producto, solo hace **1 llamada HTTP** en lugar de 100

**Antes:**
```java
private OrderItemResponseRecord toOrderItemResponse(OrderItem item) {
    return new OrderItemResponseRecord(
        item.getProductId(),
        productClient.getProductName(item.getProductId()), // ❌ Llamada HTTP por cada item
        ...
    );
}
```

**Después:**
```java
private OrderItemResponseRecord toOrderItemResponse(OrderItem item) {
    String productName = productNameCache.get().computeIfAbsent(
        item.getProductId(),
        productClient::getProductName  // ✅ Solo llama si no está en cache
    );
    return new OrderItemResponseRecord(item.getProductId(), productName, ...);
}
```

**Resultado:** Si hay 500 productos únicos en 1082 órdenes con 3 items cada una:
- **Antes:** 3,246 llamadas HTTP
- **Después:** 500 llamadas HTTP (reducción del 85%)

---

### ✅ Solución 3: Paginación opcional

**Archivo:** `OrderController.java`

El endpoint ahora soporta paginación:

```bash
# Sin paginación (backward compatible, pero optimizado)
GET /orders/historial

# Con paginación (recomendado)
GET /orders/historial?paginated=true&page=0&size=50
```

**Respuesta paginada:**
```json
{
  "content": [...],
  "totalElements": 1082,
  "totalPages": 22,
  "size": 50,
  "number": 0
}
```

---

## Mejoras de Performance Esperadas

| Métrica | Antes | Después | Mejora |
|---------|-------|---------|--------|
| **Queries SQL** | 1,083 | 1 | 99.9% |
| **HTTP Calls** | ~3,246 | ~500 | 85% |
| **Tiempo respuesta** | ~50 seg | **~2-5 seg** | 90-96% |
| **Uso memoria** | Alto (1082 órdenes) | Bajo (50 por página) | 95% |

---

## Cómo Verificar las Optimizaciones

### 1. Verificar queries SQL en logs

Con el logging habilitado en `application.yml`:

```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.orm.jdbc.bind: TRACE
```

**Buscar en logs:**

✅ **Con optimización** - Verás 1 query con LEFT JOIN FETCH:
```sql
Hibernate:
    select
        distinct o1_0.id_order,
        i1_0.order_id,
        i1_0.id_order_item,
        ...
    from
        orders o1_0
    left join
        order_items i1_0
            on o1_0.id_order=i1_0.order_id
```

❌ **Sin optimización** - Verías 1083 queries:
```sql
Hibernate: select ... from orders
Hibernate: select ... from order_items where order_id=?
Hibernate: select ... from order_items where order_id=?
... (1082 veces más)
```

### 2. Medir tiempo de respuesta

```bash
# Medir sin paginación (todas las órdenes optimizadas)
time curl -X GET http://localhost:8081/orders/historial

# Medir con paginación (50 órdenes)
time curl -X GET "http://localhost:8081/orders/historial?paginated=true&size=50"
```

### 3. Monitorear en AWS CloudWatch

**Métricas a observar:**
- **Latencia del ALB Target Response Time:** Debería bajar de ~50s a ~2-5s
- **CPU Utilization:** Debería mantenerse baja (<50%)
- **Database Connections:** Menos conexiones activas
- **Memory Utilization:** Uso más estable

### 4. Verificar logs de aplicación

El cache se limpia automáticamente después de cada request:

```java
} finally {
    orderMapper.clearProductNameCache();
}
```

---

## Recomendaciones Adicionales

### 1. Usar Paginación (ALTAMENTE RECOMENDADO)

```bash
# Frontend debería usar:
GET /orders/historial?paginated=true&page=0&size=50
```

### 2. Considerar guardar nombres de productos en OrderItem

Para eliminar completamente las llamadas HTTP:

```sql
ALTER TABLE order_items ADD COLUMN product_name VARCHAR(255);
```

```java
@Column(name = "product_name")
private String productName;
```

Guardar el nombre al crear el item y evitar llamadas al microservicio de productos.

### 3. Implementar índices en BD

Si no existen:

```sql
CREATE INDEX idx_order_status ON orders(status);
CREATE INDEX idx_order_created_at ON orders(created_at);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
```

### 4. Pool de Conexiones (application.yml)

Asegurar configuración adecuada:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### 5. Deshabilitar logging SQL en producción

En `application.yml`, cambiar a:

```yaml
logging:
  level:
    org.hibernate.SQL: WARN  # O ERROR en producción
```

---

## Testing

### Test de Carga (Opcional)

```bash
# Instalar Apache Bench
brew install httpd  # macOS
sudo apt install apache2-utils  # Linux

# Test sin paginación (10 requests concurrentes)
ab -n 100 -c 10 http://your-ecs-url:8081/orders/historial

# Test con paginación
ab -n 100 -c 10 "http://your-ecs-url:8081/orders/historial?paginated=true&size=50"
```

---

## Archivos Modificados

1. ✅ `OrderRepository.java` - Agregado `findAllWithItems()` con JOIN FETCH
2. ✅ `OrderMapper.java` - Cache de nombres de productos
3. ✅ `OrderServiceImpl.java` - Uso de query optimizada y limpieza de cache
4. ✅ `OrderController.java` - Soporte de paginación
5. ✅ `OrderService.java` - Interface con método paginado
6. ✅ `application.yml` - Logging de SQL habilitado

---

## Deployment

1. **Build nueva imagen:**
```bash
docker buildx build --platform linux/amd64 \
  -t 317976464213.dkr.ecr.us-east-1.amazonaws.com/ms-order-product:prod \
  --push .
```

2. **Actualizar Task Definition en ECS** (forzar nuevo deployment)

3. **Verificar logs en CloudWatch:**
```bash
aws logs tail /ecs/ms-order-product --follow
```

4. **Monitorear métricas en CloudWatch** durante 10-15 minutos

---

## Troubleshooting

### Si todavía es lento:

1. **Verificar que se usa la query optimizada:**
   - Revisar logs de SQL
   - Debe haber 1 query con LEFT JOIN FETCH

2. **Verificar llamadas HTTP al microservicio de productos:**
   - Agregar logging en ProductClient
   - Debe haber máximo 1 llamada por producto único

3. **Verificar paginación:**
   - Usar `?paginated=true&size=50` para reducir carga

4. **Verificar latencia de red entre ECS y RDS:**
   - Asegurar que ambos estén en la misma VPC/subnet

5. **Verificar pool de conexiones:**
   - Revisar métricas de conexiones activas en RDS
