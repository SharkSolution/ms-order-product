package com.suresell.order.exception;
public class OrderIdAlreadyExistsException extends RuntimeException {
    public OrderIdAlreadyExistsException(String message) { super(message); }
}
