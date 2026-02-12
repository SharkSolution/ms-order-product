package com.suresell.orders.shared.exception;

public class OrderEditNotAllowedException extends RuntimeException {

    private final String errorCode;

    public OrderEditNotAllowedException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return this.errorCode;
    }
}
