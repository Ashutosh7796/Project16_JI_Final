package com.spring.jwt.ProductBuyPending;

import com.spring.jwt.ProductBuyConfirmed.ProductBuyConfirmedDto;
import com.spring.jwt.ProductBuyPending.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer/orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final ProductBuyService service;
//    private final OrderTrackingService trackingService;

    // ✅ 1. CREATE ORDER
    @PostMapping("/create")
    public ProductBuyPendingDto create(@RequestBody CreateOrderRequestDto dto) {
        return service.createPendingOrder(dto);
    }

    // ✅ 2. GET PENDING ORDER (for retry payment)
    @GetMapping("/pending/{id}")
    public ProductBuyPendingDto getPending(@PathVariable Long id) {
        return service.getPendingOrder(id);
    }

    // ✅ 3. GET ORDER DETAILS
    @GetMapping("/{id}")
    public ProductBuyConfirmedDto getOrder(@PathVariable Long id) {
        return service.getConfirmedOrder(id);
    }

    // ✅ 4. GET MY ORDERS
//    @GetMapping("/my/{userId}")
//    public List<ProductBuyConfirmedDto> getMyOrders(@PathVariable Long userId) {
//        return trackingService.getOrdersByUser(userId);
//    }
//
//    // ✅ 5. TRACK ORDER (TIMELINE)
//    @GetMapping("/track/{orderId}")
//    public OrderTrackingResponse track(@PathVariable Long orderId) {
//        return trackingService.trackOrder(orderId);
//    }
}