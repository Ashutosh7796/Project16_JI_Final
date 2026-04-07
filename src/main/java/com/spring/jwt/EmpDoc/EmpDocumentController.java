package com.spring.jwt.EmpDoc;

import com.spring.jwt.Enums.DocumentType;
import com.spring.jwt.dto.ResponseDto;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/emp-documents")
@RequiredArgsConstructor
public class EmpDocumentController {

    private final EmpDocumentService service;

    @PostMapping("/add")
    public ResponseDto<?> save(@RequestBody EmpDocumentDTO dto) {
        try {
            return ResponseDto.success("Document saved successfully", service.save(dto));
        } catch (Exception e) {
            return ResponseDto.error("Failed to save document", e.getMessage());
        }
    }

    @PatchMapping("/update/{id}")
    public ResponseDto<?> update(@PathVariable Long id, @RequestBody EmpDocumentDTO dto) {
        try {
            return ResponseDto.success("Document updated successfully", service.update(id, dto));
        } catch (Exception e) {
            return ResponseDto.error("Failed to update document", e.getMessage());
        }
    }

    @DeleteMapping("delete/{id}")
    public ResponseDto<?> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ResponseDto.success("Document deleted successfully", null);
        } catch (Exception e) {
            return ResponseDto.error("Failed to delete document", e.getMessage());
        }
    }

    @GetMapping("/getById/{id}")
    public ResponseDto<?> getById(@PathVariable Long id) {
        try {
            return ResponseDto.success("Document fetched successfully", service.getById(id));
        } catch (Exception e) {
            return ResponseDto.error("Failed to fetch document", e.getMessage());
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseDto<?> getByUserId(@PathVariable Long userId) {
        try {
            return ResponseDto.success("Documents fetched successfully", service.getByUserId(userId));
        } catch (Exception e) {
            return ResponseDto.error("Failed to fetch documents", e.getMessage());
        }
    }

    @GetMapping("/user/{userId}/type/{documentType}")
    public ResponseDto<?> getByUserIdAndDocumentType(
            @PathVariable Long userId,
            @PathVariable DocumentType documentType
    ) {
        try {
            return ResponseDto.success("Document fetched successfully",
                    service.getByUserIdAndDocumentType(userId, documentType));
        } catch (Exception e) {
            return ResponseDto.error("Failed to fetch document", e.getMessage());
        }
    }
}
