/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.record.ProductResponse
 *  com.suresell.order.rest_client.ProductClient
 */
package com.suresell.order.rest_client;

import com.suresell.order.model.record.ProductResponse;

public interface ProductClient {
    public String getProductName(String var1);

    public String getProductCategory(String var1);

    public ProductResponse getProductDetails(String var1);
}

