package com.suresell.orders.domain.port.in;
import com.suresell.orders.application.dto.ClosurePreviewResponse;
import com.suresell.orders.application.dto.ClosureRequest;
import com.suresell.orders.application.dto.ClosureResponse;
import java.util.List;
public interface DailyClosurePort {
    public ClosurePreviewResponse getClosurePreview();
    public ClosureResponse executeClosure(ClosureRequest var1);
}
