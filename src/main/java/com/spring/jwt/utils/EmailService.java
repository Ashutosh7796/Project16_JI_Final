package com.spring.jwt.utils;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Service;
import java.util.Properties;

@Service
public class EmailService {

    private static final String FROM_EMAIL = "ashutoshshedgeas87@gmail.com";
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final String SMTP_PORT = "465";
    private static final String SMTP_USERNAME = "ashutoshshedgeas87@gmail.com";
    private static final String SMTP_PASSWORD = "kpwbazsrgedaerhx";

    public void sendResetPasswordEmail(String to, String resetLink, String domain) {
        String subject = "Reset Password";
        String emailContent = generateEmailContent(resetLink);

        try {
            Session session = createEmailSession();
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(FROM_EMAIL));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(subject);
            message.setContent(emailContent, "text/html; charset=utf-8");

            Transport.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Error sending email", e);
        }
    }

    private String generateEmailContent(String resetLink) {
        return "<html>" +
                "<body style='font-family: Arial, sans-serif; margin: 0; padding: 0; background-color: #f4f4f4;'>" +
                "<div style='max-width: 600px; margin: 20px auto; padding: 20px; background-color: #ffffff; border-radius: 10px; box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);'>" +
                "<h2 style='color: #333333;'>Reset Your Password</h2>" +
                "<p>Dear user,</p>" +
                "<p>You have requested to reset your password. Please click the button below to reset your password:</p>" +
                "<a href='" + resetLink + "' style='display: inline-block; margin: 20px 0; padding: 10px 20px; color: #ffffff; background-color: #007bff; text-decoration: none; border-radius: 5px;'>Reset My Password</a>" +
                "<p>If you did not request this, please ignore this email.</p>" +
                "<p>Thank you,<br>CarTechIndia.com</p>" +
                "</div>" +
                "</body>" +
                "</html>";
    }

    private Session createEmailSession() {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", SMTP_HOST);
        properties.put("mail.smtp.port", SMTP_PORT);
        properties.put("mail.smtp.ssl.enable", "true");
        properties.put("mail.smtp.auth", "true");

        return Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SMTP_USERNAME, SMTP_PASSWORD);
            }
        });
    }
}

