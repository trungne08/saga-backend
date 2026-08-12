package com.saga.be.repository;

import com.saga.be.entity.Document;
import com.saga.be.entity.enums.DocumentType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    long countByProjectIdAndAuthorIdAndType(UUID projectId, UUID authorId, DocumentType type);

    long countByProjectIdAndAuthorIdAndTypeNot(UUID projectId, UUID authorId, DocumentType type);

    List<Document> findByProjectId(UUID projectId);

    long countByProjectIdAndAuthorIdAndCreatedAtIsNotNull(UUID projectId, UUID authorId);

    List<Document> findByProjectIdAndAuthorIdOrderByCreatedAtDescIdDesc(
            UUID projectId, UUID authorId, Pageable pageable);

    @Query("""
            select document.author.id, function('date', document.createdAt), count(document)
            from Document document
            where document.project.id = :projectId
              and document.author.id in :authorIds
              and document.createdAt >= :startAt
              and document.createdAt < :endExclusive
            group by document.author.id, function('date', document.createdAt)
            order by document.author.id, function('date', document.createdAt)
            """)
    List<Object[]> aggregateDailyCountsByProjectAndAuthorIds(
            @Param("projectId") UUID projectId,
            @Param("authorIds") Collection<UUID> authorIds,
            @Param("startAt") LocalDateTime startAt,
            @Param("endExclusive") LocalDateTime endExclusive
    );
}
