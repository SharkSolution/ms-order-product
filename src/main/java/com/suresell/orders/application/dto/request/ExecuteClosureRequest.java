package com.suresell.orders.application.dto.request;
import com.suresell.orders.application.dto.dto.CashCountDetail;
import java.math.BigDecimal;
import java.util.List;

public record ExecuteClosureRequest(
        CashCountDetail cashDetail,
        BigDecimal countedCash,
        BigDecimal countedCard,
        BigDecimal countedNequi,
        BigDecimal countedQr,
        String notes,
        String sellerId,
        List<PettyCashExpenseRequest> pettyCashExpenses
) {
}
