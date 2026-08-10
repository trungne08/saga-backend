package com.saga.be.entity;

import com.saga.be.security.ApplicationRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
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
        name = "firebase_installation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_firebase_installation_fid",
                columnNames = "firebase_installation_id"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FirebaseInstallation extends BaseEntity {

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "owner_profile_id", nullable = false, columnDefinition = "char(36)")
    private UUID ownerProfileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_role", nullable = false, length = 32)
    private ApplicationRole ownerRole;

    @Column(name = "firebase_installation_id", nullable = false, length = 255)
    private String firebaseInstallationId;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "last_registered_at", nullable = false)
    private LocalDateTime lastRegisteredAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
