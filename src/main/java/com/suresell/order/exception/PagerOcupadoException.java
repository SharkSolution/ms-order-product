/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.exception.PagerOcupadoException
 */
package com.suresell.order.exception;
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
