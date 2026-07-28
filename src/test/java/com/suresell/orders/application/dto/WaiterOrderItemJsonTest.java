package com.suresell.orders.application.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.suresell.orders.application.dto.WaiterDtos.WaiterOrderItem;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * El ítem que ve la app de meseros debe viajar con el nombre del producto bajo
 * la clave `name`.
 *
 * La app lee `product.name` o `name`, nunca `productName`, así que en el detalle
 * del historial salía la cantidad ("2x") y el nombre en blanco. Se resolvió con
 * un alias en el backend para no tener que reinstalar los APK del local.
 *
 * Jackson serializa los records por COMPONENTES, así que un accesor extra no
 * basta por sí solo: este test es el que garantiza que el alias realmente sale
 * en el JSON.
 */
class WaiterOrderItemJsonTest {

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    @SuppressWarnings("unchecked")
    void elItemViajaConNombreBajoLaClaveName() throws Exception {
        WaiterOrderItem item = new WaiterOrderItem(
                "P-01", "Hamburguesa Shark", 2,
                new BigDecimal("18000"), new BigDecimal("36000"), "sin cebolla");

        Map<String, Object> json = mapper.readValue(mapper.writeValueAsString(item), Map.class);

        assertTrue(json.containsKey("name"), "falta la clave 'name': la app mostraría el nombre vacío");
        assertEquals("Hamburguesa Shark", json.get("name"));
        // Se conserva la clave original: otros clientes ya la consumen.
        assertEquals("Hamburguesa Shark", json.get("productName"));
        assertEquals(2, json.get("quantity"));
    }

    /**
     * La nota del item debe viajar en la respuesta.
     *
     * Se persistia bien desde siempre (37 de 321 items la tenian en Prod) pero
     * el DTO no la incluia, asi que el historial de la app la mostraba vacia.
     * Era presentacion, no guardado.
     */
    @Test
    @SuppressWarnings("unchecked")
    void elItemViajaConSuNota() throws Exception {
        WaiterOrderItem item = new WaiterOrderItem(
                "P-01", "Hamburguesa", 1,
                new BigDecimal("18000"), new BigDecimal("18000"), "sin cebolla");

        Map<String, Object> json = mapper.readValue(mapper.writeValueAsString(item), Map.class);

        assertEquals("sin cebolla", json.get("instructions"));
    }
}
