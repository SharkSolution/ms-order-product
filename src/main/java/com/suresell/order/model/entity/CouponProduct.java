package com.suresell.orders.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponProduct {
    private Long id;
    private DiscountCoupon coupon;
    private String productId;
    private String productName;
}
