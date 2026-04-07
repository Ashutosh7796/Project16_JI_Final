package com.spring.jwt.utils.EmailVerificationService;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "email_verification")
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "Email cannot be blank")
    @Email
    @Pattern(regexp = "^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$", message = "Invalid email format")
    @Column(name = "email", nullable = false, length = 250, unique = true)
    private String email;

    public static final String STATUS_VERIFIED = "Verified";
    public static final String STATUS_NOT_VERIFIED = "Not Verified";

    @Column(nullable = false)
    private String status;

    @Column(name = "hashed_otp", nullable = false)
    private String otp;

    @Column(nullable = false)
    private String salt;

    @Column(name = "creation_time", nullable = false)
    private LocalDateTime creationTime;

    @Column(name = "expiry_time", nullable = false)
    private LocalDateTime expiryTime;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "ip_address")
    private String ipAddress;
}
