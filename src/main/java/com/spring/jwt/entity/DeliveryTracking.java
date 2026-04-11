package com.spring.jwt.entity;

import com.spring.jwt.Enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "delivery_tracking",
        indexes = {
                @Index(name = "idx_tracking_order", columnList = "order_id"),
                @Index(name = "idx_tracking_status", columnList = "status")
        }
)
@AllArgsConstructor
@NoArgsConstructor
public class DeliveryTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // ✅ standard naming

    // 🔗 Proper relation (BEST PRACTICE)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private ProductBuyConfirmed order;

    // 📦 Courier Details
    @Column(unique = true)
    private String trackingNumber;

    private String courierName;

    // 🚚 Current Status
    @Enumerated(EnumType.STRING)
    private DeliveryStatus status;

    private String currentLocation;

    // 📅 Timeline Fields
    private LocalDateTime shippedDate;
    private LocalDateTime outForDeliveryDate;
    private LocalDateTime deliveredDate;

    // ⏳ ETA (Very Important 🔥)
    private LocalDateTime estimatedDeliveryDate;

    private LocalDateTime createdAt = LocalDateTime.now();

    // 👤 Delivery Info
    private String deliveryAddress;
    private String customerName;
    private String contactNumber;

    // 🔥 Extra Flags (Production Use)
    private Boolean isDelayed = false;
    private Boolean isReturned = false;
}