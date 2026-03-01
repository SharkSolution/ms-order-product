package com.suresell.orders.application.dto.dto;
public record CashCountDetail(
        int bill100k,
        int bill50k,
        int bill20k,
        int bill10k,
        int bill5k,
        int bill2k,
        int coin1000,
        int coin500,
        int coin200,
        int coin100,
        int coin50
) {
}
