package com.suresell.orders.shared.exception;
public class PagerOcupadoException
extends RuntimeException {
    private final String flag;
    public PagerOcupadoException(String message, String flag) {
        super(message);
        this.flag = flag;
    }
    public String getFlag() {
        return this.flag;
    }
}
