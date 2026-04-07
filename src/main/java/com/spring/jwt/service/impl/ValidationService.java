package com.spring.jwt.service.impl;

import com.spring.jwt.dto.AdminRegistrationRequest;
import com.spring.jwt.dto.SecureUserRegistrationRequest;
import com.spring.jwt.exception.BaseException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ValidationService
{

    public void validateUserRegistration(SecureUserRegistrationRequest request)
    {
        validatePasswordMatch(request.getPassword(), request.getConfirmPassword());
        validateTermsAndPrivacyAcceptance(request.getAcceptTerms(), request.getAcceptPrivacyPolicy());
    }

    public void validateAdminRegistration(AdminRegistrationRequest request)
    {
        validatePasswordMatch(request.getPassword(), request.getConfirmPassword());
    }

    private void validatePasswordMatch(String password, String confirmPassword)
    {
        if (!password.equals(confirmPassword))
        {
            throw new BaseException(String.valueOf(HttpStatus.BAD_REQUEST.value()), 
                "Password and confirm password do not match");
        }
    }

    private void validateTermsAndPrivacyAcceptance(Boolean acceptTerms, Boolean acceptPrivacyPolicy)
    {
        if (!acceptTerms || !acceptPrivacyPolicy) {
            throw new BaseException(String.valueOf(HttpStatus.BAD_REQUEST.value()), 
                "Terms and conditions and privacy policy must be accepted");
        }
    }
}