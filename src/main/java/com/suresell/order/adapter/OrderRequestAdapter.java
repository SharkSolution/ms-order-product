package com.suresell.order.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suresell.order.model.record.OrderRequestRecord;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OrderRequestAdapter {

    private final ObjectMapper objectMapper;

    public OrderRequestAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OrderRequestRecord normalize(Map<String, Object> payload) {
        // If the payload contains a "body" key and its value is a Map,
        // we assume it's the wrapped request from n8n and use its content.
        if (payload.containsKey("body") && payload.get("body") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> unwrappedPayload = (Map<String, Object>) payload.get("body");
            return objectMapper.convertValue(unwrappedPayload, OrderRequestRecord.class);
        } else {
            // Otherwise, we assume it's a direct, plain request.
            return objectMapper.convertValue(payload, OrderRequestRecord.class);
        }
    }
}
