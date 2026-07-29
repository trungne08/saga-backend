package com.saga.be.entity;

import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.BoardType;
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
        name = "jira_board",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_jira_board_project",
                    columnNames = "project_id"
            ),
            @UniqueConstraint(
                    name = "uk_jira_cloud_project",
                    columnNames = {"cloud_id", "jira_project_id"}
            )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JiraBoard extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private BoardType type;

    @Column(name = "jira_board_id")
    private String jiraBoardId;

    @Column(name = "cloud_id")
    private String cloudId;

    @Column(name = "site_url")
    private String siteUrl;

    @Column(name = "jira_project_id")
    private String jiraProjectId;

    @Column(name = "project_key")
    private String projectKey;

    @Column(name = "encrypted_access_token", columnDefinition = "TEXT")
    private String encryptedAccessToken;

    @Column(name = "encrypted_refresh_token", columnDefinition = "TEXT")
    private String encryptedRefreshToken;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "granted_scopes", columnDefinition = "TEXT")
    private String grantedScopes;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 32)
    private IntegrationStatus connectionStatus;

    @Column(name = "connected_by_cognito_sub")
    private String connectedByCognitoSub;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connected_by_student_id")
    private Student connectedByStudent;

    @Column(name = "webhook_id")
    private String webhookId;

    @Column(name = "webhook_expires_at")
    private LocalDateTime webhookExpiresAt;

    @Column(name = "webhook_secret_hash", length = 64)
    private String webhookSecretHash;

    @Column(name = "sync_cursor")
    private LocalDateTime syncCursor;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
