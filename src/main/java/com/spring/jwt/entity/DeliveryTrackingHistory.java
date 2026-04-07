package com.spring.jwt.entity;

import com.spring.jwt.Enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_tracking_history")
@Data
public class DeliveryTrackingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "delivery_id")
    private DeliveryTracking delivery;

    @Enumerated(EnumType.STRING)
    private DeliveryStatus status;

    private String location;

    private String description;

    private LocalDateTime eventTime;
}