package com.suresell.orders.shared.exception;
public class OrderIdAlreadyExistsException extends RuntimeException {
    public OrderIdAlreadyExistsException(String message) { super(message); }
}
