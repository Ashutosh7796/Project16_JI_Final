package com.spring.jwt.EmployeeAttendance.Holiday;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Data
@Entity
@Table(
        name = "holiday_calendar",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "holiday_date")
        }
)
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(nullable = false)
    private String description;
}
