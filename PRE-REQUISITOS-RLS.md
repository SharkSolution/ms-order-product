# PRE-REQUISITOS PARA APLICAR V33

> **`V33__aislamiento_real_en_tablas_de_administracion.sql` NO SE APLICA HASTA
> QUE ESTE DOCUMENTO ESTÉ RESUELTO.**

V33 cierra la política `USING (true)` de las 17 tablas de administración. A
partir de ese momento, **una conexión sin `app.tenant_id` fijado no ve ninguna
fila de esas tablas**, porque `tenant_id = NULL` evalúa a NULL, no a cierto.

`ms-core-app` es quien lee esas tablas y **no filtra por tenant en ningún
repositorio**: todo su aislamiento pasaría a depender de que `app.tenant_id`
esté fijado en **todas** sus conexiones. Este documento lista, con archivo y
línea, los caminos donde eso hoy no está garantizado.

Método: análisis estático del código de `ms-core-app` en la rama
`fase/8-panel-multitenant`, más lectura del filtro y del `DataSource`. **No me
conecté a ninguna base ni a Railway.**

---

## Resumen

| # | Camino | Estado | ¿Bloquea V33? |
|---|---|---|---|
| 1 | Peticiones HTTP normales del panel | ✅ Cubierto | No |
| 2 | `GET /api/core/qr-payments/by-date` desde `ms-order-product-mt` | 🔴 **ROTO YA HOY** | **SÍ** |
| 3 | Health check de `/actuator/health` | 🟡 Toca la base sin tenant | No — no lee tablas de negocio |
| 4 | Arranque de Hibernate/Hikari | 🟡 Toca la base sin tenant | No — ídem |
| 5 | `AnalyticsRepository` | 🟠 8 consultas sin filtro de tenant | Parcialmente |
| 6 | Schedulers / tareas de fondo | ✅ No existen | No |
| 7 | Verificación de la atribución de datos en Producción | ⬜ **NO VERIFICABLE** desde el código | **SÍ** |

**Bloqueantes reales: el 2 y el 7.** Los demás son ruido esperable o deuda que
no impide aplicar la migración.

---

## 1. ✅ Peticiones HTTP normales del panel — cubierto

`JwtTenantFilter` (`ms-core-app/.../infrastructure/multitenant/JwtTenantFilter.java`)
es un `OncePerRequestFilter` registrado como `@Component`, sin
`FilterRegistrationBean` que limite su alcance
(`grep -rn "FilterRegistrationBean\|addUrlPatterns" src/main/java` → cero
resultados). Por tanto se aplica a **todas** las rutas.

Su comportamiento es correcto y estricto (`JwtTenantFilter.java:54-100`):

| Situación | Respuesta |
|---|---|
| Sin cabecera `Authorization: Bearer` | `401` — la petición no llega al controlador |
| Token inválido o vencido | `401` |
| Token válido pero **sin** `tenant_id` (super-admin del KAM) | `403 "El token no identifica un negocio"` |
| Token válido con `tenant_id` | `TenantContext.set(tenantId)` y sigue |

Y siempre limpia en el `finally` (`:96-99`), así que un hilo reutilizado del pool
de Tomcat no arrastra el tenant anterior.

**Conclusión:** los 21 prefijos de ruta del panel (`/api/supplies`,
`/employees`, `/payrolls`, `/expenses`, `/api/valeras`,
`/api/accounts-receivable`…) llegan siempre con negocio en sesión. Este camino
no bloquea nada.

---

## 2. 🔴 BLOQUEANTE — `/qr-payments/by-date` ya está roto hoy

### El hallazgo

`ms-order-product-mt` llama a `ms-core-app` durante el **cierre de caja**:

```java
// ms-order-product-mt/.../application/usecase/ExecuteDailyClosureUseCase.java:203-205
String url = coreApiUrl + "/qr-payments/by-date?date=" + date.toString();
log.info("Consultando pagos QR en ms-core-app: {}", url);
ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
```

`getForEntity(url, JsonNode.class)` **no envía ninguna cabecera**: va sin JWT.

La ruta destino es `/qr-payments` (`ms-core-app/.../infrastructure/web/QrPaymentController.java:17`)
bajo el context-path `/api/core` (`application.yml:4`), o sea
`/api/core/qr-payments/by-date`. Esa ruta **no está en la lista de exentas** del
filtro, que solo perdona `/actuator`, `/swagger-ui` y `/v3/api-docs`
(`JwtTenantFilter.java:48-50`).

**Por tanto `ms-core-app` responde `401 "Falta el token de sesion"`
(`JwtTenantFilter.java:64`) — hoy, no cuando se aplique V33.**

### Por qué nadie se ha enterado

El llamador se traga el error (`ExecuteDailyClosureUseCase.java:211-221`):

```java
} catch (Exception e) {
    log.warn("Error al consultar pago QR en ms-core-app (posible falta de internet o registro no existe): {}", e.getMessage());
}
log.info("Usando valor QR manual del cajero como fallback: {}", fallbackManualQr);
return fallbackManualQr != null ? fallbackManualQr : BigDecimal.ZERO;
```

No falla el cierre: lo **cuadra con otro número** —el que tecleó el cajero— y
deja un `WARN` como única señal. El mensaje del log además atribuye el fallo a
*"posible falta de internet o registro no existe"*, que es justamente la
explicación equivocada.

### Desde cuándo

El filtro se introdujo el **2026-07-30**:

```
$ git log -1 --format="%ad %s" --date=short --diff-filter=A -- .../JwtTenantFilter.java
2026-07-30 feat(panel): multi-tenant real y cierre de un agujero de seguridad (N3-1)
```

Antes de esa fecha `/api/core/**` estaba abierto y la llamada funcionaba. **La
integración de pagos QR con el cierre de caja lleva rota desde el 2026-07-30**,
en silencio, y el cierre viene cuadrando con el valor manual.

> ⬜ **NO VERIFICABLE desde el código:** si los cierres desde el 2026-07-30
> tienen diferencias de caja atribuibles a esto. Haría falta comparar en
> Producción `daily_closures.total_expected_qr` contra la suma de `qr_payments`
> del mismo día. Consulta de solo lectura, la dejo escrita al final.

### Qué hay que hacer antes de V33

Sin esto, V33 no empeora nada aquí (ya está roto), pero se pierde la ocasión de
arreglarlo y quedaría un consumidor sin tenant apuntando a una tabla que pasará
a estar aislada. Dos opciones, **la primera es la correcta**:

**(a) Propagar el JWT del cierre.** El usuario que cierra caja ya tiene un token
válido con su `tenant_id`. Hay que llevarlo hasta la llamada y mandarlo en la
cabecera `Authorization`. Ventaja: `ms-core-app` fija el tenant correcto y
`qr_payments` queda aislada de verdad. Es el arreglo que hace compatible el
endpoint con V33.

**(b) Un token de servicio para llamadas máquina-a-máquina.** Más trabajo y
abre la puerta a un token de larga vida. Solo si (a) no es viable.

**Lo que NO se debe hacer:** añadir `/qr-payments` a la lista de rutas exentas.
Eso lo dejaría sin tenant en sesión y, con V33 aplicada, devolvería cero filas
igualmente — además de reabrir un endpoint con datos de dinero.

---

## 3. 🟡 Health check — toca la base sin tenant, y no importa

`application.yml:47-54` expone `/actuator/health` con `show-details: always` y
`probes.enabled: true`. Spring Boot autoconfigura `DataSourceHealthIndicator`,
que pide una conexión y ejecuta una consulta de validación.

Esa conexión pasa por `TenantAwareDataSource` **sin** `TenantContext` fijado (la
ruta `/actuator` está exenta del filtro, `JwtTenantFilter.java:48`), así que se
ejecuta `set_config('app.tenant_id', '', false)`.

**No bloquea V33:** la consulta de salud es un `SELECT 1`, no lee ninguna tabla
de negocio. Lo único que cambia es que, con la instrumentación de T7 desplegada,
este camino **aparecerá en el log** como `tenant-ausente`. Es ruido esperado y
conviene reconocerlo para no confundirlo con un hallazgo.

---

## 4. 🟡 Arranque — mismo caso

Hikari abre conexiones al iniciar (`minimum-idle: 1`, `application.yml:23`) y
Hibernate arranca su `EntityManagerFactory`. Ninguna de esas conexiones tiene
tenant. Con `ddl-auto: none` (`application.yml:29`) no hay DDL que ejecutar.

**No bloquea V33.** También aparecerá en el log de T7, una vez por arranque.

---

## 5. 🟠 `AnalyticsRepository` — deuda que V33 destapa a medias

`ms-core-app/.../domain/port/out/AnalyticsRepository.java` tiene 8 consultas y
**ninguna filtra por tenant**. Hoy funcionan por dos motivos distintos según la
tabla que tocan:

| Consulta | Línea | Tabla | Hoy |
|---|---|---|---|
| `getSalesTrend` | 19-23 | `daily_closures` | ✅ Aislada por RLS real (V2) |
| `getTopProducts` | 25-31 | `order_item`, `orders`, `menu_products` | ✅ Aisladas por RLS real (V1/V2) |
| `getPaymentMethodDistribution` | 54-63 | `orders`, `order_payments` | ✅ Aisladas (V1/V13) |
| `getCashPerformance` | 72-76 | `daily_closures` | ✅ Aislada |
| `getPeakHoursRaw` | 78-84 | `orders` | ✅ Aislada |
| `getTotalSalesToday` | 86-87 | `orders` | ✅ Aislada |
| `getTotalOrdersToday` | 89-90 | `orders` | ✅ Aislada |
| **`getInventoryConsumptionToday`** | **92-93** | **`supply_consumptions`** | 🔴 **Sin aislar — cuenta los consumos de todos los negocios** |

Es decir: siete de las ocho ya están protegidas por la RLS de las tablas de
ventas, y la octava lee una de las 17 tablas abiertas.

**Efecto de V33 sobre la octava:** pasa de devolver un número inflado con datos
de todos los negocios a devolver el número correcto del negocio en sesión —
**siempre que la llamada llegue con tenant**, cosa que sí ocurre porque
`AnalyticsController` está detrás del filtro.

**No bloquea V33: la mejora.** Se anota aquí porque el número del panel de
analítica **va a cambiar** el día que se aplique, y conviene que no se lea como
un error.

---

## 6. ✅ Schedulers y tareas de fondo — no existen

```
$ grep -rn "@Scheduled|@Async|@PostConstruct|ApplicationRunner|CommandLineRunner|@EventListener" \
       ms-core-app/src/main/java
(cero resultados)
```

`ms-core-app` no tiene ninguna tarea de fondo. Todo su acceso a datos nace de
una petición HTTP. Es la razón principal por la que el riesgo de V33 es menor de
lo que parecía.

Los 9 casos de uso que llaman `findAll()`
(`GetAllSupplyCategoriesUseCase`, `ManageEmployeeUseCase`, `ExpenseCategoryService`,
`SupplyConsumptionService`, `SupplyService`, `CreateSupplyUseCase`,
`ManagePayrollUseCase`, `ManageExpenseUseCase`, `ManageWaiterUseCase`) se invocan
todos desde controladores, o sea siempre con tenant en sesión.

---

## 7. ⬜ BLOQUEANTE — la atribución de los datos existentes NO ES VERIFICABLE desde el código

Este es el riesgo que no se puede resolver leyendo código.

Hasta V32 (2026-08), las 17 tablas tenían `tenant_id TEXT NOT NULL DEFAULT
'shark-burger'` (V28:33 y siguientes). Todo lo que se insertó en ese periodo sin
mandar la columna quedó atribuido a `shark-burger`, **fuera o no de ese
negocio**. V32 corrigió el default, pero **no reatribuyó ninguna fila
existente**.

Si en Producción hay filas cuyo `tenant_id` no corresponde a su dueño real, al
aplicar V33 ese negocio dejará de verlas y parecerá que se perdieron datos.

**Hay que ejecutar esto en Producción ANTES de aplicar V33** (solo lectura, no
modifica nada):

```sql
SELECT 'supply_categories' AS tabla, tenant_id, count(*) FROM supply_categories GROUP BY 2
UNION ALL SELECT 'supplies',                tenant_id, count(*) FROM supplies                GROUP BY 2
UNION ALL SELECT 'supply_consumptions',     tenant_id, count(*) FROM supply_consumptions     GROUP BY 2
UNION ALL SELECT 'weekly_inventory_counts', tenant_id, count(*) FROM weekly_inventory_counts GROUP BY 2
UNION ALL SELECT 'suppliers',               tenant_id, count(*) FROM suppliers               GROUP BY 2
UNION ALL SELECT 'supplier_requests',       tenant_id, count(*) FROM supplier_requests       GROUP BY 2
UNION ALL SELECT 'supplier_request_items',  tenant_id, count(*) FROM supplier_request_items  GROUP BY 2
UNION ALL SELECT 'shopping_items',          tenant_id, count(*) FROM shopping_items          GROUP BY 2
UNION ALL SELECT 'employees',               tenant_id, count(*) FROM employees               GROUP BY 2
UNION ALL SELECT 'payrolls',                tenant_id, count(*) FROM payrolls                GROUP BY 2
UNION ALL SELECT 'expense_categories',      tenant_id, count(*) FROM expense_categories      GROUP BY 2
UNION ALL SELECT 'expenses',                tenant_id, count(*) FROM expenses                GROUP BY 2
UNION ALL SELECT 'valeras',                 tenant_id, count(*) FROM valeras                 GROUP BY 2
UNION ALL SELECT 'meal_preparations',       tenant_id, count(*) FROM meal_preparations       GROUP BY 2
UNION ALL SELECT 'accounts_receivable',     tenant_id, count(*) FROM accounts_receivable     GROUP BY 2
UNION ALL SELECT 'debt_transactions',       tenant_id, count(*) FROM debt_transactions       GROUP BY 2
UNION ALL SELECT 'qr_payments',             tenant_id, count(*) FROM qr_payments             GROUP BY 2
ORDER BY 1, 2;
```

**Criterio de aprobación:** cada `tenant_id` que aparezca debe existir en
`tenants` y corresponder a un negocio que efectivamente usa ese módulo. Si sale
un único `tenant_id` y es el del cliente que opera el panel, está limpio y se
puede aplicar V33.

Y para el punto 2, comprobar si el cierre viene cuadrando con el valor manual
desde el 2026-07-30:

```sql
SELECT c.closure_date,
       c.total_expected_qr                      AS qr_en_el_cierre,
       COALESCE(q.suma, 0)                      AS qr_registrado,
       c.total_expected_qr - COALESCE(q.suma,0) AS diferencia
FROM   daily_closures c
LEFT   JOIN (SELECT payment_date, sum(amount) AS suma
             FROM qr_payments GROUP BY payment_date) q
       ON q.payment_date = c.closure_date
WHERE  c.closure_date >= DATE '2026-07-30'
ORDER  BY c.closure_date DESC;
```

---

## Lista de comprobación antes de aplicar V33

- [ ] **(2)** El JWT se propaga en la llamada a `/qr-payments/by-date`, o se ha
      decidido explícitamente dejar ese endpoint fuera de servicio.
- [ ] **(7)** La consulta de atribución se ejecutó en Producción y no hay
      `tenant_id` inesperados.
- [ ] **T7 desplegada** (`feat/deteccion-sin-tenant`) y con varios días de log,
      sin avisos `tenant-ausente` distintos de `/actuator/health` y del arranque.
- [ ] V33 aplicada primero en **Staging**, con el panel probado a mano:
      inventario, empleados, nómina, gastos, cartera y valeras siguen viéndose.
- [ ] Snapshot de la base antes de aplicar en Producción, como se hizo en
      `V21:18` (`backup_20260725`).
- [ ] El rollback de V33 (bloque DOWN al final del archivo) está a mano y
      alguien sabe que reabre el agujero.

---

## Lo que V33 NO arregla, y conviene tener claro

Cerrar la política deja el aislamiento en manos de la base, que es donde debe
estar. Pero `ms-core-app` sigue **sin filtrar por tenant en su código**: si
mañana alguien conecta ese servicio con un rol que tenga `BYPASSRLS` —por
ejemplo el `postgres.<project-ref>` de Supabase, que es el que usa el camino de
sincronización de `ms-order-product-mt`
(`ms-order-product-mt/src/main/resources/application.yml:45`)— el aislamiento
desaparece otra vez y nada en el código lo impediría.

La defensa en profundidad que falta es un filtro de Hibernate (`@Filter`) o un
criterio explícito en los repositorios. Está fuera del alcance de V33 y anotado
en `discovery/NOTAS.md`.
