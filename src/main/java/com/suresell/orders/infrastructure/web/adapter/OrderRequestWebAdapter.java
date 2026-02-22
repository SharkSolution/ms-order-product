package com.suresell.orders.infrastructure.web.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suresell.orders.application.dto.OrderRequestRecord;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class OrderRequestWebAdapter {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public OrderRequestWebAdapter(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public OrderRequestRecord normalize(Map<String, Object> payload) {
        OrderRequestRecord dto;
        if (payload.containsKey("body") && payload.get("body") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> unwrappedPayload = (Map<String, Object>) payload.get("body");
            dto = objectMapper.convertValue(unwrappedPayload, OrderRequestRecord.class);
        } else {
            dto = objectMapper.convertValue(payload, OrderRequestRecord.class);
        }
        validate(dto);
        return dto;
    }

    private void validate(OrderRequestRecord dto) {
        Set<ConstraintViolation<OrderRequestRecord>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            String message = violations.iterator().next().getMessage();
            throw new IllegalArgumentException(message);
        }
    }
}
