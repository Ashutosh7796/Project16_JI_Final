package com.spring.jwt.FarmerPayment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface FarmerPaymentService {

    FarmerPaymentResponseDTO initiatePayment(FarmerPaymentInitiateDTO dto, String idempotencyKey,
                                              String clientIp, String userAgent);

    FarmerPaymentResponseDTO handleCallback(Map<String, String> ccAvenueParams, String clientIp);

    FarmerPaymentResponseDTO getPaymentById(Long paymentId);

    Page<FarmerPaymentResponseDTO> getPaymentsBySurveyId(Long surveyId, Pageable pageable);

    Page<FarmerPaymentResponseDTO> getPaymentsByUserId(Long userId, Pageable pageable);

    FarmerPaymentResponseDTO getPaymentByOrderId(String orderId);
}
