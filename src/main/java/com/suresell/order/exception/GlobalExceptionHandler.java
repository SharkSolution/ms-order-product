package com.suresell.order.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(value = {AdminPasswordException.class})
    public ResponseEntity<Map<String, String>> handleAdminPasswordException(AdminPasswordException ex) {
        logger.warn("Acceso denegado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "ADMIN_PASSWORD_INVALID", "message", ex.getMessage()));
    }

    @ExceptionHandler(value = {PagerOcupadoException.class})
    public ResponseEntity<Map<String, String>> handlePagerOcupadoException(PagerOcupadoException ex) {
        logger.warn("Pager ocupado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getFlag(), "message", ex.getMessage()));
    }

    @ExceptionHandler(value = {MesaDuplicadaException.class})
    public ResponseEntity<Map<String, String>> handleMesaDuplicadaException(MesaDuplicadaException ex) {
        logger.warn("Excepción legacy de mesa duplicada: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getFlag(), "message", ex.getMessage()));
    }

    @ExceptionHandler(value = {OrderEditNotAllowedException.class})
    public ResponseEntity<Map<String, String>> handleOrderEditNotAllowedException(OrderEditNotAllowedException ex) {
        logger.warn("Edición de orden bloqueada: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getErrorCode(), "message", ex.getMessage()));
    }
}
