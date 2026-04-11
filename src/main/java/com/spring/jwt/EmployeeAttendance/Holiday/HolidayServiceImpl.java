package com.spring.jwt.EmployeeAttendance.Holiday;

import com.spring.jwt.exception.ResourceNotFoundException;
import com.spring.jwt.exception.UserAlreadyExistException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HolidayServiceImpl implements HolidayService {

    private final HolidayRepository holidayRepository;

    @Override
    public HolidayDTO createHoliday(HolidayDTO dto) {

        if (holidayRepository.existsByHolidayDate(dto.getHolidayDate())) {
            throw new UserAlreadyExistException(
                    "Holiday already exists for date: "
                            + dto.getHolidayDate());
        }

        Holiday holiday = new Holiday();
        holiday.setHolidayDate(dto.getHolidayDate());
        holiday.setDescription(dto.getDescription());

        return toDto(holidayRepository.save(holiday));
    }

    @Override
    public HolidayDTO updateHoliday(Long id, HolidayDTO dto) {

        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Holiday not found with ID: " + id));

        holiday.setHolidayDate(dto.getHolidayDate());
        holiday.setDescription(dto.getDescription());

        return toDto(holidayRepository.save(holiday));
    }

    @Override
    public HolidayDTO getHolidayById(Long id) {

        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Holiday not found with ID: " + id));

        return toDto(holiday);
    }

    @Override
    public Page<HolidayDTO> getAllHolidays(Pageable pageable) {

        Page<Holiday> page =
                holidayRepository.findAll(pageable);

        if (page.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No holidays found");
        }

        return page.map(this::toDto);
    }

    @Override
    public void deleteHoliday(Long id) {

        if (!holidayRepository.existsById(id)) {
            throw new ResourceNotFoundException(
                    "Holiday not found with ID: " + id);
        }

        holidayRepository.deleteById(id);
    }

    private HolidayDTO toDto(Holiday holiday) {

        HolidayDTO dto = new HolidayDTO();
        dto.setId(holiday.getId());
        dto.setHolidayDate(holiday.getHolidayDate());
        dto.setDescription(holiday.getDescription());
        return dto;
    }
}
