package com.suresell.orders.domain.service;

import com.suresell.orders.application.dto.dto.CashCountDetail;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class CashflowCalculator {

    private static final BigDecimal V_100K = BigDecimal.valueOf(100_000);
    private static final BigDecimal V_50K = BigDecimal.valueOf(50_000);
    private static final BigDecimal V_20K = BigDecimal.valueOf(20_000);
    private static final BigDecimal V_10K = BigDecimal.valueOf(10_000);
    private static final BigDecimal V_5K = BigDecimal.valueOf(5_000);
    private static final BigDecimal V_2K = BigDecimal.valueOf(2_000);

    private static final BigDecimal V_1000 = BigDecimal.valueOf(1_000);
    private static final BigDecimal V_500 = BigDecimal.valueOf(500);
    private static final BigDecimal V_200 = BigDecimal.valueOf(200);
    private static final BigDecimal V_100 = BigDecimal.valueOf(100);
    private static final BigDecimal V_50 = BigDecimal.valueOf(50);

    public BigDecimal calculateTotalCash(CashCountDetail d) {
        BigDecimal total = BigDecimal.ZERO;
        total = total.add(V_100K.multiply(BigDecimal.valueOf(d.bill100k())));
        total = total.add(V_50K.multiply(BigDecimal.valueOf(d.bill50k())));
        total = total.add(V_20K.multiply(BigDecimal.valueOf(d.bill20k())));
        total = total.add(V_10K.multiply(BigDecimal.valueOf(d.bill10k())));
        total = total.add(V_5K.multiply(BigDecimal.valueOf(d.bill5k())));
        total = total.add(V_2K.multiply(BigDecimal.valueOf(d.bill2k())));

        total = total.add(BigDecimal.valueOf(1000).multiply(BigDecimal.valueOf(d.coin1000())));
        total = total.add(BigDecimal.valueOf(500).multiply(BigDecimal.valueOf(d.coin500())));
        total = total.add(BigDecimal.valueOf(200).multiply(BigDecimal.valueOf(d.coin200())));
        total = total.add(BigDecimal.valueOf(100).multiply(BigDecimal.valueOf(d.coin100())));
        total = total.add(BigDecimal.valueOf(50).multiply(BigDecimal.valueOf(d.coin50())));

        return total;
    }

    public BigDecimal calculateBaseForNextDay(CashCountDetail d) {
        BigDecimal base = BigDecimal.ZERO;


        int keep50k = Math.min(d.bill50k(), 2);
        base = base.add(V_50K.multiply(BigDecimal.valueOf(keep50k)));

        int keep20k = Math.min(d.bill20k(), 10);
        base = base.add(V_20K.multiply(BigDecimal.valueOf(keep20k)));

        int keep10k = Math.min(d.bill10k(), 15);
        base = base.add(V_10K.multiply(BigDecimal.valueOf(keep10k)));

        base = base.add(V_5K.multiply(BigDecimal.valueOf(d.bill5k())));
        base = base.add(V_2K.multiply(BigDecimal.valueOf(d.bill2k())));

        base = base.add(BigDecimal.valueOf(1000).multiply(BigDecimal.valueOf(d.coin1000())));
        base = base.add(BigDecimal.valueOf(500).multiply(BigDecimal.valueOf(d.coin500())));
        base = base.add(BigDecimal.valueOf(200).multiply(BigDecimal.valueOf(d.coin200())));
        base = base.add(BigDecimal.valueOf(100).multiply(BigDecimal.valueOf(d.coin100())));
        base = base.add(BigDecimal.valueOf(50).multiply(BigDecimal.valueOf(d.coin50())));

        return base;
    }
}