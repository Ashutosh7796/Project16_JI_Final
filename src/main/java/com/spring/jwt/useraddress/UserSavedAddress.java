package com.spring.jwt.useraddress;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_saved_addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSavedAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Optional label, e.g. "Home", "Office". */
    @Column(name = "label", length = 80)
    private String label;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "phone", nullable = false, length = 32)
    private String phone;

    @Column(name = "address_line1", nullable = false, length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", nullable = false, length = 120)
    private String city;

    @Column(name = "state", nullable = false, length = 120)
    private String state;

    @Column(name = "pincode", nullable = false, length = 16)
    private String pincode;

    @Column(name = "country", nullable = false, length = 80)
    @Builder.Default
    private String country = "India";

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean defaultAddress = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String toDeliveryLine() {
        StringBuilder sb = new StringBuilder();
        appendPart(sb, addressLine1);
        appendPart(sb, addressLine2);
        appendPart(sb, city);
        appendPart(sb, state);
        appendPart(sb, pincode);
        appendPart(sb, country == null || country.isBlank() ? "India" : country);
        return sb.toString();
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(part.trim());
    }
}
