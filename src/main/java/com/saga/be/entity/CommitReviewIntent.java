package com.saga.be.entity;

import com.saga.be.entity.enums.CommitReviewIntentStatus;
import com.saga.be.entity.enums.CommitReviewMode;
import com.saga.be.entity.enums.CommitReviewPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
        name = "commit_review_intent",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_commit_review_intent_repo_sha",
                columnNames = {"git_repo_id", "sha_hash"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommitReviewIntent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "git_repo_id", nullable = false)
    private GitRepo repo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commit_id", nullable = false)
    private CommitData commit;

    @Column(name = "sha_hash", nullable = false, length = 64)
    private String shaHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_mode", nullable = false, length = 32)
    private CommitReviewMode reviewMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private CommitReviewPriority priority;

    @Column(name = "priority_rank", nullable = false)
    private int priorityRank;

    @Enumerated(EnumType.STRING)
    @Column(name = "intent_status", nullable = false, length = 32)
    private CommitReviewIntentStatus intentStatus;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "ai_job_id", length = 36)
    private UUID aiJobId;

    @Column(name = "review_policy_version", length = 64)
    private String reviewPolicyVersion;

    @Column(name = "last_job_status", length = 32)
    private String lastJobStatus;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "safe_error_code", length = 64)
    private String safeErrorCode;
}
