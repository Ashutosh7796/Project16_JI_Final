package com.spring.jwt.EmployeeAttendance.Holiday;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface HolidayService {

    HolidayDTO createHoliday(HolidayDTO dto);

    HolidayDTO updateHoliday(Long id, HolidayDTO dto);

    HolidayDTO getHolidayById(Long id);

    Page<HolidayDTO> getAllHolidays(Pageable pageable);

    void deleteHoliday(Long id);
}
