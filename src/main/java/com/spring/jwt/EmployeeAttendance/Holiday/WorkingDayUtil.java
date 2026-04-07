package com.spring.jwt.EmployeeAttendance.Holiday;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class WorkingDayUtil {

    private final HolidayRepository holidayRepository;

    public boolean isWorkingDay(LocalDate date) {

        // Skip Saturday & Sunday
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY
                || day == DayOfWeek.SUNDAY) {
            return false;
        }

        // Skip Holidays
        if (holidayRepository.existsByHolidayDate(date)) {
            return false;
        }

        return true;
    }
}
