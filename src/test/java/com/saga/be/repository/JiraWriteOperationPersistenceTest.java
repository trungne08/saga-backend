package com.saga.be.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.saga.be.entity.BaseEntity;
import com.saga.be.entity.JiraWriteOperation;
import com.saga.be.entity.Project;
import com.saga.be.entity.enums.JiraWriteOperationStatus;
import com.saga.be.entity.enums.JiraWriteOperationType;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.JoinColumn;
import java.lang.reflect.Field;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class JiraWriteOperationPersistenceTest {

    @Autowired
    private JiraWriteOperationRepository operations;

    @Autowired
    private ProjectRepository projects;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsUuidColumnsAndFindsOperationByProjectAndIdempotencyKey() {
        Project project = projects.saveAndFlush(
                Project.builder().name("Jira write persistence").build()
        );
        UUID projectId = project.getId();
        UUID actorProfileId = UUID.fromString(
                "20000000-0000-0000-0000-000000000002"
        );
        LocalDateTime completedAt = LocalDateTime.parse(
                "2026-08-06T03:15:00"
        );

        JiraWriteOperation saved = operations.saveAndFlush(
                JiraWriteOperation.builder()
                        .project(project)
                        .actorProfileId(actorProfileId)
                        .operationType(JiraWriteOperationType.TASK_UPDATE)
                        .idempotencyKey("round-trip-key")
                        .requestFingerprint("a".repeat(64))
                        .remoteResourceId("10001")
                        .remoteResourceKey("SAGA-1")
                        .status(JiraWriteOperationStatus.COMPLETED)
                        .safeErrorCode(null)
                        .completedAt(completedAt)
                        .build()
        );
        UUID operationId = saved.getId();
        entityManager.clear();

        JiraWriteOperation reloaded = operations
                .findByProjectIdAndIdempotencyKey(projectId, "round-trip-key")
                .orElseThrow();

        assertEquals(operationId, reloaded.getId());
        assertEquals(projectId, reloaded.getProject().getId());
        assertEquals(actorProfileId, reloaded.getActorProfileId());
        assertEquals(JiraWriteOperationType.TASK_UPDATE, reloaded.getOperationType());
        assertEquals("a".repeat(64), reloaded.getRequestFingerprint());
        assertEquals("10001", reloaded.getRemoteResourceId());
        assertEquals("SAGA-1", reloaded.getRemoteResourceKey());
        assertEquals(JiraWriteOperationStatus.COMPLETED, reloaded.getStatus());
        assertEquals(completedAt, reloaded.getCompletedAt());
    }

    @Test
    void declaresFixedCharacterMappingsThatMatchV17() throws Exception {
        assertCharMapping(BaseEntity.class.getDeclaredField("id"), 36);
        assertCharMapping(
                JiraWriteOperation.class.getDeclaredField("actorProfileId"),
                36
        );

        Field project = JiraWriteOperation.class.getDeclaredField("project");
        assertEquals("project_id", project.getAnnotation(JoinColumn.class).name());

        Field fingerprint = JiraWriteOperation.class.getDeclaredField(
                "requestFingerprint"
        );
        assertEquals(
                Types.CHAR,
                fingerprint.getAnnotation(JdbcTypeCode.class).value()
        );
        Column fingerprintColumn = fingerprint.getAnnotation(Column.class);
        assertEquals("request_fingerprint", fingerprintColumn.name());
        assertEquals(64, fingerprintColumn.length());
    }

    private void assertCharMapping(Field field, int length) {
        JdbcTypeCode jdbcType = field.getAnnotation(JdbcTypeCode.class);
        Column column = field.getAnnotation(Column.class);
        assertNotNull(jdbcType);
        assertNotNull(column);
        assertEquals(Types.CHAR, jdbcType.value());
        assertEquals("char(" + length + ")", column.columnDefinition());
    }
}
