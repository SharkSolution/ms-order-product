package com.suresell.orders.shared.exception;
public class OrderAlreadyDeliveredException extends RuntimeException {
    public OrderAlreadyDeliveredException(String message) { super(message); }
}
