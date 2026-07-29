package com.saga.be.entity;

import com.saga.be.entity.enums.GitHubInstallationStatus;
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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "github_installation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_github_installation_id",
                columnNames = "installation_id"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitHubInstallation extends BaseEntity {

    @Column(name = "installation_id", nullable = false)
    private Long installationId;

    @Column(name = "installed_by_cognito_sub", nullable = false)
    private String installedByCognitoSub;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "installed_by_student_id")
    private Student installedByStudent;

    @Column(name = "account_login")
    private String accountLogin;

    @Column(name = "account_type")
    private String accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "installation_status", nullable = false, length = 32)
    private GitHubInstallationStatus installationStatus;

    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
