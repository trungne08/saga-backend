package com.saga.be.entity;

import com.saga.be.entity.enums.PullRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "pull_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PullRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id")
    private GitRepo repo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private Student author;

    @Column(name = "author_external_id")
    private String authorExternalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = true)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "git_issue_id", nullable = true)
    private GitIssue gitIssue;

    @Column(name = "title")
    private String title;

    @Column(name = "github_pull_request_id")
    private Long githubPullRequestId;

    @Column(name = "node_id")
    private String nodeId;

    @Column(name = "pull_number")
    private Integer pullNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PullRequestStatus status;

    @Column(name = "merged_at")
    private LocalDateTime mergedAt;

    @Column(name = "review_count")
    private Integer reviewCount;

    @Column(name = "comment_count")
    private Integer commentCount;

    @Column(name = "external_updated_at")
    private LocalDateTime externalUpdatedAt;
}
