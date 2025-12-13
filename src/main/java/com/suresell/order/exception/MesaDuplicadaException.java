/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.exception.MesaDuplicadaException
 */
package com.suresell.order.exception;
public class MesaDuplicadaException
extends RuntimeException {
    private final String flag;
    public MesaDuplicadaException(String message, String flag) {
        super(message);
        this.flag = flag;
    }
    public String getFlag() {
        return this.flag;
    }
}
