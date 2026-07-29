package com.saga.be.integration.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.IdentityMap;
import com.saga.be.entity.IdentityMappingHistory;
import com.saga.be.entity.Student;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.IdentityMappingStatus;
import com.saga.be.entity.enums.IntegrationProvider;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.IdentityMapRepository;
import com.saga.be.repository.IdentityMappingHistoryRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IdentityMappingServiceTest {

    private IdentityMapRepository mappingRepository;
    private IdentityMappingHistoryRepository historyRepository;
    private StudentRepository studentRepository;
    private IdentityMappingService service;

    @BeforeEach
    void setUp() {
        mappingRepository = mock(IdentityMapRepository.class);
        historyRepository = mock(IdentityMappingHistoryRepository.class);
        studentRepository = mock(StudentRepository.class);
        service = new IdentityMappingService(
                mappingRepository,
                historyRepository,
                studentRepository
        );
        when(mappingRepository.saveAndFlush(any(IdentityMap.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rejectsStableProviderIdentityOwnedByAnotherStudent() {
        UUID currentId = UUID.randomUUID();
        Student other = student(UUID.randomUUID());
        when(studentRepository.findById(currentId))
                .thenReturn(Optional.of(student(currentId)));
        IdentityMap existing = IdentityMap.builder()
                .student(other)
                .provider(IntegrationProvider.GITHUB)
                .externalAccountId("99123")
                .mappingStatus(IdentityMappingStatus.ACTIVE)
                .build();
        when(mappingRepository.findByProviderAndExternalAccountId(
                IntegrationProvider.GITHUB,
                "99123"
        )).thenReturn(Optional.of(existing));

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> service.connectVerified(
                        principal(currentId),
                        IntegrationProvider.GITHUB,
                        "99123",
                        "mutable-login",
                        null
                )
        );

        assertEquals("IDENTITY_ALREADY_LINKED", exception.getCode());
        verify(mappingRepository, never()).saveAndFlush(any());
    }

    @Test
    void reconnectingSameIdentityIsIdempotentAndRefreshesMetadata() {
        UUID studentId = UUID.randomUUID();
        Student student = student(studentId);
        IdentityMap existing = IdentityMap.builder()
                .student(student)
                .provider(IntegrationProvider.JIRA)
                .externalAccountId("atlassian-account-id")
                .externalUsername("Old Name")
                .mappingStatus(IdentityMappingStatus.DISCONNECTED)
                .build();
        when(studentRepository.findById(studentId))
                .thenReturn(Optional.of(student));
        when(mappingRepository.findByProviderAndExternalAccountId(
                IntegrationProvider.JIRA,
                "atlassian-account-id"
        )).thenReturn(Optional.of(existing));
        when(mappingRepository.findByStudentIdAndProvider(
                studentId,
                IntegrationProvider.JIRA
        )).thenReturn(Optional.of(existing));

        IdentityMap result = service.connectVerified(
                principal(studentId),
                IntegrationProvider.JIRA,
                "atlassian-account-id",
                "New Name",
                "USER@EXAMPLE.COM"
        );

        assertEquals(IdentityMappingStatus.ACTIVE, result.getMappingStatus());
        assertEquals("New Name", result.getExternalUsername());
        assertEquals("user@example.com", result.getExternalEmail());
        assertNull(result.getDisconnectedAt());
        verify(historyRepository).save(any(IdentityMappingHistory.class));
    }

    @Test
    void activeIdentityMustBeDisconnectedBeforeSwitchingAccounts() {
        UUID studentId = UUID.randomUUID();
        Student student = student(studentId);
        IdentityMap existing = IdentityMap.builder()
                .student(student)
                .provider(IntegrationProvider.GITHUB)
                .externalAccountId("100")
                .mappingStatus(IdentityMappingStatus.ACTIVE)
                .build();
        when(studentRepository.findById(studentId))
                .thenReturn(Optional.of(student));
        when(mappingRepository.findByProviderAndExternalAccountId(
                IntegrationProvider.GITHUB,
                "200"
        )).thenReturn(Optional.empty());
        when(mappingRepository.findByStudentIdAndProvider(
                studentId,
                IntegrationProvider.GITHUB
        )).thenReturn(Optional.of(existing));

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> service.connectVerified(
                        principal(studentId),
                        IntegrationProvider.GITHUB,
                        "200",
                        "new-login",
                        null
                )
        );
        assertEquals("STUDENT_PROVIDER_ALREADY_LINKED", exception.getCode());
    }

    @Test
    void disconnectRetainsStableIdAndHistoricalRow() {
        UUID studentId = UUID.randomUUID();
        IdentityMap existing = IdentityMap.builder()
                .student(student(studentId))
                .provider(IntegrationProvider.JIRA)
                .externalAccountId("stable-id")
                .mappingStatus(IdentityMappingStatus.ACTIVE)
                .build();
        when(mappingRepository.findByStudentIdAndProvider(
                studentId,
                IntegrationProvider.JIRA
        )).thenReturn(Optional.of(existing));

        service.disconnectOwn(principal(studentId), IntegrationProvider.JIRA);

        assertEquals(IdentityMappingStatus.DISCONNECTED, existing.getMappingStatus());
        assertEquals("stable-id", existing.getExternalAccountId());
        assertTrue(existing.getDisconnectedAt() != null);
        verify(mappingRepository, never()).delete(any());
        verify(historyRepository).save(any(IdentityMappingHistory.class));
    }

    @Test
    void nonStudentCannotManagePersonalConnection() {
        SagaPrincipal admin = new SagaPrincipal(
                "admin-sub",
                "admin@example.com",
                "Admin",
                ApplicationRole.ADMIN,
                UUID.randomUUID(),
                AccountStatus.ACTIVE
        );

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> service.disconnectOwn(admin, IntegrationProvider.GITHUB)
        );
        assertEquals("INTEGRATION_FORBIDDEN", exception.getCode());
    }

    private Student student(UUID id) {
        Student student = Student.builder()
                .cognitoSub("student-" + id)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        student.setId(id);
        return student;
    }

    private SagaPrincipal principal(UUID studentId) {
        return new SagaPrincipal(
                "student-sub",
                "student@example.com",
                "Student",
                ApplicationRole.STUDENT,
                studentId,
                AccountStatus.ACTIVE
        );
    }
}
