package com.saga.be.entity;

import com.saga.be.entity.enums.IdentityMappingStatus;
import com.saga.be.entity.enums.IntegrationProvider;
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
        name = "identity_map",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_identity_student_provider",
                    columnNames = {"student_id", "provider"}
            ),
            @UniqueConstraint(
                    name = "uk_identity_provider_external",
                    columnNames = {"provider", "external_account_id"}
            )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdentityMap extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private IntegrationProvider provider;

    @Column(name = "external_account_id", length = 255)
    private String externalAccountId;

    @Column(name = "external_username")
    private String externalUsername;

    @Column(name = "external_email")
    private String externalEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_status", nullable = false, length = 32)
    private IdentityMappingStatus mappingStatus;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "disconnected_at")
    private LocalDateTime disconnectedAt;

    @Column(name = "reviewed_by_cognito_sub")
    private String reviewedByCognitoSub;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
