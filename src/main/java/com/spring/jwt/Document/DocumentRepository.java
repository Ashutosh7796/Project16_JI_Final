package com.spring.jwt.Document;

import com.spring.jwt.Enums.DocumentType;
import com.spring.jwt.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Integer> {

    @Query("SELECT d FROM Document d WHERE d.user.id = :userId AND d.documentType = :documentType")
    Optional<Document> findByUserIdAndDocumentType(@Param("userId") Long userId, @Param("documentType") DocumentType documentType);

    @Query("SELECT d FROM Document d WHERE d.user.id = :userId ORDER BY d.uploadedAt DESC")
    List<Document> findByUserIdOrderByUploadedAtDesc(@Param("userId") Long userId);

    @Query("SELECT d FROM Document d WHERE d.user.id = :userId ORDER BY d.uploadedAt DESC")
    Page<Document> findDocumentsByUserIdWithPagination(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.user.id = :userId ORDER BY d.uploadedAt DESC")
    List<Document> findDocumentsByUserId(@Param("userId") Long userId);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Document d WHERE d.user.id = :userId AND d.documentType = :documentType")
    boolean existsByUserIdAndDocumentType(@Param("userId") Long userId, @Param("documentType") DocumentType documentType);

    @Query("SELECT COUNT(d) FROM Document d WHERE d.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    @Query("SELECT d FROM Document d WHERE d.user.id = :userId AND d.documentType IN :documentTypes")
    List<Document> findByUserIdAndDocumentTypeIn(@Param("userId") Long userId, @Param("documentTypes") List<DocumentType> documentTypes);

    @Query("DELETE FROM Document d WHERE d.user.id = :userId AND d.documentType = :documentType")
    void deleteByUserIdAndDocumentType(@Param("userId") Long userId, @Param("documentType") DocumentType documentType);
}
