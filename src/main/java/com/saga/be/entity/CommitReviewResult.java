package com.saga.be.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(
        name = "commit_review_result",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_commit_review_result_intent", columnNames = {"intent_id"}),
            @UniqueConstraint(name = "uk_commit_review_result_job", columnNames = {"ai_job_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommitReviewResult extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intent_id", nullable = false)
    private CommitReviewIntent intent;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "ai_job_id", nullable = false, length = 36)
    private UUID aiJobId;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "project_id", nullable = false, length = 36)
    private UUID projectId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "git_repo_id", nullable = false)
    private GitRepo repo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commit_id", nullable = false)
    private CommitData commit;

    @Column(name = "sha_hash", nullable = false, length = 64)
    private String shaHash;

    @Column(name = "policy_version", nullable = false, length = 64)
    private String policyVersion;

    @Column(name = "review_mode", nullable = false, length = 32)
    private String reviewMode;

    @Column(name = "traceability_status", nullable = false, length = 32)
    private String traceabilityStatus;

    @Column(name = "message_quality", nullable = false, length = 16)
    private String messageQuality;

    @Column(name = "code_quality", nullable = false, length = 32)
    private String codeQuality;

    @Column(name = "inferred_function_label", length = 32)
    private String inferredFunctionLabel;

    @Column(name = "inferred_function_confidence", length = 16)
    private String inferredFunctionConfidence;

    @Column(name = "task_alignment", nullable = false, length = 32)
    private String taskAlignment;

    @Column(name = "verdict_eligible", nullable = false)
    private boolean verdictEligible;

    @Column(name = "verdict", nullable = false, length = 32)
    private String verdict;

    @Column(name = "overall_status", nullable = false, length = 32)
    private String overallStatus;

    @Column(name = "schema_version", nullable = false, length = 64)
    private String schemaVersion;

    @JdbcTypeCode(Types.LONGVARCHAR)
    @Column(name = "findings_json", columnDefinition = "MEDIUMTEXT")
    private String findingsJson;

    @JdbcTypeCode(Types.LONGVARCHAR)
    @Column(name = "evidence_refs_json", columnDefinition = "MEDIUMTEXT")
    private String evidenceRefsJson;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;
}
