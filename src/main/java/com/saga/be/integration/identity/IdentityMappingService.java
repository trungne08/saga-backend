package com.saga.be.integration.identity;

import com.saga.be.dto.response.IdentityConnectionResponse;
import com.saga.be.dto.response.PersonalIntegrationsResponse;
import com.saga.be.entity.IdentityMap;
import com.saga.be.entity.IdentityMappingHistory;
import com.saga.be.entity.Student;
import com.saga.be.entity.enums.IdentityMappingAction;
import com.saga.be.entity.enums.IdentityMappingStatus;
import com.saga.be.entity.enums.IntegrationProvider;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.IdentityMapRepository;
import com.saga.be.repository.IdentityMappingHistoryRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityMappingService {

    private final IdentityMapRepository identityMapRepository;
    private final IdentityMappingHistoryRepository historyRepository;
    private final StudentRepository studentRepository;

    public IdentityMappingService(
            IdentityMapRepository identityMapRepository,
            IdentityMappingHistoryRepository historyRepository,
            StudentRepository studentRepository
    ) {
        this.identityMapRepository = identityMapRepository;
        this.historyRepository = historyRepository;
        this.studentRepository = studentRepository;
    }

    @Transactional(readOnly = true)
    public PersonalIntegrationsResponse getOwnConnections(SagaPrincipal principal) {
        UUID studentId = requireStudent(principal);
        return new PersonalIntegrationsResponse(
                identityMapRepository.findByStudentIdOrderByProvider(studentId)
                        .stream()
                        .map(IdentityConnectionResponse::from)
                        .toList()
        );
    }

    @Transactional
    public IdentityMap connectVerified(
            SagaPrincipal principal,
            IntegrationProvider provider,
            String externalAccountId,
            String displayName,
            String email
    ) {
        UUID studentId = requireStudent(principal);
        String stableId = requireStableId(externalAccountId);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> IntegrationException.invalid(
                        "STUDENT_NOT_FOUND",
                        "The authenticated Student profile does not exist"
                ));

        Optional<IdentityMap> externalOwner =
                identityMapRepository.findByProviderAndExternalAccountId(
                        provider,
                        stableId
                );
        if (
            externalOwner.isPresent()
            && !externalOwner.get().getStudent().getId().equals(studentId)
        ) {
            throw IntegrationException.conflict(
                    "IDENTITY_ALREADY_LINKED",
                    "This provider identity is already linked to another Student"
            );
        }

        IdentityMap mapping = identityMapRepository
                .findByStudentIdAndProvider(studentId, provider)
                .orElseGet(() -> IdentityMap.builder()
                        .student(student)
                        .provider(provider)
                        .mappingStatus(IdentityMappingStatus.ACTIVE)
                        .build());

        if (
            mapping.getExternalAccountId() != null
            && !mapping.getExternalAccountId().equals(stableId)
            && mapping.getMappingStatus() == IdentityMappingStatus.ACTIVE
        ) {
            throw IntegrationException.conflict(
                    "STUDENT_PROVIDER_ALREADY_LINKED",
                    "Disconnect the current provider identity before connecting another one"
            );
        }

        boolean sameIdentity = stableId.equals(mapping.getExternalAccountId());
        mapping.setExternalAccountId(stableId);
        mapping.setExternalUsername(trimToNull(displayName));
        mapping.setExternalEmail(normalizeMetadataEmail(email));
        mapping.setMappingStatus(IdentityMappingStatus.ACTIVE);
        mapping.setVerifiedAt(LocalDateTime.now());
        mapping.setDisconnectedAt(null);

        try {
            IdentityMap saved = identityMapRepository.saveAndFlush(mapping);
            writeHistory(
                    saved,
                    sameIdentity
                            ? IdentityMappingAction.RECONNECTED
                            : IdentityMappingAction.CONNECTED,
                    principal.cognitoSub()
            );
            return saved;
        } catch (DataIntegrityViolationException exception) {
            throw IntegrationException.conflict(
                    "IDENTITY_LINK_CONFLICT",
                    "The provider identity could not be linked because it is already in use"
            );
        }
    }

    @Transactional
    public void disconnectOwn(
            SagaPrincipal principal,
            IntegrationProvider provider
    ) {
        UUID studentId = requireStudent(principal);
        IdentityMap mapping = identityMapRepository
                .findByStudentIdAndProvider(studentId, provider)
                .orElse(null);
        if (mapping == null || mapping.getMappingStatus()
                == IdentityMappingStatus.DISCONNECTED) {
            return;
        }

        mapping.setMappingStatus(IdentityMappingStatus.DISCONNECTED);
        mapping.setDisconnectedAt(LocalDateTime.now());
        IdentityMap saved = identityMapRepository.saveAndFlush(mapping);
        writeHistory(
                saved,
                IdentityMappingAction.DISCONNECTED,
                principal.cognitoSub()
        );
    }

    @Transactional(readOnly = true)
    public Optional<Student> findActiveStudent(
            IntegrationProvider provider,
            String stableExternalId
    ) {
        return identityMapRepository
                .findByProviderAndExternalAccountIdAndMappingStatus(
                        provider,
                        stableExternalId,
                        IdentityMappingStatus.ACTIVE
                )
                .map(IdentityMap::getStudent);
    }

    private void writeHistory(
            IdentityMap mapping,
            IdentityMappingAction action,
            String actorSub
    ) {
        historyRepository.save(IdentityMappingHistory.builder()
                .identityMap(mapping)
                .student(mapping.getStudent())
                .provider(mapping.getProvider())
                .externalAccountId(mapping.getExternalAccountId())
                .action(action)
                .actorCognitoSub(actorSub)
                .occurredAt(LocalDateTime.now())
                .build());
    }

    private UUID requireStudent(SagaPrincipal principal) {
        if (
            principal == null
            || principal.applicationRole() != ApplicationRole.STUDENT
            || principal.localProfileId() == null
        ) {
            throw IntegrationException.forbidden(
                    "Only a Student may manage personal provider identities"
            );
        }
        return principal.localProfileId();
    }

    private String requireStableId(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || normalized.length() > 255) {
            throw IntegrationException.invalid(
                    "PROVIDER_ID_INVALID",
                    "The provider returned an invalid account identifier"
            );
        }
        return normalized;
    }

    private String normalizeMetadataEmail(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
