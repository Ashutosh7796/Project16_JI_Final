package com.spring.jwt.FarmerPayment;

import com.spring.jwt.Enums.PaymentStatus;
import com.spring.jwt.Payment.*;
import com.spring.jwt.EmployeeFarmerSurvey.EmployeeFarmerSurveyRepository;
import com.spring.jwt.EmployeeFarmerSurvey.SurveyIdResolver;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FarmerPaymentServiceImpl implements FarmerPaymentService {

    private static final int MAX_FARMER_CALLBACK_OPT_LOCK_ATTEMPTS = 8;

    private final FarmerPaymentRepository farmerPaymentRepo;
    private final EmployeeFarmerSurveyRepository surveyRepo;
    private final SurveyIdResolver surveyIdResolver;
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
        Long surveyId = surveyIdResolver.resolveToInternalId(dto.getSurveyId());
        Long currentUserId = getCurrentUserId();

        String resolvedKey = (idempotencyKey == null || idempotencyKey.isBlank())
                ? generateIdempotencyKey(surveyId, currentUserId)
                : idempotencyKey;
        final String sanitizedKey = PaymentInputSanitizer.sanitizeOrderId(resolvedKey);

        // Idempotency check
        Optional<FarmerPayment> existing = farmerPaymentRepo.findByIdempotencyKey(sanitizedKey);
        if (existing.isPresent()) {
            FarmerPayment existingPayment = existing.get();
            if (!existingPayment.getSurvey().getSurveyId().equals(surveyId)) {
                throw PaymentException.idempotencySurveyMismatch();
            }
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

        // Serialize initiations per survey (prevents duplicate SUCCESS under concurrent requests)
        EmployeeFarmerSurvey survey = surveyRepo.findByIdForUpdate(surveyId)
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
        for (int attempt = 1; attempt <= MAX_FARMER_CALLBACK_OPT_LOCK_ATTEMPTS; attempt++) {
            try {
                return handleCallbackBody(ccAvenueParams, clientIp);
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt >= MAX_FARMER_CALLBACK_OPT_LOCK_ATTEMPTS) {
                    log.error("Farmer callback optimistic-lock retries exhausted (order_id hint: {})",
                            ccAvenueParams != null ? ccAvenueParams.get("order_id") : null);
                    throw e;
                }
                log.warn("Farmer callback optimistic lock conflict; retry {}/{}",
                        attempt, MAX_FARMER_CALLBACK_OPT_LOCK_ATTEMPTS);
            }
        }
        throw new IllegalStateException("Unreachable farmer callback retry loop");
    }

    private FarmerPaymentResponseDTO handleCallbackBody(Map<String, String> ccAvenueParams, String clientIp) {
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

        if ("Success".equalsIgnoreCase(orderStatus) && !amountMismatchDetected) {
            Long surveyId = payment.getSurvey().getSurveyId();
            surveyRepo.findByIdForUpdate(surveyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Survey not found with ID: " + surveyId));

            FarmerPayment locked = farmerPaymentRepo.findByCcavenueOrderId(sanitizedOrderId)
                    .orElseThrow(() -> PaymentException.paymentNotFound(sanitizedOrderId));

            if (PaymentStatus.SUCCESS.equals(locked.getPaymentStatus())) {
                log.warn("Duplicate callback after survey lock for order {}", sanitizedOrderId);
                auditService.logEvent("FARMER", locked.getPaymentId(), sanitizedOrderId,
                        "DUPLICATE_CALLBACK", "SUCCESS", "SUCCESS", null, clientIp, null);
                return mapToResponseDTO(locked, null);
            }

            Optional<FarmerPayment> otherSuccess =
                    farmerPaymentRepo.findBySurvey_SurveyIdAndPaymentStatus(surveyId, PaymentStatus.SUCCESS);
            if (otherSuccess.isPresent() && !otherSuccess.get().getPaymentId().equals(locked.getPaymentId())) {
                locked.setPaymentStatus(PaymentStatus.FAILED);
                locked.setTrackingId(trackingId);
                locked.setBankRefNo(bankRefNo);
                locked.setCcavenuePaymentMode(paymentMode);
                locked.setStatusMessage(truncate(
                        "Survey already has a successful payment (order " + otherSuccess.get().getCcavenueOrderId() + ")",
                        500));
                farmerPaymentRepo.save(locked);
                auditService.logEvent("FARMER", locked.getPaymentId(), sanitizedOrderId,
                        "VERIFIED_FAILED", oldStatus, "FAILED", null, clientIp,
                        "blocked_duplicate_survey_success");
                log.warn("Farmer payment {} failed: another successful payment exists for survey {}",
                        sanitizedOrderId, surveyId);
                return mapToResponseDTO(locked, null);
            }

            locked.setPaymentStatus(PaymentStatus.SUCCESS);
            locked.setTrackingId(trackingId);
            locked.setBankRefNo(bankRefNo);
            locked.setCcavenuePaymentMode(paymentMode);
            locked.setStatusMessage(truncate(statusMessage, 500));
            farmerPaymentRepo.save(locked);

            auditService.logEvent("FARMER", locked.getPaymentId(), sanitizedOrderId,
                    "VERIFIED_SUCCESS", oldStatus, locked.getPaymentStatus().name(), null, clientIp,
                    "tracking_id=" + trackingId + ", bank_ref_no=" + bankRefNo);
            log.info("Farmer payment callback processed: paymentId={}, orderId={}, status={}",
                    locked.getPaymentId(), sanitizedOrderId, locked.getPaymentStatus());
            return mapToResponseDTO(locked, null);
        }

        payment.setPaymentStatus(PaymentStatus.FAILED);
        payment.setTrackingId(trackingId);
        payment.setBankRefNo(bankRefNo);
        payment.setCcavenuePaymentMode(paymentMode);
        payment.setStatusMessage(truncate(statusMessage, 500));
        farmerPaymentRepo.save(payment);

        auditService.logEvent("FARMER", payment.getPaymentId(), sanitizedOrderId,
                "VERIFIED_FAILED", oldStatus, payment.getPaymentStatus().name(), null, clientIp,
                "tracking_id=" + trackingId + ", bank_ref_no=" + bankRefNo);
        log.info("Farmer payment callback processed: paymentId={}, orderId={}, status={}",
                payment.getPaymentId(), sanitizedOrderId, payment.getPaymentStatus());
        return mapToResponseDTO(payment, null);
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

    @Override
    @Transactional(readOnly = true)
    public Map<String, FarmerPaymentResponseDTO> getSuccessfulPaymentsBySurveyIds(List<String> surveyIds) {
        if (surveyIds == null || surveyIds.isEmpty()) {
            return Map.of();
        }

        Long currentUserId = getCurrentUserId();
        boolean admin = isCurrentUserAdmin();

        Map<String, Long> requestedToInternalSurveyId = new LinkedHashMap<>();
        for (String rawSurveyId : surveyIds) {
            String surveyId = String.valueOf(rawSurveyId == null ? "" : rawSurveyId).trim();
            if (surveyId.isEmpty() || requestedToInternalSurveyId.containsKey(surveyId)) {
                continue;
            }
            try {
                Long internalSurveyId = surveyIdResolver.resolveToInternalId(surveyId);
                requestedToInternalSurveyId.put(surveyId, internalSurveyId);
            } catch (RuntimeException ex) {
                log.debug("Skipping unresolved survey ID in bulk status lookup: {}", surveyId);
            }
        }

        if (requestedToInternalSurveyId.isEmpty()) {
            return Map.of();
        }

        Set<Long> internalSurveyIds = requestedToInternalSurveyId.values().stream().collect(Collectors.toSet());
        List<FarmerPayment> successfulPayments = farmerPaymentRepo
                .findBySurvey_SurveyIdInAndPaymentStatusOrderByCreatedAtDesc(internalSurveyIds, PaymentStatus.SUCCESS);

        Map<Long, FarmerPayment> latestSuccessfulBySurvey = new LinkedHashMap<>();
        for (FarmerPayment payment : successfulPayments) {
            Long internalSurveyId = payment.getSurvey().getSurveyId();
            if (latestSuccessfulBySurvey.containsKey(internalSurveyId)) {
                continue;
            }
            if (admin || payment.getUser().getUserId().equals(currentUserId)) {
                latestSuccessfulBySurvey.put(internalSurveyId, payment);
            }
        }

        Map<String, FarmerPaymentResponseDTO> result = new LinkedHashMap<>();
        requestedToInternalSurveyId.forEach((requestedSurveyId, internalSurveyId) -> {
            FarmerPayment payment = latestSuccessfulBySurvey.get(internalSurveyId);
            if (payment != null) {
                result.put(requestedSurveyId, mapToResponseDTO(payment, null));
            }
        });

        return result;
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
