package com.spring.jwt.EmpDoc;


import com.spring.jwt.entity.EmpDocument;
import com.spring.jwt.entity.User;
import com.spring.jwt.Enums.DocumentType;
import com.spring.jwt.exception.ResourceNotFoundException;
import com.spring.jwt.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmpDocumentServiceImpl implements EmpDocumentService {

    private final EmpDocumentRepository empDocumentRepository;
    private final UserRepository userRepository;

    @Override
    public EmpDocumentDTO save(EmpDocumentDTO dto) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        EmpDocument doc = new EmpDocument();
        doc.setUser(user);
        doc.setPdfUrl(dto.getPdfUrl());
        doc.setDocumentType(dto.getDocumentType());

        EmpDocument saved = empDocumentRepository.save(doc);

        return mapToDTO(saved);
    }

    @Override
    public EmpDocumentDTO update(Long id, EmpDocumentDTO dto) {
        EmpDocument doc = empDocumentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (dto.getPdfUrl() != null) doc.setPdfUrl(dto.getPdfUrl());
        if (dto.getDocumentType() != null) doc.setDocumentType(dto.getDocumentType());

        return mapToDTO(empDocumentRepository.save(doc));
    }

    @Override
    public void delete(Long id) {
        EmpDocument doc = empDocumentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        empDocumentRepository.delete(doc);
    }

    @Override
    public EmpDocumentDTO getById(Long id) {
        EmpDocument doc = empDocumentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        return mapToDTO(doc);
    }

    @Override
    public List<EmpDocumentDTO> getByUserId(Long userId) {
        return empDocumentRepository.findByUser_UserId(userId)
                .stream().map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public EmpDocumentDTO getByUserIdAndDocumentType(Long userId, DocumentType documentType) {
        EmpDocument doc = empDocumentRepository
                .findByUser_UserIdAndDocumentType(userId, documentType)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        return mapToDTO(doc);
    }

    private EmpDocumentDTO mapToDTO(EmpDocument doc) {
        EmpDocumentDTO dto = new EmpDocumentDTO();
        dto.setEmpDocumentId(doc.getEmpDocumentId());
        dto.setUserId(doc.getUser().getUserId());
        dto.setPdfUrl(doc.getPdfUrl());
        dto.setDocumentType(doc.getDocumentType());
        return dto;
    }
}
