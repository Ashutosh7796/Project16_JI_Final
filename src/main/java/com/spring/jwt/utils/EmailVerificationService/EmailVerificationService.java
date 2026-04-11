package com.spring.jwt.utils.EmailVerificationService;

import com.spring.jwt.utils.EmailVerificationService.EmailUtils.VerifyOtpDTO;

import java.time.LocalDateTime;

public interface EmailVerificationService {


    void sendEmail(String email);

    String saveEmail(String email, String hashedOtp, String salt, LocalDateTime localDateTime);

    public String verifyOtp(VerifyOtpDTO verifyOtpDTO);

}
