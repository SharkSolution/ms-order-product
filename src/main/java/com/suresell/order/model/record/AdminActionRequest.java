/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.record.AdminActionRequest
 */
package com.suresell.order.model.record;
public record AdminActionRequest(String adminPassword) {

    public AdminActionRequest(String adminPassword) {
        this.adminPassword = adminPassword;
    }
    public String adminPassword() {
        return this.adminPassword;
    }
}
