package com.spring.jwt.config;

import com.spring.jwt.Enums.DocumentType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * Aspect to validate and log DocumentType parameters
 * Helps debug document upload issues
 */
@Aspect
@Component
@Slf4j
public class DocumentTypeValidationAspect {

    @Before("execution(* com.spring.jwt.Document.DocumentService.uploadDocument(..)) && args(userId, file, documentType, ..)")
    public void logDocumentUpload(JoinPoint joinPoint, Long userId, Object file, DocumentType documentType) {
        log.debug("DocumentService.uploadDocument called - userId: {}, documentType: {} ({})", 
            userId, documentType, documentType.name());
        
        if (documentType == DocumentType.BANK_PASSBOOK) {
            log.info("BANK_PASSBOOK document upload detected for user: {}", userId);
        }
    }
}
