package com.spring.jwt.ProductBuyPending;

import com.spring.jwt.ProductBuyConfirmed.ProductBuyConfirmedDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer/orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final ProductBuyService service;

    @PostMapping("/create")
    public ProductBuyPendingDto create(@Valid @RequestBody CreateOrderRequestDto dto) {
        return service.createPendingOrder(dto);
    }

    @GetMapping("/pending/{id}")
    public ProductBuyPendingDto getPending(@PathVariable Long id) {
        return service.getPendingOrder(id);
    }

    @GetMapping("/{id}")
    public ProductBuyConfirmedDto getOrder(@PathVariable Long id) {
        return service.getConfirmedOrder(id);
    }
}
