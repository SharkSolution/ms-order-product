# 🍔 Ejemplo Funcional: Día en el Restaurante

Este documento describe **un día completo de operación** del sistema de órdenes, mostrando cómo se comporta ante caídas de **internet** y de **microservicios**, garantizando que la **cocina siempre vea todas las órdenes**.

---

## 📅 Escenario Completo

---

## ⏰ 10:00 AM — TODO FUNCIONA NORMAL

**Estado del sistema**
- 🌐 Internet: ✅ Disponible
- 🧩 ms-product-category: ✅ Funcionando

**Pedidos realizados**

**Orden #1475**
- Pager: 🔴 ROJO #5
- 1× Hamburguesa Clásica — **$49.00**

**Orden #1476**
- Pager: 🔵 AZUL #3
- 1× Pizza Napolitana — **$65.00**

✔️ Ambas órdenes se guardan correctamente en **AWS (Base de datos en la nube)**.

---

## ⏰ 10:30 AM — SE CAE INTERNET

**Estado del sistema**
- 🌐 Internet: ❌ Caído
- 🧩 ms-product-category: ✅ Funcionando (local)

**Pedido realizado (Offline)**

**Orden Offline**
- Pager: 🟡 AMARILLO #1
- 1× Ensalada César
- productId: `35`

**Persistencia local**
```json
./cache/offline-order-LOCAL-abc123.json
{
  "productId": "35",
  "quantity": 1
}
```

- Estado: `synced = false` (pendiente de sincronizar)

---

## ⏰ 10:35 AM — TABLET CONSULTA ÓRDENES

**Estado del sistema**
- 🌐 Internet: ❌ Caído
- 🧩 ms-product-category: ✅ Funcionando

**Solicitud**
```
GET /orders/cocina
```

### 🔍 Flujo interno del sistema

**Paso 1 — Intentar obtener desde AWS**
- ¿AWS disponible? ❌ No

**Paso 2 — Leer caché local**
- Archivo: `./cache/kitchen-orders.json`
- Contenido (previo a la caída):
  - Orden #1475 — ROJO #5 — Hamburguesa Clásica
  - Orden #1476 — AZUL #3 — Pizza Napolitana

**Paso 3 — Combinación**
- ❌ No se combinan órdenes offline (caché desactualizado)

### 👀 Lo que ve la Tablet
```
COMANDA - Pager ROJO #5
1x Hamburguesa Clásica  $49.00

COMANDA - Pager AZUL #3
1x Pizza Napolitana     $65.00
```

⚠️ La orden **AMARILLO #1** no aparece todavía.

---

## ⏰ 11:00 AM — VUELVE INTERNET

**Estado del sistema**
- 🌐 Internet: ✅ Recuperado
- 🧩 ms-product-category: ✅ Funcionando

**Sincronización automática (cada 30 seg)**
- Lee: `offline-orders-index.json`
- Encuentra: `LOCAL-abc123` con `synced=false`
- Sube a AWS: `productId=35`, `quantity=1`
- Actualiza estado: `synced=true`

**Órdenes en AWS ahora**
- #1475 — ROJO #5
- #1476 — AZUL #3
- #1477 — AMARILLO #1 ✅ (recién sincronizada)

---

## ⏰ 11:01 AM — TABLET CONSULTA ÓRDENES

**Estado del sistema**
- 🌐 Internet: ✅ Disponible
- 🧩 ms-product-category: ✅ Funcionando

### 🔍 Flujo interno

**Paso 1 — Obtener desde AWS**
- Se consultan 3 órdenes con nombres completos

**Paso 2 — Obtener offline pendientes**
- `synced=false` → ❌ Ninguna

**Paso 3 — Combinar**
- AWS (3) + Offline (0) = **3 órdenes**

### 👀 Lo que ve la Tablet
```
ROJO #5      Hamburguesa Clásica  $49.00
AZUL #3      Pizza Napolitana     $65.00
AMARILLO #1  Ensalada César       $42.00
```

---

## ⏰ 11:30 AM — CAE ms-product-category (LOCAL)

**Estado del sistema**
- 🌐 Internet: ✅ Disponible
- 🧩 ms-product-category: ❌ Caído (puerto 8082)

**Pedidos realizados**

**Orden #1478 (Online)**
- Pager: 🟢 VERDE #7
- 1× Tacos al Pastor
- Guardada en AWS ✅

**Orden Offline #2**
- Pager: 🟣 MORADO #9
- 1× Nachos
- productId: `42`
- Guardada en archivo local ⚠️

---

## ⏰ 11:32 AM — TABLET CONSULTA (CASO CRÍTICO)

**Estado del sistema**
- 🌐 Internet: ✅ Disponible
- 🧩 ms-product-category: ❌ Caído

### 🔍 Flujo interno

**Paso 1 — Obtener desde AWS**
- Retorna 4 órdenes con nombres correctos

**Paso 2 — Obtener offline pendientes**
- Encuentra orden MORADO #9 (`productId=42`)
- Intenta enriquecer nombre:
  - Llamada a `localhost:8082` ❌ Connection refused
  - Fallback: `"Producto no disponible"`

**Paso 3 — Combinar**
- AWS (4) + Offline (1 con fallback)

### 👀 Lo que ve la Tablet
```
ROJO #5      Hamburguesa Clásica  $49.00
AZUL #3      Pizza Napolitana     $65.00
AMARILLO #1  Ensalada César       $42.00
VERDE #7     Tacos al Pastor      $38.00
MORADO #9    Producto no disponible $55.00 ⚠️
```

---

## ⏰ 11:33 AM — SE RECUPERA ms-product-category

**Estado del sistema**
- 🌐 Internet: ✅ Disponible
- 🧩 ms-product-category: ✅ Recuperado

**Nueva consulta automática**
- Se reintenta enriquecer orden MORADO #9
- Respuesta exitosa: `Nachos`
- Cache en memoria (15 min)

### 👀 Lo que ve la Tablet
```
ROJO #5      Hamburguesa Clásica  $49.00
AZUL #3      Pizza Napolitana     $65.00
AMARILLO #1  Ensalada César       $42.00
VERDE #7     Tacos al Pastor      $38.00
MORADO #9    Nachos               $55.00 ✅
```

---

## 📊 Resumen Visual del Flujo

```
GET /orders/cocina
   ↓
[ Paso 1 ] Órdenes desde AWS
   ↓
[ Paso 2 ] Órdenes Offline pendientes
   ↓
  - Enriquecer en tiempo real
  - Fallback si falla
   ↓
[ Paso 3 ] Combinar listas
   ↓
Respuesta a la Tablet
```

---

## 🎯 Puntos Clave

1. **"Producto no disponible" nunca se guarda** (solo memoria)
2. Se regenera en cada consulta
3. La tablet siempre ve **AWS + Offline**
4. Las órdenes offline se enriquecen en tiempo real
5. Fallos no rompen el flujo ni corrompen archivos

✅ **Resultado:** la cocina **nunca pierde órdenes**, incluso en escenarios críticos.

