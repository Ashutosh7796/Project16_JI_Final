package com.spring.jwt.FarmerPayment;

import com.spring.jwt.Enums.PaymentStatus;
import com.spring.jwt.Payment.*;
import com.spring.jwt.EmployeeFarmerSurvey.EmployeeFarmerSurveyRepository;
import com.spring.jwt.entity.EmployeeFarmerSurvey;
import com.spring.jwt.entity.FarmerPayment;
import com.spring.jwt.entity.User;
import com.spring.jwt.exception.ResourceNotFoundException;
import com.spring.jwt.repository.UserRepository;
import com.spring.jwt.service.security.UserDetailsCustom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FarmerPaymentServiceImpl implements FarmerPaymentService {

    private final FarmerPaymentRepository farmerPaymentRepo;
    private final EmployeeFarmerSurveyRepository surveyRepo;
    private final UserRepository userRepo;
    private final CcAvenuePaymentService ccAvenuePaymentService;
    private final CcAvenueConfig ccAvenueConfig;
    private final PaymentAuditService auditService;

    @Value("${farmer.registration.fee:500.00}")
    private BigDecimal registrationFee;

    @Value("${payment.fraud.max-attempts-per-survey:5}")
    private int maxAttemptsPerSurvey;

    @Value("${payment.fraud.max-attempts-window-minutes:60}")
    private int attemptsWindowMinutes;

    @Value("${payment.fraud.max-daily-payments-per-user:20}")
    private int maxDailyPaymentsPerUser;

    @Override
    @Transactional
    public FarmerPaymentResponseDTO initiatePayment(FarmerPaymentInitiateDTO dto, String idempotencyKey,
                                                     String clientIp, String userAgent) {
        Long surveyId = dto.getSurveyId();
        Long currentUserId = getCurrentUserId();

        String resolvedKey = (idempotencyKey == null || idempotencyKey.isBlank())
                ? generateIdempotencyKey(surveyId, currentUserId)
                : idempotencyKey;
        final String sanitizedKey = PaymentInputSanitizer.sanitizeOrderId(resolvedKey);

        // Idempotency check
        Optional<FarmerPayment> existing = farmerPaymentRepo.findByIdempotencyKey(sanitizedKey);
        if (existing.isPresent()) {
            FarmerPayment existingPayment = existing.get();
            if (!isCurrentUserAdmin() && !existingPayment.getUser().getUserId().equals(currentUserId)) {
                throw new AccessDeniedException("Idempotency key belongs to a different user");
            }
            if (PaymentStatus.SUCCESS.equals(existingPayment.getPaymentStatus())) {
                log.info("Idempotent return: payment {} already succeeded for survey {}",
                        existingPayment.getPaymentId(), surveyId);
                return mapToResponseDTO(existingPayment, null);
            }
            if (PaymentStatus.PENDING.equals(existingPayment.getPaymentStatus())) {
                log.info("Idempotent return: payment {} still pending for survey {}",
                        existingPayment.getPaymentId(), surveyId);
                String paymentForm = regeneratePaymentForm(existingPayment);
                return mapToResponseDTO(existingPayment, paymentForm);
            }
        }

        // Validate survey exists
        EmployeeFarmerSurvey survey = surveyRepo.findById(surveyId)
                .orElseThrow(() -> new ResourceNotFoundException("Survey not found with ID: " + surveyId));
        if (!isCurrentUserAdmin() && !survey.getUser().getUserId().equals(currentUserId)) {
            throw new AccessDeniedException("You are not allowed to initiate payment for this survey");
        }

        // Check if already paid
        Optional<FarmerPayment> successfulPayment =
                farmerPaymentRepo.findBySurvey_SurveyIdAndPaymentStatus(surveyId, PaymentStatus.SUCCESS);
        if (successfulPayment.isPresent()) {
            throw PaymentException.duplicatePayment("Survey " + surveyId + " already paid");
        }

        // Fraud checks
        performFraudChecks(surveyId, currentUserId, clientIp);

        User user = userRepo.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        BigDecimal amount = PaymentInputSanitizer.sanitizeAmount(registrationFee, new BigDecimal("50000.00"));

        int attemptCount = (int) farmerPaymentRepo.countRecentBySurvey(surveyId,
                LocalDateTime.now().minusMinutes(attemptsWindowMinutes)) + 1;

        String ccavenueOrderId = "FARM-" + surveyId + "-" + System.currentTimeMillis();

        FarmerPayment payment = FarmerPayment.builder()
                .survey(survey)
                .user(user)
                .amount(amount)
                .paymentStatus(PaymentStatus.PENDING)
                .ccavenueOrderId(ccavenueOrderId)
                .idempotencyKey(sanitizedKey)
                .initiatorIp(clientIp)
                .userAgent(truncate(userAgent, 500))
                .attemptCount(attemptCount)
                .build();

        farmerPaymentRepo.save(payment);

        auditService.logEvent("FARMER", payment.getPaymentId(), ccavenueOrderId,
                "INITIATED", null, "PENDING", currentUserId, clientIp,
                "surveyId=" + surveyId + ", amount=" + amount + ", attempt=" + attemptCount);

        String redirectUrl = buildFarmerCallbackUrl(ccAvenueConfig.getRedirectUrl(), "/api/payment/farmer/response");
        String cancelUrl = buildFarmerCallbackUrl(ccAvenueConfig.getCancelUrl(), "/api/payment/farmer/cancel");

        CcAvenuePaymentRequest paymentRequest = CcAvenuePaymentRequest.builder()
                .orderId(ccavenueOrderId)
                .amount(amount)
                .redirectUrl(redirectUrl)
                .cancelUrl(cancelUrl)
                .billingName(PaymentInputSanitizer.sanitizeName(survey.getFarmerName()))
                .billingAddress(PaymentInputSanitizer.sanitizeAddress(
                        survey.getAddress() != null ? survey.getAddress() : survey.getVillage()))
                .billingTel(PaymentInputSanitizer.sanitizePhone(survey.getFarmerMobile()))
                .build();

        String paymentForm = ccAvenuePaymentService.generatePaymentForm(paymentRequest);

        auditService.logEvent("FARMER", payment.getPaymentId(), ccavenueOrderId,
                "FORM_GENERATED", "PENDING", "PENDING", currentUserId, clientIp, null);

        log.info("Farmer payment initiated: paymentId={}, surveyId={}, orderId={}, amount={}",
                payment.getPaymentId(), surveyId, ccavenueOrderId, amount);

        return mapToResponseDTO(payment, paymentForm);
    }

    @Override
    @Transactional
    public FarmerPaymentResponseDTO handleCallback(Map<String, String> ccAvenueParams, String clientIp) {
        String orderId = ccAvenueParams.get("order_id");
        String orderStatus = ccAvenueParams.get("order_status");
        String trackingId = ccAvenueParams.get("tracking_id");
        String bankRefNo = ccAvenueParams.get("bank_ref_no");
        String paymentMode = ccAvenueParams.get("payment_mode");
        String statusMessage = ccAvenueParams.get("status_message");
        String callbackAmount = ccAvenueParams.get("amount");

        if (orderId == null || orderId.isBlank()) {
            throw PaymentException.invalidCallback("Missing order_id");
        }

        final String sanitizedOrderId = PaymentInputSanitizer.sanitizeOrderId(orderId);

        FarmerPayment payment;
        try {
            payment = farmerPaymentRepo.findByCcavenueOrderId(sanitizedOrderId)
                    .orElseThrow(() -> PaymentException.paymentNotFound(sanitizedOrderId));
        } catch (PaymentException e) {
            auditService.logFraudEvent("FARMER", null, sanitizedOrderId, null, clientIp,
                    "Callback for unknown order");
            throw e;
        }

        if (PaymentStatus.SUCCESS.equals(payment.getPaymentStatus())) {
            log.warn("Duplicate callback for already-succeeded payment: {}", sanitizedOrderId);
            auditService.logEvent("FARMER", payment.getPaymentId(), sanitizedOrderId,
                    "DUPLICATE_CALLBACK", "SUCCESS", "SUCCESS", null, clientIp, null);
            return mapToResponseDTO(payment, null);
        }

        boolean amountMismatchDetected = false;

        // Amount verification
        if (callbackAmount != null) {
            try {
                BigDecimal returnedAmount = new BigDecimal(callbackAmount);
                if (payment.getAmount().compareTo(returnedAmount) != 0) {
                    amountMismatchDetected = true;
                    auditService.logFraudEvent("FARMER", payment.getPaymentId(), sanitizedOrderId,
                            payment.getUser().getUserId(), clientIp,
                            "Amount mismatch: expected=" + payment.getAmount() + ", received=" + returnedAmount);
                    log.error("FRAUD: Amount mismatch for order {}: expected={}, received={}",
                            sanitizedOrderId, payment.getAmount(), returnedAmount);
                }
            } catch (NumberFormatException e) {
                log.warn("Could not parse callback amount: {}", callbackAmount);
            }
        }

        String oldStatus = payment.getPaymentStatus().name();

        try {
            if ("Success".equalsIgnoreCase(orderStatus) && !amountMismatchDetected) {
                payment.setPaymentStatus(PaymentStatus.SUCCESS);
            } else {
                payment.setPaymentStatus(PaymentStatus.FAILED);
            }

            payment.setTrackingId(trackingId);
            payment.setBankRefNo(bankRefNo);
            payment.setCcavenuePaymentMode(paymentMode);
            payment.setStatusMessage(truncate(statusMessage, 500));

            farmerPaymentRepo.save(payment);

            auditService.logEvent("FARMER", payment.getPaymentId(), sanitizedOrderId,
                    "Success".equalsIgnoreCase(orderStatus) ? "VERIFIED_SUCCESS" : "VERIFIED_FAILED",
                    oldStatus, payment.getPaymentStatus().name(), null, clientIp,
                    "tracking_id=" + trackingId + ", bank_ref_no=" + bankRefNo);

            log.info("Farmer payment callback processed: paymentId={}, orderId={}, status={}",
                    payment.getPaymentId(), sanitizedOrderId, payment.getPaymentStatus());

        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic lock conflict for payment {}, retrying...", sanitizedOrderId);
            return handleCallbackRetry(sanitizedOrderId, ccAvenueParams, clientIp);
        }

        return mapToResponseDTO(payment, null);
    }

    private FarmerPaymentResponseDTO handleCallbackRetry(String orderId, Map<String, String> params, String clientIp) {
        FarmerPayment payment = farmerPaymentRepo.findByCcavenueOrderId(orderId)
                .orElseThrow(() -> PaymentException.paymentNotFound(orderId));

        if (PaymentStatus.SUCCESS.equals(payment.getPaymentStatus())) {
            return mapToResponseDTO(payment, null);
        }

        return handleCallback(params, clientIp);
    }

    @Override
    @Transactional
    public FarmerPaymentResponseDTO getPaymentById(Long paymentId) {
        FarmerPayment payment = farmerPaymentRepo.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with ID: " + paymentId));
        validatePaymentAccess(payment);
        return mapToResponseDTO(payment, null);
    }

    @Override
    @Transactional
    public Page<FarmerPaymentResponseDTO> getPaymentsBySurveyId(Long surveyId, Pageable pageable) {
        EmployeeFarmerSurvey survey = surveyRepo.findById(surveyId)
                .orElseThrow(() -> new ResourceNotFoundException("Survey not found with ID: " + surveyId));
        if (!isCurrentUserAdmin() && !survey.getUser().getUserId().equals(getCurrentUserId())) {
            throw new AccessDeniedException("You are not allowed to access payments for this survey");
        }
        return farmerPaymentRepo.findBySurvey_SurveyIdOrderByCreatedAtDesc(surveyId, pageable)
                .map(p -> mapToResponseDTO(p, null));
    }

    @Override
    @Transactional
    public Page<FarmerPaymentResponseDTO> getPaymentsByUserId(Long userId, Pageable pageable) {
        if (!isCurrentUserAdmin() && !userId.equals(getCurrentUserId())) {
            throw new AccessDeniedException("You are not allowed to access payments for this user");
        }
        return farmerPaymentRepo.findByUser_UserIdOrderByCreatedAtDesc(userId, pageable)
                .map(p -> mapToResponseDTO(p, null));
    }

    private void performFraudChecks(Long surveyId, Long userId, String clientIp) {
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(attemptsWindowMinutes);
        long recentAttempts = farmerPaymentRepo.countRecentBySurvey(surveyId, windowStart);
        if (recentAttempts >= maxAttemptsPerSurvey) {
            auditService.logFraudEvent("FARMER", null, null, userId, clientIp,
                    "Exceeded max attempts (" + maxAttemptsPerSurvey + ") for survey " + surveyId);
            throw PaymentException.rateLimitExceeded();
        }

        LocalDateTime dayStart = LocalDateTime.now().minusHours(24);
        long dailyPayments = farmerPaymentRepo.countRecentByUser(userId, dayStart);
        if (dailyPayments >= maxDailyPaymentsPerUser) {
            auditService.logFraudEvent("FARMER", null, null, userId, clientIp,
                    "Exceeded daily limit (" + maxDailyPaymentsPerUser + ") for user " + userId);
            throw PaymentException.rateLimitExceeded();
        }
    }
    @Override
    @Transactional
    public FarmerPaymentResponseDTO getPaymentByOrderId(String orderId) {
        String sanitizedOrderId = PaymentInputSanitizer.sanitizeOrderId(orderId);
        FarmerPayment payment = farmerPaymentRepo.findByCcavenueOrderId(sanitizedOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with order ID: " + sanitizedOrderId));
        validatePaymentAccess(payment);
        return mapToResponseDTO(payment, null);
    }

    private String regeneratePaymentForm(FarmerPayment payment) {
        String redirectUrl = buildFarmerCallbackUrl(ccAvenueConfig.getRedirectUrl(), "/api/payment/farmer/response");
        String cancelUrl = buildFarmerCallbackUrl(ccAvenueConfig.getCancelUrl(), "/api/payment/farmer/cancel");

        CcAvenuePaymentRequest request = CcAvenuePaymentRequest.builder()
                .orderId(payment.getCcavenueOrderId())
                .amount(payment.getAmount())
                .redirectUrl(redirectUrl)
                .cancelUrl(cancelUrl)
                .billingName(PaymentInputSanitizer.sanitizeName(payment.getSurvey().getFarmerName()))
                .billingTel(PaymentInputSanitizer.sanitizePhone(payment.getSurvey().getFarmerMobile()))
                .build();

        return ccAvenuePaymentService.generatePaymentForm(request);
    }

    private String buildFarmerCallbackUrl(String baseUrl, String farmerPath) {
        if (baseUrl == null) return null;
        // Extract the base domain (everything before /api/)
        int apiIdx = baseUrl.indexOf("/api/");
        if (apiIdx > 0) {
            return baseUrl.substring(0, apiIdx) + farmerPath;
        }
        return baseUrl;
    }

    private String generateIdempotencyKey(Long surveyId, Long userId) {
        return "FARM-" + surveyId + "-" + userId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsCustom userDetails) {
            return userDetails.getUserId();
        }
        throw new AccessDeniedException("Authenticated user is required");
    }

    private boolean isCurrentUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private void validatePaymentAccess(FarmerPayment payment) {
        Long currentUserId = getCurrentUserId();
        if (!isCurrentUserAdmin() && !payment.getUser().getUserId().equals(currentUserId)) {
            throw new AccessDeniedException("You are not allowed to access this payment");
        }
    }

    private FarmerPaymentResponseDTO mapToResponseDTO(FarmerPayment payment, String paymentFormHtml) {
        if (payment.getPaymentPublicId() == null || payment.getSurvey().getSurveyPublicId() == null) {
            if (payment.getPaymentPublicId() == null) {
                payment.setPaymentPublicId("pay_" + UUID.randomUUID().toString().replace("-", ""));
            }
            if (payment.getSurvey().getSurveyPublicId() == null) {
                payment.getSurvey().setSurveyPublicId("sur_" + UUID.randomUUID().toString().replace("-", ""));
            }
            farmerPaymentRepo.save(payment);
        }

        return FarmerPaymentResponseDTO.builder()
                .paymentId(payment.getPaymentPublicId())
                .surveyId(payment.getSurvey().getSurveyPublicId())
                .farmerName(payment.getSurvey().getFarmerName())
                .userId(payment.getUser().getUserId())
                .amount(payment.getAmount())
                .paymentStatus(payment.getPaymentStatus().name())
                .ccavenueOrderId(payment.getCcavenueOrderId())
                .trackingId(payment.getTrackingId())
                .bankRefNo(payment.getBankRefNo())
                .ccavenuePaymentMode(payment.getCcavenuePaymentMode())
                .statusMessage(payment.getStatusMessage())
                .attemptCount(payment.getAttemptCount())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .paymentFormHtml(paymentFormHtml)
                .build();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
