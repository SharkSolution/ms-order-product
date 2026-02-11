package com.suresell.orders.infrastructure.web;

import com.suresell.orders.shared.exception.OrderAlreadyDeliveredException;
import com.suresell.orders.shared.exception.OrderIdAlreadyExistsException;
import com.suresell.orders.shared.exception.OrderNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class DeliveryExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(DeliveryExceptionHandler.class);

    @ExceptionHandler(OrderIdAlreadyExistsException.class)
    public ResponseEntity<Object> handleOrderIdAlreadyExists(OrderIdAlreadyExistsException ex) {
        return new ResponseEntity<>(Map.of("error", ex.getMessage()), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Object> handleOrderNotFound(OrderNotFoundException ex) {
        return new ResponseEntity<>(Map.of("error", ex.getMessage()), HttpStatus.NOT_FOUND);
    }
    
    @ExceptionHandler(OrderAlreadyDeliveredException.class)
    public ResponseEntity<Object> handleOrderAlreadyDelivered(OrderAlreadyDeliveredException ex) {
        return new ResponseEntity<>(Map.of("error", ex.getMessage()), HttpStatus.CONFLICT);
    }
}
