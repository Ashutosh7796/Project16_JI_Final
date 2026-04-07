package com.spring.jwt.utils.EmailVerificationService;

import com.spring.jwt.utils.EmailVerificationService.EmailUtils.EmailVerificationRequest;
import com.spring.jwt.utils.EmailVerificationService.EmailUtils.ResponseMessage;
import com.spring.jwt.utils.EmailVerificationService.EmailUtils.VerifyOtpDTO;
import com.spring.jwt.utils.EmailVerificationService.EmailUtils.VerifyOtpRequest;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/emailVerification")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    @PostMapping("/send-otp")
    public ResponseEntity<ResponseMessage> sendOtp(@RequestBody EmailVerificationRequest request) {
        emailVerificationService.sendEmail(request.getEmail());
        return ResponseEntity.ok(new ResponseMessage("OTP sent successfully."));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ResponseMessage> verifyOtp(@RequestBody @Valid VerifyOtpRequest request) {

        VerifyOtpDTO verifyOtpDTO = new VerifyOtpDTO(request.getOtp(), request.getEmail());

        String result = emailVerificationService.verifyOtp(verifyOtpDTO);

        return ResponseEntity.ok(new ResponseMessage(result));
    }

}
