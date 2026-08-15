package com.saga.be.entity;

import com.saga.be.entity.enums.IntegrationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "git_repo",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_git_repo_provider_id",
                    columnNames = {"provider", "repository_id"}
            ),
            @UniqueConstraint(
                    name = "uk_git_repo_project_full_name",
                    columnNames = {"project_id", "full_name"}
            )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitRepo extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;


    @Column(name = "name")
    private String name;

    @Column(name = "url")
    private String url;

    @Column(name = "provider")
    private String provider;

    @Column(name = "repository_id")
    private Long repositoryId;

    @Column(name = "owner_login")
    private String ownerLogin;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "default_branch")
    private String defaultBranch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "installation_id")
    private GitHubInstallation installation;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 32)
    private IntegrationStatus connectionStatus;

    @Column(name = "sync_cursor")
    private LocalDateTime syncCursor;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "review_cutover_at")
    private LocalDateTime reviewCutoverAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
