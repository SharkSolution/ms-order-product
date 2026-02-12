package com.suresell.orders.domain.port.in;

import com.suresell.orders.application.dto.ApplyDiscountCommand;
import com.suresell.orders.application.dto.ApplyDiscountResult;
import com.suresell.orders.application.dto.LinkOrderCouponCommand;
import com.suresell.orders.domain.model.DiscountCoupon;
import com.suresell.orders.application.dto.ProductDiscountDto;

import java.util.List;

public interface DiscountPort {
    ApplyDiscountResult applyDiscount(ApplyDiscountCommand command);
    void linkOrderWithCoupon(LinkOrderCouponCommand command);
    List<DiscountCoupon> getActiveCoupons();
    DiscountCoupon createCoupon(DiscountCoupon coupon, List<ProductDiscountDto> products);
    DiscountCoupon updateCoupon(Long id, DiscountCoupon updatedData, List<ProductDiscountDto> products);
    DiscountCoupon deactivateCoupon(Long id);
    List<DiscountCoupon> listAllCoupons(String status);
    void deleteCoupon(Long id);
}
