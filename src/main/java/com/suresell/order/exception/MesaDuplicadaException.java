package com.suresell.order.exception;

public class MesaDuplicadaException extends RuntimeException {
    private final String flag;

    public MesaDuplicadaException(String message, String flag) {
        super(message);
        this.flag = flag;
    }

    public String getFlag() {
        return flag;
    }
}

