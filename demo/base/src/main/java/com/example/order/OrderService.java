package com.example.order;

import com.example.order.dto.OrderView;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final CouponService couponService;

    public OrderService(CouponService couponService) {
        this.couponService = couponService;
    }

    public OrderView find(String id) {
        OrderView view = new OrderView(id, "PAID");
        view.setCoupon(couponService.resolve(id));
        return view;
    }

    public OrderView create(OrderView view) {
        view.setStatus("CREATED");
        return view;
    }
}
