/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.exception.AdminPasswordException
 *  com.suresell.order.exception.GlobalExceptionHandler
 *  com.suresell.order.exception.MesaDuplicadaException
 *  com.suresell.order.exception.PagerOcupadoException
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.http.HttpStatus
 *  org.springframework.http.HttpStatusCode
 *  org.springframework.http.ResponseEntity
 *  org.springframework.web.bind.annotation.ExceptionHandler
 *  org.springframework.web.bind.annotation.RestControllerAdvice
 */
package com.suresell.order.exception;

import com.suresell.order.exception.AdminPasswordException;
import com.suresell.order.exception.MesaDuplicadaException;
import com.suresell.order.exception.PagerOcupadoException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(value={AdminPasswordException.class})
    public ResponseEntity<Map<String, String>> handleAdminPasswordException(AdminPasswordException ex) {
        logger.warn("Acceso denegado: {}", (Object)ex.getMessage());
        return ResponseEntity.status((HttpStatusCode)HttpStatus.FORBIDDEN).body(Map.of("error", "ADMIN_PASSWORD_INVALID", "message", ex.getMessage()));
    }

    @ExceptionHandler(value={PagerOcupadoException.class})
    public ResponseEntity<Map<String, String>> handlePagerOcupadoException(PagerOcupadoException ex) {
        logger.warn("Pager ocupado: {}", (Object)ex.getMessage());
        return ResponseEntity.status((HttpStatusCode)HttpStatus.CONFLICT).body(Map.of("error", ex.getFlag(), "message", ex.getMessage()));
    }

    @ExceptionHandler(value={MesaDuplicadaException.class})
    public ResponseEntity<Map<String, String>> handleMesaDuplicadaException(MesaDuplicadaException ex) {
        logger.warn("Excepci\u00f3n legacy de mesa duplicada: {}", (Object)ex.getMessage());
        return ResponseEntity.status((HttpStatusCode)HttpStatus.CONFLICT).body(Map.of("error", ex.getFlag(), "message", ex.getMessage()));
    }
}

