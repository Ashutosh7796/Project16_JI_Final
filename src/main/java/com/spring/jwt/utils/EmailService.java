package com.spring.jwt.utils;

import com.spring.jwt.config.EmailProperties;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * Email Service - Industrial Standard Implementation
 * 
 * Features:
 * - Externalized configuration via EmailProperties
 * - Retry mechanism for failed sends
 * - Proper error handling and logging
 * - HTML email templates
 * - Security best practices
 * 
 * @author System
 * @version 2.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final EmailProperties emailProperties;

    /**
     * Sends password reset email with retry mechanism
     * 
     * @param to Recipient email address
     * @param resetLink Password reset link with token
     * @throws RuntimeException if email fails after retries
     */
    @Retryable(
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2),
        retryFor = {MessagingException.class}
    )
    public void sendResetPasswordEmail(String to, String resetLink) {
        String subject = "Password Reset Request - " + emailProperties.getCompanyName();
        String emailContent = generatePasswordResetEmailContent(resetLink);

        try {
            sendEmail(to, subject, emailContent);
            log.info("Password reset email sent successfully to: {}", maskEmail(to));
        } catch (MessagingException e) {
            log.error("Failed to send password reset email to: {}", maskEmail(to), e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    /**
     * Sends email using configured SMTP settings
     */
    private void sendEmail(String to, String subject, String htmlContent) throws MessagingException {
        Session session = createEmailSession();
        MimeMessage message = new MimeMessage(session);
        
        try {
            message.setFrom(new InternetAddress(
                emailProperties.getFromEmail(), 
                emailProperties.getFromName()
            ));
        } catch (java.io.UnsupportedEncodingException e) {
            // Fallback to email without personal name
            message.setFrom(new InternetAddress(emailProperties.getFromEmail()));
        }
        
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
        message.setSubject(subject);
        message.setContent(htmlContent, "text/html; charset=utf-8");

        Transport.send(message);
    }

    /**
     * Creates email session with SMTP configuration
     */
    private Session createEmailSession() {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", emailProperties.getSmtpHost());
        properties.put("mail.smtp.port", emailProperties.getSmtpPort());
        properties.put("mail.smtp.ssl.enable", emailProperties.isSslEnable());
        properties.put("mail.smtp.auth", emailProperties.isAuthEnable());
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");

        return Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(
                    emailProperties.getUsername(), 
                    emailProperties.getPassword()
                );
            }
        });
    }

    /**
     * Generates HTML content for password reset email
     * 
     * Professional template with:
     * - Responsive design
     * - Clear call-to-action
     * - Security warnings
     * - Company branding
     */
    private String generatePasswordResetEmailContent(String resetLink) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Password Reset</title>
            </head>
            <body style="margin: 0; padding: 0; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f5f5f5;">
                <table role="presentation" style="width: 100%%; border-collapse: collapse;">
                    <tr>
                        <td align="center" style="padding: 40px 0;">
                            <table role="presentation" style="width: 600px; max-width: 100%%; border-collapse: collapse; background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                                <!-- Header -->
                                <tr>
                                    <td style="padding: 40px 40px 20px; text-align: center; background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); border-radius: 8px 8px 0 0;">
                                        <h1 style="margin: 0; color: #ffffff; font-size: 28px; font-weight: 600;">Password Reset Request</h1>
                                    </td>
                                </tr>
                                
                                <!-- Content -->
                                <tr>
                                    <td style="padding: 40px;">
                                        <p style="margin: 0 0 20px; color: #333333; font-size: 16px; line-height: 1.6;">Hello,</p>
                                        
                                        <p style="margin: 0 0 20px; color: #333333; font-size: 16px; line-height: 1.6;">
                                            We received a request to reset your password. Click the button below to create a new password:
                                        </p>
                                        
                                        <!-- CTA Button -->
                                        <table role="presentation" style="margin: 30px 0;">
                                            <tr>
                                                <td align="center">
                                                    <a href="%s" style="display: inline-block; padding: 16px 40px; background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: #ffffff; text-decoration: none; border-radius: 6px; font-size: 16px; font-weight: 600; box-shadow: 0 4px 6px rgba(102, 126, 234, 0.3);">Reset My Password</a>
                                                </td>
                                            </tr>
                                        </table>
                                        
                                        <!-- Security Notice -->
                                        <div style="margin: 30px 0; padding: 20px; background-color: #fff3cd; border-left: 4px solid #ffc107; border-radius: 4px;">
                                            <p style="margin: 0; color: #856404; font-size: 14px; line-height: 1.6;">
                                                <strong>⚠️ Security Notice:</strong><br>
                                                This link will expire in 30 minutes for your security.
                                            </p>
                                        </div>
                                        
                                        <p style="margin: 20px 0 0; color: #666666; font-size: 14px; line-height: 1.6;">
                                            If you didn't request this password reset, please ignore this email or contact our support team if you have concerns.
                                        </p>
                                        
                                        <!-- Alternative Link -->
                                        <div style="margin: 30px 0; padding: 20px; background-color: #f8f9fa; border-radius: 4px;">
                                            <p style="margin: 0 0 10px; color: #666666; font-size: 13px;">
                                                If the button doesn't work, copy and paste this link into your browser:
                                            </p>
                                            <p style="margin: 0; color: #667eea; font-size: 13px; word-break: break-all;">
                                                %s
                                            </p>
                                        </div>
                                    </td>
                                </tr>
                                
                                <!-- Footer -->
                                <tr>
                                    <td style="padding: 30px 40px; background-color: #f8f9fa; border-radius: 0 0 8px 8px; text-align: center;">
                                        <p style="margin: 0 0 10px; color: #666666; font-size: 14px;">
                                            Best regards,<br>
                                            <strong>%s Team</strong>
                                        </p>
                                        <p style="margin: 10px 0 0; color: #999999; font-size: 12px;">
                                            Need help? Contact us at <a href="mailto:%s" style="color: #667eea; text-decoration: none;">%s</a>
                                        </p>
                                        <p style="margin: 10px 0 0; color: #999999; font-size: 12px;">
                                            <a href="%s" style="color: #667eea; text-decoration: none;">%s</a>
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(
                resetLink,
                resetLink,
                emailProperties.getCompanyName(),
                emailProperties.getSupportEmail(),
                emailProperties.getSupportEmail(),
                emailProperties.getWebsiteUrl(),
                emailProperties.getWebsiteUrl()
            );
    }

    /**
     * Masks email for logging (security best practice)
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
