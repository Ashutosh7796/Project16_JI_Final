package com.spring.jwt.FarmerPayment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/farmer-payment")
@RequiredArgsConstructor
@Slf4j
public class FarmerPaymentController {

    private final FarmerPaymentService farmerPaymentService;

    @PostMapping("/initiate")
    public ResponseEntity<FarmerPaymentResponseDTO> initiatePayment(
            @Valid @RequestBody FarmerPaymentInitiateDTO dto,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {

        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        FarmerPaymentResponseDTO response = farmerPaymentService.initiatePayment(
                dto, idempotencyKey, clientIp, userAgent);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<FarmerPaymentResponseDTO> getPayment(@PathVariable Long paymentId) {
        return ResponseEntity.ok(farmerPaymentService.getPaymentById(paymentId));
    }

    @GetMapping("/survey/{surveyId}")
    public ResponseEntity<Page<FarmerPaymentResponseDTO>> getPaymentsBySurvey(
            @PathVariable Long surveyId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(farmerPaymentService.getPaymentsBySurveyId(surveyId, pageable));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<FarmerPaymentResponseDTO>> getPaymentsByUser(
            @PathVariable Long userId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(farmerPaymentService.getPaymentsByUserId(userId, pageable));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
