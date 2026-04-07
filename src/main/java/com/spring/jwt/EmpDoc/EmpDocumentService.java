package com.spring.jwt.EmpDoc;


import com.spring.jwt.Enums.DocumentType;

import java.util.List;

public interface EmpDocumentService {

    EmpDocumentDTO save(EmpDocumentDTO dto);

    EmpDocumentDTO update(Long id, EmpDocumentDTO dto);

    void delete(Long id);

    EmpDocumentDTO getById(Long id);

    List<EmpDocumentDTO> getByUserId(Long userId);

    EmpDocumentDTO getByUserIdAndDocumentType(Long userId, DocumentType documentType);
}
