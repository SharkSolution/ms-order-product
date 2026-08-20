# CONSULTAS DE VIGILANCIA

Consultas **de solo lectura** para detectar en un día lo que la última vez tardó
tres semanas en salir a la luz.

Todas son `SELECT`. Ninguna modifica nada.

---

## 1. Cierres con la conciliación de QR rota — LA IMPORTANTE

**Qué detecta:** que `ms-core-app` no está respondiendo a la conciliación del QR y
que los cierres se están cuadrando con el valor manual del cajero.

**Cada cuánto:** a diario. Idealmente automatizada con alerta.

```sql
SELECT closure_date,
       user_name,
       total_counted_qr,
       qr_detalle,
       qr_capturado_en
FROM   daily_closures
WHERE  qr_fuente = 'fallo_integracion'
  AND  closure_date >= current_date - INTERVAL '7 days'
ORDER  BY closure_date DESC;
```

**Criterio:** cualquier fila es una señal. `qr_detalle` trae el motivo técnico
real —`HTTP 401 Unauthorized de ms-core-app (el JWT no llego o no es valido)`,
`ConnectException: ...`, `SocketTimeoutException`— así que la fila sola basta
para saber por dónde empezar.

> **`sin_registro_externo` NO es un fallo** y no entra en esta consulta. Significa
> que `ms-core-app` respondió bien y dijo que no hay registro, mientras el cajero
> sí contó dinero. Con `qr_payments` en tres filas históricas, ese es el caso
> **normal**, no la excepción. Si se contara como fallo, la alarma sonaría todos
> los días y dejaría de servir.

Versión resumida, para un panel o un cron:

```sql
SELECT count(*) AS cierres_degradados_7d
FROM   daily_closures
WHERE  qr_fuente = 'fallo_integracion'
  AND  closure_date >= current_date - INTERVAL '7 days';
```

> Si este número es > 0 dos días seguidos, la integración está caída. El índice
> parcial `idx_daily_closures_qr_fallo` (V34) está hecho justo para esta consulta.

---

## 1-bis. 🔴 LA MÉTRICA DE CONTROL INTERNO: POS contra cajero

**Qué detecta:** la diferencia entre lo que el POS registró como cobrado por QR y
lo que el cajero declara al cerrar. **Es la métrica útil desde el primer día**, y
no depende de `ms-core-app` ni de que nadie mantenga un registro externo.

**Por qué esta y no la conciliación externa:** `qr_payments` tiene **tres filas
en toda su historia**. El administrador prácticamente no ha usado ese registro,
así que una vigilancia basada en él no mide nada. `qr_pos` sale de las ventas
mismas y existe siempre.

```sql
SELECT closure_date,
       user_name,
       qr_pos,
       qr_manual_cajero,
       qr_manual_cajero - qr_pos AS diferencia,
       qr_fuente,
       total_counted_qr          AS el_que_fue_al_cuadre
FROM   daily_closures
WHERE  closure_date >= current_date - INTERVAL '30 days'
  AND  qr_pos IS NOT NULL
  AND  qr_manual_cajero IS NOT NULL
  AND  qr_manual_cajero <> qr_pos
ORDER  BY abs(qr_manual_cajero - qr_pos) DESC;
```

**Cómo leerlo:**

| Patrón | Interpretación probable |
|---|---|
| `manual < pos` de forma repetida | Ventas cobradas por QR que no llegan a declararse |
| `manual > pos` de forma repetida | Se está declarando como QR algo que el POS no registró así |
| Una diferencia aislada y pequeña | Ruido normal; un cobro fuera del POS |
| **Un cajero concentra las diferencias** | **Es esa persona, no el sistema** |

> ⚠️ Una diferencia **no prueba nada por sí sola**. Un cobro por QR hecho fuera
> del POS la produce igual. La consulta acota dónde mirar.

---

## 2. Impacto histórico del incidente del 2026-07-30

**Qué responde:** cuántos cierres se cuadraron con el valor manual del cajero
mientras `/qr-payments/by-date` devolvía 401, y por cuánto dinero.

Es el número que falta en la cabecera de
`V34__origen_y_confianza_del_qr_en_el_cierre.sql`, marcado ahí como
`[PENDIENTE — consulta de impacto]`.

**Ojo:** los cierres anteriores a V34 tienen `qr_fuente = NULL` — no se pueden
reclasificar. Lo que sí se puede es comparar el QR que quedó en el cierre contra
lo que había registrado en `qr_payments` ese día. Si difieren, el cierre no se
concilió.

```sql
SELECT c.closure_date,
       c.user_name,
       c.total_counted_qr                         AS qr_en_el_cierre,
       COALESCE(q.suma, 0)                        AS qr_registrado_por_admin,
       c.total_counted_qr - COALESCE(q.suma, 0)   AS diferencia
FROM   daily_closures c
LEFT   JOIN (SELECT payment_date, sum(amount) AS suma
             FROM   qr_payments
             GROUP  BY payment_date) q
       ON q.payment_date = c.closure_date
WHERE  c.closure_date >= DATE '2026-07-30'
  AND  c.qr_fuente IS NULL          -- solo el histórico sin procedencia
ORDER  BY c.closure_date DESC;
```

Y el conteo, que es el número que va en la migración:

```sql
SELECT count(*)                                        AS cierres_afectados,
       sum(abs(c.total_counted_qr - COALESCE(q.suma, 0))) AS diferencia_acumulada
FROM   daily_closures c
LEFT   JOIN (SELECT payment_date, sum(amount) AS suma
             FROM   qr_payments GROUP BY payment_date) q
       ON q.payment_date = c.closure_date
WHERE  c.closure_date >= DATE '2026-07-30'
  AND  c.qr_fuente IS NULL
  AND  c.total_counted_qr IS DISTINCT FROM COALESCE(q.suma, 0);
```

> ⚠️ **Salvedad importante.** Una diferencia aquí no prueba por sí sola que hubo
> fallo: también aparece cuando el administrador registró el pago QR *después* de
> que el cajero cerrara. La consulta acota a los candidatos, no dictamina. Para
> los cierres posteriores a V34 esta ambigüedad desaparece, porque `qr_fuente` lo
> dice sin margen de interpretación.

---

## 3. Reparto de procedencias — salud de la integración

**Qué responde:** de todos los cierres del mes, cuántos están realmente
conciliados. Es el indicador de si la conciliación aporta algo o es decorativa.

```sql
SELECT date_trunc('month', closure_date) AS mes,
       COALESCE(qr_fuente, 'sin_registrar_pre_v34') AS fuente,
       count(*)                          AS cierres,
       sum(total_counted_qr)             AS monto_qr
FROM   daily_closures
WHERE  closure_date >= current_date - INTERVAL '6 months'
GROUP  BY 1, 2
ORDER  BY 1 DESC, 3 DESC;
```

**Criterio:** en operación normal la mayoría debería ser `conciliado_core` o
`manual_cajero`. Una proporción creciente de `fallo_integracion` es degradación
progresiva; `manual_cajero` al 100 % durante semanas sugiere que el
administrador dejó de registrar los pagos QR — otro problema distinto, pero que
también conviene ver.

---

## 4. Cierres con QR conciliado que aun así no cuadran

**Qué detecta:** diferencias reales de caja en QR, ya sin el ruido de los fallos
de integración. Con `qr_fuente = 'conciliado_core'` se sabe que los dos lados del
cuadre son fiables, así que una diferencia aquí es una diferencia de verdad.

```sql
SELECT closure_date, user_name,
       total_expected_qr, total_counted_qr, difference_qr
FROM   daily_closures
WHERE  qr_fuente = 'conciliado_core'
  AND  difference_qr <> 0
  AND  closure_date >= current_date - INTERVAL '30 days'
ORDER  BY abs(difference_qr) DESC;
```

Antes de V34 esta consulta no se podía escribir: no había forma de separar una
diferencia real de una producida por el fallback silencioso.

---

## Cómo automatizar la n.º 1

**NO VERIFICABLE desde el código** cuál es el mecanismo de alertas disponible —
requiere saber qué hay montado en Railway o si existe algún cron externo. Lo que
sí se puede afirmar es que la consulta resumida de §1 devuelve un solo número, y
que basta con que alguien lo mire una vez al día para que un incidente como el
del 2026-07-30 dure horas en vez de tres semanas.

---

## 5. Discrepancia entre el total del cliente y el del servidor

**Qué detecta:** dos cosas distintas, y las dos importan.

1. **Un POS con el código alterado** para inflar o desinflar totales.
2. **Un desfase de catálogo** entre terminal y servidor — el POS vendió con un
   precio viejo. Es un problema real de operación y hoy es invisible.

**Contexto:** el servidor descarta los importes que manda el cliente y usa
siempre los suyos. Desde `V36` además los **compara** y guarda la diferencia en
`orders.total_discrepancia`. **Es señal, no autoridad:** no participa en ningún
cálculo.

`NULL` significa "el cliente no mandó total, no había con qué comparar", y es
distinto de `0`, que significa "comparados y coinciden".

```sql
SELECT count(*)                     AS ordenes,
       sum(abs(total_discrepancia)) AS acumulado,
       min(total_discrepancia)      AS mayor_a_la_baja,
       max(total_discrepancia)      AS mayor_al_alza
FROM   orders
WHERE  total_discrepancia IS NOT NULL
  AND  total_discrepancia <> 0
  AND  created_at >= current_date - INTERVAL '7 days';
```

**Criterio:** en operación normal debería dar cero órdenes.

### Por terminal — lo que lo convierte en control interno

Una discrepancia suelta es ruido; **la misma caja repitiendo es una señal.**

```sql
SELECT o.terminal_id,
       coalesce(t.codigo, t.alias, left(o.terminal_id::text, 8)) AS caja,
       count(*)                       AS ordenes_con_discrepancia,
       sum(o.total_discrepancia)      AS neto,
       sum(abs(o.total_discrepancia)) AS bruto
FROM   orders o
LEFT   JOIN terminals t ON t.id = o.terminal_id
WHERE  o.total_discrepancia IS NOT NULL
  AND  o.total_discrepancia <> 0
  AND  o.created_at >= current_date - INTERVAL '30 days'
GROUP  BY 1, 2
ORDER  BY bruto DESC;
```

| Patrón | Interpretación probable |
|---|---|
| Neto ≈ bruto, siempre al alza | Desfase de catálogo: el POS tiene precios más altos |
| Neto ≈ bruto, siempre a la baja | Desfase de catálogo, o descuentos que el servidor no reconoce |
| Neto ≈ 0 pero bruto alto | Diferencias en los dos sentidos: sospechoso |
| **Una sola caja concentra el bruto** | **Es esa caja, no el catálogo** |

> ⚠️ Una discrepancia **no prueba manipulación**. Un catálogo desactualizado la
> produce igual, y es mucho más frecuente.

### Cobertura de la comparación

Si `sin_comparar` es alto, la señal no sirve: los clientes no están mandando
total.

```sql
SELECT count(*) FILTER (WHERE total_discrepancia IS NULL) AS sin_comparar,
       count(*) FILTER (WHERE total_discrepancia = 0)     AS coinciden,
       count(*) FILTER (WHERE total_discrepancia <> 0)    AS discrepan,
       count(*)                                           AS total
FROM   orders
WHERE  created_at >= current_date - INTERVAL '7 days';
```
