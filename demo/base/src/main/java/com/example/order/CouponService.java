package com.example.order;

import org.springframework.stereotype.Service;

@Service
public class CouponService {

    public String resolve(String orderId) {
        return orderId.startsWith("9") ? "COUPON-9" : "";
    }
}
