package com.suresell.orders.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suresell.orders.application.dto.OrderRequestRecord;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OrderRequestAdapter {

    private final ObjectMapper objectMapper;

    public OrderRequestAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OrderRequestRecord normalize(Map<String, Object> payload) {
        if (payload.containsKey("body") && payload.get("body") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> unwrappedPayload = (Map<String, Object>) payload.get("body");
            return objectMapper.convertValue(unwrappedPayload, OrderRequestRecord.class);
        } else {
            return objectMapper.convertValue(payload, OrderRequestRecord.class);
        }
    }
}
