package com.suresell.orders.shared.exception;

import com.suresell.orders.shared.exception.AdminPasswordException;
import com.suresell.orders.shared.exception.PagerOcupadoException;
import com.suresell.orders.shared.exception.MesaDuplicadaException;
import com.suresell.orders.shared.exception.OrderEditNotAllowedException;
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
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "ADMIN_PASSWORD_INVALID", "message", ex.getMessage()));
    }

    @ExceptionHandler(value = {PagerOcupadoException.class})
    public ResponseEntity<Map<String, String>> handlePagerOcupadoException(PagerOcupadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getFlag(), "message", ex.getMessage()));
    }

    @ExceptionHandler(value = {MesaDuplicadaException.class})
    public ResponseEntity<Map<String, String>> handleMesaDuplicadaException(MesaDuplicadaException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getFlag(), "message", ex.getMessage()));
    }

    @ExceptionHandler(value = {OrderEditNotAllowedException.class})
    public ResponseEntity<Map<String, String>> handleOrderEditNotAllowedException(OrderEditNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getErrorCode(), "message", ex.getMessage()));
    }
}
