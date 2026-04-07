package com.spring.jwt.ProductBuyPending;

import com.spring.jwt.ProductBuyConfirmed.ProductBuyConfirmedDto;
import com.spring.jwt.dto.*;

public interface ProductBuyService {

    ProductBuyPendingDto createPendingOrder(CreateOrderRequestDto dto);

    ProductBuyConfirmedDto confirmPayment(PaymentVerifyDto dto);

    void markPaymentFailed(Long pendingOrderId);

    ProductBuyConfirmedDto getConfirmedOrder(Long id);

    ProductBuyPendingDto getPendingOrder(Long id);
}