package com.spring.jwt.EmployeeAttendance.Holiday;


import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private final HolidayService holidayService;


    @PostMapping
    public ResponseEntity<HolidayDTO> create(
            @Valid @RequestBody HolidayDTO dto) {
        return ResponseEntity.ok(
                holidayService.createHoliday(dto));
    }


    @PutMapping("/{id}")
    public ResponseEntity<HolidayDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody HolidayDTO dto) {
        return ResponseEntity.ok(
                holidayService.updateHoliday(id, dto));
    }


    @GetMapping("/{id}")
    public ResponseEntity<HolidayDTO> getById(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                holidayService.getHolidayById(id));
    }


    @GetMapping
    public ResponseEntity<Page<HolidayDTO>> getAll(
            Pageable pageable) {
        return ResponseEntity.ok(
                holidayService.getAllHolidays(pageable));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id) {
        holidayService.deleteHoliday(id);
        return ResponseEntity.noContent().build();
    }
}
