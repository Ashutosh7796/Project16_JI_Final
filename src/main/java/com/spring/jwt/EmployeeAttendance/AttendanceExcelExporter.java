package com.spring.jwt.EmployeeAttendance;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class AttendanceExcelExporter {

    public byte[] exportMonthlyReport(
            EmployeeMonthlyAttendanceReportDTO report)
            throws IOException {

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook
                .createSheet("Monthly Attendance");

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Date");
        header.createCell(1).setCellValue("Status");
        header.createCell(2).setCellValue("Approved");
        header.createCell(3).setCellValue("Reason");

        int rowIdx = 1;
        for (AttendanceDayReportDTO day :
                report.getDailyReport()) {

            Row row = sheet.createRow(rowIdx++);
            row.createCell(0)
                    .setCellValue(day.getDate().toString());
            row.createCell(1)
                    .setCellValue(
                            day.getAttendanceStatus().name());
            row.createCell(2)
                    .setCellValue(
                            Boolean.TRUE.equals(
                                    day.getApproved()));
            row.createCell(3)
                    .setCellValue(day.getReason());
        }

        ByteArrayOutputStream out =
                new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();

        return out.toByteArray();
    }
}
