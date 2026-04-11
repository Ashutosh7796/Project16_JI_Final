package com.spring.jwt.Employee;


import com.spring.jwt.entity.Employee;
import com.spring.jwt.entity.User;
import org.springframework.stereotype.Component;

@Component
public class EmployeeMapper {

    public Employee toEntity(EmployeeCreateUpdateDTO dto, User user) {

        Employee emp = new Employee();
        emp.setUser(user);
        emp.setEmployeeCode(dto.getEmployeeCode());
        emp.setCompanyName(dto.getCompanyName());
        emp.setAddress(dto.getAddress());
        emp.setPermanentAddress(dto.getPermanentAddress());
        emp.setCity(dto.getCity());
        emp.setDistrict(dto.getDistrict());
        emp.setState(dto.getState());
        emp.setAccountNumber(dto.getAccountNumber());
        emp.setIfscCode(dto.getIfscCode());
        emp.setPfNumber(dto.getPfNumber());
        emp.setInsuranceNumber(dto.getInsuranceNumber());
        emp.setPanNumber(dto.getPanNumber());
        emp.setVehicleNumber(dto.getVehicleNumber());
        emp.setDescription(dto.getDescription());
        return emp;
    }

    public EmployeeResponseDTO toDto(Employee emp) {

        EmployeeResponseDTO dto = new EmployeeResponseDTO();
        dto.setEmployeeId(emp.getEmployeeId());
        dto.setUserId(emp.getUser().getUserId());
        dto.setUserEmail(emp.getUser().getEmail());
        dto.setUserMobile(emp.getUser().getMobileNumber());
        dto.setEmployeeCode(emp.getEmployeeCode());
        dto.setCompanyName(emp.getCompanyName());
        dto.setAddress(emp.getAddress());
        dto.setPermanentAddress(emp.getPermanentAddress());
        dto.setCity(emp.getCity());
        dto.setDistrict(emp.getDistrict());
        dto.setState(emp.getState());
        dto.setAccountNumber(emp.getAccountNumber());
        dto.setIfscCode(emp.getIfscCode());
        dto.setPfNumber(emp.getPfNumber());
        dto.setInsuranceNumber(emp.getInsuranceNumber());
        dto.setPanNumber(emp.getPanNumber());
        dto.setVehicleNumber(emp.getVehicleNumber());
        dto.setDescription(emp.getDescription());

        return dto;
    }

    private EmployeeResponseDTO mapToResponse(Employee employee) {

        EmployeeResponseDTO dto = new EmployeeResponseDTO();
        User user = employee.getUser();

        // ---------- IDS ----------
        dto.setEmployeeId(employee.getEmployeeId());
        dto.setUserId(user.getUserId());

        // ---------- USER ----------
      ;

        // ---------- EMPLOYEE ----------
        dto.setEmployeeCode(employee.getEmployeeCode());
        dto.setCompanyName(employee.getCompanyName());
        dto.setAddress(employee.getAddress());
        dto.setPermanentAddress(employee.getPermanentAddress());
        dto.setCity(employee.getCity());
        dto.setDistrict(employee.getDistrict());
        dto.setState(employee.getState());
        dto.setAccountNumber(employee.getAccountNumber());
        dto.setIfscCode(employee.getIfscCode());
        dto.setPfNumber(employee.getPfNumber());
        dto.setInsuranceNumber(employee.getInsuranceNumber());
        dto.setPanNumber(employee.getPanNumber());
        dto.setVehicleNumber(employee.getVehicleNumber());
        dto.setDescription(employee.getDescription());

        return dto;
    }


}
