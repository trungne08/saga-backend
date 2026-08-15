package com.saga.be.entity;

import com.saga.be.entity.enums.TraceabilityRelationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "git_issue_commit_link",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_git_issue_commit_link_pair",
                columnNames = {"git_issue_id", "commit_id"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitIssueCommitLink extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "git_issue_id", nullable = false)
    private GitIssue gitIssue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commit_id", nullable = false)
    private CommitData commit;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 32)
    private TraceabilityRelationType relationType;
}
