package com.saga.be.integration.write;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saga.be.entity.JiraWriteOperation;
import com.saga.be.entity.Project;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.JiraWriteOperationStatus;
import com.saga.be.entity.enums.JiraWriteOperationType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.JiraWriteOperationRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class JiraWriteOperationServiceTest {

    @Test
    void duplicateClaimRollsBackAttemptThenReloadsInNewTransaction() {
        JiraWriteOperationRepository repository = mock(JiraWriteOperationRepository.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));
        Project project = Project.builder().build();
        project.setId(UUID.randomUUID());
        SagaPrincipal actor = new SagaPrincipal("sub", "a@test", "A", ApplicationRole.ADMIN,
                UUID.randomUUID(), AccountStatus.ACTIVE);
        JiraWriteOperation existing = JiraWriteOperation.builder()
                .operationType(JiraWriteOperationType.TASK_CREATE).requestFingerprint("same")
                .status(JiraWriteOperationStatus.COMPLETED).build();
        when(repository.findByProjectIdAndIdempotencyKey(project.getId(), "key"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "Duplicate entry for key uk_jira_write_operation_project_key"));

        JiraWriteOperation claimed = new JiraWriteOperationService(repository, transactions)
                .claim(project, actor, JiraWriteOperationType.TASK_CREATE, "key", "same");

        assertSame(existing, claimed);
    }

    @Test
    void reusingExistingKeyWithDifferentFingerprintIsConflict() {
        JiraWriteOperationRepository repository = mock(JiraWriteOperationRepository.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));
        Project project = Project.builder().build(); project.setId(UUID.randomUUID());
        SagaPrincipal actor = new SagaPrincipal("sub", "a@test", "A", ApplicationRole.ADMIN, UUID.randomUUID(), AccountStatus.ACTIVE);
        JiraWriteOperation existing = JiraWriteOperation.builder().operationType(JiraWriteOperationType.TASK_CREATE)
                .requestFingerprint("first").status(JiraWriteOperationStatus.COMPLETED).build();
        when(repository.findByProjectIdAndIdempotencyKey(project.getId(), "key")).thenReturn(Optional.of(existing));
        assertEquals("JIRA_IDEMPOTENCY_KEY_REUSED", assertThrows(IntegrationException.class,
                () -> new JiraWriteOperationService(repository, transactions)
                        .claim(project, actor, JiraWriteOperationType.TASK_CREATE, "key", "second")).getCode());
    }

    @Test
    void unrelatedConstraintViolationIsNotMisclassifiedAsIdempotencyRace() {
        JiraWriteOperationRepository repository = mock(JiraWriteOperationRepository.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));
        Project project = Project.builder().build(); project.setId(UUID.randomUUID());
        SagaPrincipal actor = new SagaPrincipal("sub", "a@test", "A", ApplicationRole.ADMIN,
                UUID.randomUUID(), AccountStatus.ACTIVE);
        when(repository.findByProjectIdAndIdempotencyKey(project.getId(), "key")).thenReturn(Optional.empty());
        DataIntegrityViolationException databaseError = new DataIntegrityViolationException("fk_unrelated");
        when(repository.saveAndFlush(any())).thenThrow(databaseError);

        assertSame(databaseError, assertThrows(DataIntegrityViolationException.class,
                () -> new JiraWriteOperationService(repository, transactions)
                        .claim(project, actor, JiraWriteOperationType.TASK_CREATE, "key", "same")));
    }
}
