package com.saga.be.service;

import com.saga.be.dto.request.AdminPeerReviewRubricRequest;
import com.saga.be.dto.response.AdminPeerReviewRubricResponse;
import com.saga.be.entity.RubricTemplate;
import com.saga.be.repository.RubricTemplateRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AdminPeerReviewRubricService {

    private static final int MAX_ACTIVE_GLOBAL_RUBRICS = 4;

    private final RubricTemplateRepository rubricTemplateRepository;

    @Transactional
    public AdminPeerReviewRubricResponse create(AdminPeerReviewRubricRequest request) {
        List<RubricTemplate> activeGlobalRubrics = activeGlobalRubrics();
        if (activeGlobalRubrics.size() >= MAX_ACTIVE_GLOBAL_RUBRICS) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The maximum number of active global peer review rubrics has been reached"
            );
        }

        RubricTemplate rubric = RubricTemplate.builder()
                .subject(null)
                .criteriaName(requiredTrimmedCriteriaName(request.getCriteriaName()))
                .weight(request.getWeight())
                .description(request.getDescription())
                .deletedAt(null)
                .build();
        return AdminPeerReviewRubricResponse.from(rubricTemplateRepository.save(rubric));
    }

    @Transactional
    public AdminPeerReviewRubricResponse update(UUID rubricId, AdminPeerReviewRubricRequest request) {
        RubricTemplate rubric = requireActiveGlobalRubric(rubricId);
        rubric.setCriteriaName(requiredTrimmedCriteriaName(request.getCriteriaName()));
        rubric.setWeight(request.getWeight());
        rubric.setDescription(request.getDescription());
        return AdminPeerReviewRubricResponse.from(rubricTemplateRepository.save(rubric));
    }

    @Transactional
    public void softDelete(UUID rubricId) {
        RubricTemplate rubric = requireActiveGlobalRubric(rubricId);
        rubric.setDeletedAt(LocalDateTime.now());
        rubricTemplateRepository.save(rubric);
    }

    private List<RubricTemplate> activeGlobalRubrics() {
        return rubricTemplateRepository.findBySubjectIdIsNullAndDeletedAtIsNullOrderByCreatedAtAsc();
    }

    private RubricTemplate requireActiveGlobalRubric(UUID rubricId) {
        RubricTemplate rubric = rubricTemplateRepository.findById(rubricId)
                .orElseThrow(this::notFound);
        if (rubric.getDeletedAt() != null) {
            throw notFound();
        }
        if (rubric.getSubject() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only global peer review rubrics can be managed through this endpoint"
            );
        }
        return rubric;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Peer review rubric not found");
    }

    private String requiredTrimmedCriteriaName(String criteriaName) {
        String trimmed = criteriaName == null ? "" : criteriaName.trim();
        if (trimmed.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Criteria name must not be blank");
        }
        return trimmed;
    }
}
