package com.spring.jwt.utils.EmailVerificationService;

import com.spring.jwt.dto.EmailVerificationMapper;
import com.spring.jwt.exception.*;
import com.spring.jwt.repository.UserRepository;
import com.spring.jwt.utils.EmailVerificationService.EmailUtils.VerifyOtpDTO;
import io.github.bucket4j.*;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private final EmailVerificationRepo emailVerificationRepo;
    private final UserRepository userRepository;
    private final EmailVerificationMapper emailVerificationMapper;

    @Value("${spring.mail.username}") private String emailUsername;
    @Value("${spring.mail.password}") private String emailPassword;
    @Value("${spring.mail.host}") private String emailHost;
    @Value("${spring.mail.port}") private String emailPort;
    @Value("${spring.mail.properties.mail.smtp.ssl.enable}") private String emailSmtpSslEnable;
    @Value("${spring.mail.properties.mail.smtp.auth}") private String emailSmtpAuth;

    private final ConcurrentMap<String, Bucket> emailRateLimiters = new ConcurrentHashMap<>();

    private static final String STATUS_VERIFIED = "Verified";
    private static final String STATUS_NOT_VERIFIED = "Not Verified";
    private static final int OTP_EXPIRY_MINUTES = 3;
    private static final int OTP_RATE_LIMIT = 5;

    private Bucket getRateLimiterForEmail(String email) {
        return emailRateLimiters.computeIfAbsent(email, key ->
                Bucket.builder().addLimit(Bandwidth.classic(OTP_RATE_LIMIT, Refill.greedy(OTP_RATE_LIMIT, Duration.ofMinutes(5)))).build()
        );
    }

    @Override
    public void sendEmail(String email) {
        if (email == null || email.isBlank()) throw new EmptyFieldException("Email field is empty");
        if (userRepository.findByEmail(email) != null) throw new UserAlreadyExistException("User already exists");
        if (!getRateLimiterForEmail(email).tryConsume(1)) throw new RuntimeException("Too many OTP requests. Try again later.");

        String otp = OtpUtil.generateOtp(6);
        String salt = OtpUtil.generateSalt();
        String hashedOtp = OtpUtil.hashOtp(otp, salt);

        saveEmail(email, hashedOtp, salt, LocalDateTime.now());

        sendEmailInternal(loadEmailTemplate("otp_email_template.html").replace("{{otp}}", otp), "OTP Verification", email);
    }


    @Override
    public String saveEmail(String email, String otp, String salt, LocalDateTime creationTime) {
        EmailVerification emailVerification = emailVerificationRepo.findByEmail(email).orElseGet(EmailVerification::new);
        emailVerification.setEmail(email);
        emailVerification.setOtp(otp);
        emailVerification.setSalt(salt);
        emailVerification.setStatus(STATUS_NOT_VERIFIED);
        emailVerification.setCreationTime(creationTime);
        emailVerification.setExpiryTime(creationTime.plusMinutes(OTP_EXPIRY_MINUTES));
        emailVerificationRepo.save(emailVerification);
        return "Email saved";
    }

    @Override
    public String verifyOtp(VerifyOtpDTO verifyOtpDTO) {
        EmailVerification emailVerification = emailVerificationRepo.findByEmail(verifyOtpDTO.getEmail())
                .orElseThrow(() -> new InvalidOtpException("Invalid OTP"));

        if (Duration.between(emailVerification.getCreationTime(), LocalDateTime.now()).toMinutes() > OTP_EXPIRY_MINUTES) {
            throw new OtpExpiredException("Invalid or expired OTP");
        }

        if (!OtpUtil.verifyOtp(verifyOtpDTO.getOtp(), emailVerification.getOtp(), emailVerification.getSalt())) {
            emailVerification.setAttempts(emailVerification.getAttempts() + 1);
            emailVerificationRepo.save(emailVerification);
            throw new InvalidOtpException("Invalid OTP. Attempt " + emailVerification.getAttempts());
        }

        emailVerification.setAttempts(0);
        emailVerification.setStatus(STATUS_VERIFIED);
        emailVerificationRepo.save(emailVerification);
        return "Verified";
    }

    @Scheduled(cron = "0 4 14 * * ?", zone = "Asia/Kolkata")
    @Transactional
    public void cleanupExpiredOTP() {
        LocalDateTime expiryThreshold = LocalDateTime.now().minusMinutes(OTP_EXPIRY_MINUTES);

        List<EmailVerification> expiredOtps = emailVerificationRepo.findByStatusAndCreationTimeBefore(STATUS_NOT_VERIFIED, expiryThreshold);
        log.debug("Found {} expired OTPs for cleanup", expiredOtps.size());

        if (!expiredOtps.isEmpty()) {
            int deletedRecords = emailVerificationRepo.deleteByStatusAndCreationTimeBefore(STATUS_NOT_VERIFIED, expiryThreshold);
            log.info("Deleted {} expired OTPs during scheduled cleanup", deletedRecords);
        } else {
            log.debug("No expired OTPs found during scheduled cleanup");
        }
    }


    private void sendEmailInternal(String message, String subject, String to) {
        try {
            Session session = Session.getInstance(setupEmailProperties(), new Authenticator() {
                @Override protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(emailUsername, emailPassword);
                }
            });
            Message mimeMessage = new MimeMessage(session);
            mimeMessage.setFrom(new InternetAddress(emailUsername));
            mimeMessage.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            mimeMessage.setSubject(subject);
            mimeMessage.setContent(message, "text/html");
            Transport.send(mimeMessage);
        } catch (MessagingException e) {
            throw new RuntimeException("Error sending email", e);
        }
    }

    private Properties setupEmailProperties() {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", emailHost);
        properties.put("mail.smtp.port", emailPort);
        properties.put("mail.smtp.ssl.enable", emailSmtpSslEnable);
        properties.put("mail.smtp.auth", emailSmtpAuth);
        return properties;
    }

    private String loadEmailTemplate(String templateFileName) {
        try (InputStream inputStream = getClass().getResourceAsStream("/templates/" + templateFileName)) {
            if (inputStream == null) {
                log.error("Template file not found: {}", templateFileName);
                return "Please enter the OTP code: {{otp}}"; // Basic fallback template
            }
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
            }
        } catch (Exception e) {
            log.error("Error loading email template", e);
            throw new RuntimeException("Error loading email template", e);
        }
    }
}
