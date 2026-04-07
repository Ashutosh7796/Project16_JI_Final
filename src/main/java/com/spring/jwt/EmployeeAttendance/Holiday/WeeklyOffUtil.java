package com.spring.jwt.EmployeeAttendance.Holiday;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class WeeklyOffUtil {

    private final CompanyWeeklyOffRepository weeklyOffRepository;
    private final HolidayRepository holidayRepository;

    public boolean isWorkingDay(LocalDate date) {

        // 1️⃣ Holiday check
        if (holidayRepository.existsByHolidayDate(date)) {
            return false;
        }

        // 2️⃣ Weekly off check
        DayOfWeek weeklyOff =
                weeklyOffRepository.findAll()
                        .stream()
                        .findFirst()
                        .map(CompanyWeeklyOff::getWeeklyOffDay)
                        .orElse(DayOfWeek.SUNDAY); // default

        if (date.getDayOfWeek() == weeklyOff) {
            return false;
        }

        return true;
    }
}
