package com.saga.be.repository;

import com.saga.be.entity.RubricTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RubricTemplateRepository extends JpaRepository<RubricTemplate, UUID> {

    List<RubricTemplate> findBySubjectIdIsNullOrderByCreatedAtAsc();

    List<RubricTemplate> findBySubjectIdOrderByCreatedAtAsc(UUID subjectId);

    List<RubricTemplate> findBySubjectIdIsNullAndDeletedAtIsNullOrderByCreatedAtAsc();

    List<RubricTemplate> findBySubjectIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID subjectId);

    Optional<RubricTemplate> findByIdAndDeletedAtIsNull(UUID id);
}
