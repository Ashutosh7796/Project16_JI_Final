package com.spring.jwt.dto;


import com.spring.jwt.utils.EmailVerificationService.EmailUtils.EmailVerificationDto;
import com.spring.jwt.utils.EmailVerificationService.EmailVerification;
import org.springframework.stereotype.Component;

@Component
public class EmailVerificationMapper {

    public EmailVerificationDto toDto(EmailVerification emailVerification) {
        EmailVerificationDto dto = new EmailVerificationDto();
        dto.setId(emailVerification.getId());
        dto.setEmail(emailVerification.getEmail());
        dto.setStatus(emailVerification.getStatus());
        dto.setSalt(emailVerification.getSalt());
        dto.setHashedOtp(emailVerification.getOtp());
        dto.setCreationTime(emailVerification.getCreationTime());
        dto.setExpiryTime(emailVerification.getExpiryTime());
        dto.setAttempts(emailVerification.getAttempts());
        return dto;
    }
}
