//package com.spring.jwt.EmployeeAttendance;
//
//import com.spring.jwt.Enums.AttendanceStatus;
//import com.spring.jwt.dto.BaseResponseDTO2;
//import jakarta.validation.Valid;
//import lombok.RequiredArgsConstructor;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.Pageable;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.access.prepost.PreAuthorize;
//import org.springframework.web.bind.annotation.*;
//
//@RestController
//@RequestMapping("/api/v1/attendance")
//@RequiredArgsConstructor
//public class EmployeeAttendanceController {
//
//    private final EmployeeAttendanceService attendanceService;
//
//    // ================= MARK ATTENDANCE =================
//    @PostMapping("/mark")
//    public ResponseEntity<BaseResponseDTO2<EmployeeAttendanceDTO>>
//    markAttendance(@Valid @RequestBody EmployeeAttendanceDTO dto) {
//
//        return ResponseEntity.ok(
//                BaseResponseDTO2.success(
//                        HttpStatus.OK,
//                        "Attendance marked successfully",
//                        attendanceService.markAttendance(dto)
//                )
//        );
//    }
//
//    // ================= GET BY ID =================
//    @GetMapping("/{id}")
//    public ResponseEntity<BaseResponseDTO2<EmployeeAttendanceDTO>>
//    getById(@PathVariable Long id) {
//
//        return ResponseEntity.ok(
//                BaseResponseDTO2.success(
//                        HttpStatus.OK,
//                        "Attendance fetched successfully",
//                        attendanceService.getAttendanceById(id)
//                )
//        );
//    }
//
//    // ================= ALL ATTENDANCE (ADMIN) =================
//    @GetMapping
//    @PreAuthorize("hasRole('ADMIN')")
//    public ResponseEntity<BaseResponseDTO2<Page<EmployeeAttendanceDTO>>>
//    getAll(Pageable pageable) {
//
//        return ResponseEntity.ok(
//                BaseResponseDTO2.success(
//                        HttpStatus.OK,
//                        "Attendance list fetched successfully",
//                        attendanceService.getAllAttendance(pageable)
//                )
//        );
//    }
//
//    // ================= MY ATTENDANCE =================
//    @GetMapping("/me")
//    public ResponseEntity<BaseResponseDTO2<Page<EmployeeAttendanceDTO>>>
//    getMyAttendance(Pageable pageable) {
//
//        return ResponseEntity.ok(
//                BaseResponseDTO2.success(
//                        HttpStatus.OK,
//                        "My attendance fetched successfully",
//                        attendanceService.getMyAttendance(pageable)
//                )
//        );
//    }
//
//    // ================= BY STATUS =================
//    @GetMapping("/status/{status}")
//    public ResponseEntity<BaseResponseDTO2<Page<EmployeeAttendanceDTO>>>
//    getByStatus(
//            @PathVariable AttendanceStatus status,
//            Pageable pageable) {
//
//        return ResponseEntity.ok(
//                BaseResponseDTO2.success(
//                        HttpStatus.OK,
//                        "Attendance fetched by status",
//                        attendanceService.getAttendanceByStatus(status, pageable)
//                )
//        );
//    }
//
//    // ================= MY BY STATUS =================
//    @GetMapping("/me/status/{status}")
//    public ResponseEntity<BaseResponseDTO2<Page<EmployeeAttendanceDTO>>>
//    getMyByStatus(
//            @PathVariable AttendanceStatus status,
//            Pageable pageable) {
//
//        return ResponseEntity.ok(
//                BaseResponseDTO2.success(
//                        HttpStatus.OK,
//                        "My attendance fetched by status",
//                        attendanceService.getMyAttendanceByStatus(status, pageable)
//                )
//        );
//    }
//
//    // ================= APPROVE (ADMIN) =================
//    @PutMapping("/approve/{attendanceId}")
//    @PreAuthorize("hasRole('ADMIN')")
//    public ResponseEntity<BaseResponseDTO2<EmployeeAttendanceDTO>>
//    approve(@PathVariable Long attendanceId) {
//
//        return ResponseEntity.ok(
//                BaseResponseDTO2.success(
//                        HttpStatus.OK,
//                        "Attendance approved successfully",
//                        attendanceService.approveAttendance(attendanceId)
//                )
//        );
//    }
//
//    // ================= REJECT (ADMIN) =================
//    @PutMapping("/reject/{attendanceId}")
//    @PreAuthorize("hasRole('ADMIN')")
//    public ResponseEntity<BaseResponseDTO2<EmployeeAttendanceDTO>>
//    reject(
//            @PathVariable Long attendanceId,
//            @RequestParam String reason) {
//
//        return ResponseEntity.ok(
//                BaseResponseDTO2.success(
//                        HttpStatus.OK,
//                        "Attendance rejected successfully",
//                        attendanceService.rejectAttendance(attendanceId, reason)
//                )
//        );
//    }
//
//    // ================= DASHBOARD =================
//    @GetMapping("/dashboard")
//    public ResponseEntity<BaseResponseDTO2<AttendanceDashboardDTO>>
//    dashboard() {
//
//        return ResponseEntity.ok(
//                BaseResponseDTO2.success(
//                        HttpStatus.OK,
//                        "Attendance dashboard fetched",
//                        attendanceService.getAttendanceDashboardCount()
//                )
//        );
//    }
//
//    // ================= MONTHLY SUMMARY =================
//    @GetMapping("/me/monthly-summary")
//    public ResponseEntity<BaseResponseDTO2<MonthlyAttendanceSummaryDTO>>
//    monthlySummary(
//            @RequestParam int month,
//            @RequestParam int year) {
//
//        return ResponseEntity.ok(
//                BaseResponseDTO2.success(
//                        HttpStatus.OK,
//                        "Monthly attendance summary fetched",
//                        attendanceService.getMyMonthlyAttendanceSummary(month, year)
//                )
//        );
//    }
//
//    // ================= FULL MONTH REPORT =================
//    @GetMapping("/me/monthly-report")
//    public ResponseEntity<BaseResponseDTO2<EmployeeMonthlyAttendanceReportDTO>>
//    monthlyReport(
//            @RequestParam int month,
//            @RequestParam int year) {
//
//        return ResponseEntity.ok(
//                BaseResponseDTO2.success(
//                        HttpStatus.OK,
//                        "Monthly attendance report fetched",
//                        attendanceService.getMyMonthlyAttendanceFullReport(month, year)
//                )
//        );
//    }
//
//    // ================= ADMIN OVERRIDE =================
//    @PostMapping("/admin/override")
//    @PreAuthorize("hasRole('ADMIN')")
//    public ResponseEntity<BaseResponseDTO2<EmployeeAttendanceDTO>>
//    adminOverride(
//            @Valid @RequestBody AdminAttendanceOverrideDTO dto) {
//
//        return ResponseEntity.ok(
//                BaseResponseDTO2.success(
//                        HttpStatus.OK,
//                        "Attendance overridden successfully",
//                        attendanceService.adminOverrideAttendance(dto)
//                )
//        );
//    }
//
//    // ================= DELETE =================
//    @DeleteMapping("/{attendanceId}")
//    @PreAuthorize("hasRole('ADMIN')")
//    public ResponseEntity<BaseResponseDTO2<Void>>
//    delete(@PathVariable Long attendanceId) {
//
//        attendanceService.deleteAttendance(attendanceId);
//
//        return ResponseEntity.ok(
//                BaseResponseDTO2.success(
//                        HttpStatus.OK,
//                        "Attendance deleted successfully",
//                        null
//                )
//        );
//    }
//}
