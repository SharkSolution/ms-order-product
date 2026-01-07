package com.suresell.order.exception;
public class OrderAlreadyDeliveredException extends RuntimeException {
    public OrderAlreadyDeliveredException(String message) { super(message); }
}
