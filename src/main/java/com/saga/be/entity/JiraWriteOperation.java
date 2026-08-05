package com.saga.be.entity;

import com.saga.be.entity.enums.JiraWriteOperationStatus;
import com.saga.be.entity.enums.JiraWriteOperationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "jira_write_operation", uniqueConstraints = @UniqueConstraint(
        name = "uk_jira_write_operation_project_key",
        columnNames = {"project_id", "idempotency_key"}
))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JiraWriteOperation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "actor_profile_id", nullable = false)
    private UUID actorProfileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 32)
    private JiraWriteOperationType operationType;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "remote_resource_id")
    private String remoteResourceId;

    @Column(name = "remote_resource_key")
    private String remoteResourceKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private JiraWriteOperationStatus status;

    @Column(name = "safe_error_code", length = 64)
    private String safeErrorCode;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
