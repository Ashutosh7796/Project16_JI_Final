package com.spring.jwt.Employee;

import com.spring.jwt.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmployeeRepository  extends JpaRepository<Employee, Long> {

    Optional<Employee> findByUser_UserId(Long userId);



}
