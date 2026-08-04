package com.saga.be.repository;

import com.saga.be.entity.Document;
import com.saga.be.entity.enums.DocumentType;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    long countByProjectIdAndAuthorIdAndType(UUID projectId, UUID authorId, DocumentType type);

    long countByProjectIdAndAuthorIdAndTypeNot(UUID projectId, UUID authorId, DocumentType type);

    List<Document> findByProjectId(UUID projectId);
}