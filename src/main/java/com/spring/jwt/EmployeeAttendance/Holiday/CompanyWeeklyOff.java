package com.spring.jwt.EmployeeAttendance.Holiday;

import jakarta.persistence.*;
import lombok.Data;

import java.time.DayOfWeek;

@Data
@Entity
@Table(name = "company_weekly_off")
public class CompanyWeeklyOff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // MONDAY / TUESDAY / ... / SUNDAY
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private DayOfWeek weeklyOffDay;
}
