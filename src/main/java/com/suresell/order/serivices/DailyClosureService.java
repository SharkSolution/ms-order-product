/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.record.ClosurePreviewResponse
 *  com.suresell.order.model.record.ClosureRequest
 *  com.suresell.order.model.record.ClosureResponse
 *  com.suresell.order.serivices.DailyClosureService
 */
package com.suresell.order.serivices;

import com.suresell.order.model.record.ClosurePreviewResponse;
import com.suresell.order.model.record.ClosureRequest;
import com.suresell.order.model.record.ClosureResponse;
import java.util.List;

public interface DailyClosureService {
    public ClosurePreviewResponse getClosurePreview();

    public ClosureResponse executeClosure(ClosureRequest var1);

    public List<ClosureResponse> getAllClosures();
}

