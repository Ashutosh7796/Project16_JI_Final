package com.spring.jwt.EmployeeAttendance.Holiday;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;

@RestController
@RequestMapping("/api/v1/company-weekly-off")
@RequiredArgsConstructor
public class CompanyWeeklyOffController {

    private final CompanyWeeklyOffRepository repository;

    @PostMapping
    public ResponseEntity<String> setWeeklyOff(
            @RequestBody WeeklyOffDTO dto) {

        repository.deleteAll(); // ensure single row

        CompanyWeeklyOff off = new CompanyWeeklyOff();
        off.setWeeklyOffDay(dto.getWeeklyOffDay());

        repository.save(off);

        return ResponseEntity.ok(
                "Weekly off set to " + dto.getWeeklyOffDay());
    }

    @GetMapping
    public DayOfWeek getWeeklyOff() {
        return repository.findAll()
                .stream()
                .findFirst()
                .map(CompanyWeeklyOff::getWeeklyOffDay)
                .orElse(DayOfWeek.SUNDAY);
    }
}
