package com.spring.jwt.EmpDoc;

import com.spring.jwt.entity.EmpDocument;
import com.spring.jwt.Enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmpDocumentRepository extends JpaRepository<EmpDocument, Long> {

    List<EmpDocument> findByUser_UserId(Long userId);

    Optional<EmpDocument> findByUser_UserIdAndDocumentType(Long userId, DocumentType documentType);
}
