package com.saga.be.integration.identity;

import com.saga.be.dto.request.IdentityMappingReviewRequest;
import com.saga.be.dto.response.IdentityConnectionResponse;
import com.saga.be.entity.IdentityMap;
import com.saga.be.entity.IdentityMappingHistory;
import com.saga.be.entity.Student;
import com.saga.be.entity.enums.IdentityMappingAction;
import com.saga.be.entity.enums.IdentityMappingStatus;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.IdentityMapRepository;
import com.saga.be.repository.IdentityMappingHistoryRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.AuthenticationAuditService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityMappingReviewService {

    private final IdentityMapRepository mappingRepository;
    private final IdentityMappingHistoryRepository historyRepository;
    private final StudentRepository studentRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AuthenticationAuditService auditService;

    public IdentityMappingReviewService(
            IdentityMapRepository mappingRepository,
            IdentityMappingHistoryRepository historyRepository,
            StudentRepository studentRepository,
            TeamMemberRepository teamMemberRepository,
            AuthenticationAuditService auditService
    ) {
        this.mappingRepository = mappingRepository;
        this.historyRepository = historyRepository;
        this.studentRepository = studentRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<IdentityConnectionResponse> mappings(
            SagaPrincipal reviewer,
            UUID studentId
    ) {
        requireReviewer(reviewer, studentId);
        return mappingRepository.findByStudentIdOrderByProvider(studentId)
                .stream()
                .map(IdentityConnectionResponse::from)
                .toList();
    }

    @Transactional
    public IdentityConnectionResponse review(
            SagaPrincipal reviewer,
            UUID mappingId,
            IdentityMappingReviewRequest request,
            String remoteAddress
    ) {
        IdentityMap mapping = mappingRepository.findLockedById(mappingId)
                .orElseThrow(() -> IntegrationException.invalid(
                        "IDENTITY_MAPPING_NOT_FOUND",
                        "The identity mapping does not exist"
                ));
        requireReviewer(reviewer, mapping.getStudent().getId());
        IdentityMappingAction action;
        switch (request.action()) {
            case APPROVE -> {
                mapping.setMappingStatus(IdentityMappingStatus.ACTIVE);
                action = IdentityMappingAction.APPROVED;
            }
            case REJECT -> {
                mapping.setMappingStatus(IdentityMappingStatus.REJECTED);
                action = IdentityMappingAction.REJECTED;
            }
            case CORRECT -> {
                if (request.correctedStudentId() == null) {
                    throw IntegrationException.invalid(
                            "CORRECTED_STUDENT_REQUIRED",
                            "A corrected Student is required"
                    );
                }
                requireReviewer(reviewer, request.correctedStudentId());
                Student corrected = studentRepository
                        .findById(request.correctedStudentId())
                        .orElseThrow(() -> IntegrationException.invalid(
                                "STUDENT_NOT_FOUND",
                                "The corrected Student does not exist"
                        ));
                mapping.setStudent(corrected);
                mapping.setMappingStatus(IdentityMappingStatus.ACTIVE);
                action = IdentityMappingAction.CORRECTED;
            }
            default -> throw new IllegalStateException("Unsupported review action");
        }
        mapping.setReviewedByCognitoSub(reviewer.cognitoSub());
        mapping.setReviewedAt(LocalDateTime.now());
        try {
            mapping = mappingRepository.saveAndFlush(mapping);
        } catch (DataIntegrityViolationException exception) {
            throw IntegrationException.conflict(
                    "IDENTITY_MAPPING_CONFLICT",
                    "The corrected mapping conflicts with another provider identity"
            );
        }
        historyRepository.save(IdentityMappingHistory.builder()
                .identityMap(mapping)
                .student(mapping.getStudent())
                .provider(mapping.getProvider())
                .externalAccountId(mapping.getExternalAccountId())
                .action(action)
                .actorCognitoSub(reviewer.cognitoSub())
                .occurredAt(LocalDateTime.now())
                .build());
        auditService.recordIntegrationEvent(
                reviewer.cognitoSub(),
                "IDENTITY_MAPPING_" + action.name(),
                "IDENTITY_MAPPING",
                mapping.getId(),
                "SUCCESS",
                remoteAddress
        );
        return IdentityConnectionResponse.from(mapping);
    }

    private void requireReviewer(SagaPrincipal reviewer, UUID studentId) {
        if (reviewer.applicationRole() == ApplicationRole.ADMIN) {
            return;
        }
        if (
            reviewer.applicationRole() == ApplicationRole.LECTURER
            && teamMemberRepository.existsByStudentIdAndTeamCourseInstructorId(
                    studentId,
                    reviewer.localProfileId()
            )
        ) {
            return;
        }
        throw IntegrationException.forbidden(
                "Only an authorized Lecturer or Admin may review identity mappings"
        );
    }
}
