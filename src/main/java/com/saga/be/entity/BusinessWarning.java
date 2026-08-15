package com.saga.be.entity;

import com.saga.be.entity.enums.BusinessWarningCategory;
import com.saga.be.entity.enums.BusinessWarningSeverity;
import com.saga.be.entity.enums.NotificationType;
import com.saga.be.entity.enums.SprintProgressMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.sql.Types;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(
        name = "business_warning",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_business_warning_event",
                columnNames = {"event_key"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessWarning extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "warning_type", nullable = false, length = 64)
    private NotificationType warningType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private BusinessWarningCategory category;

    @Column(name = "event_key", nullable = false, length = 255)
    private String eventKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 32)
    private BusinessWarningSeverity severity;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "team_id", length = 36)
    private UUID teamId;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "project_id", length = 36)
    private UUID projectId;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "sprint_id", length = 36)
    private UUID sprintId;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "student_id", length = 36)
    private UUID studentId;

    @Column(name = "commit_sha", length = 64)
    private String commitSha;

    @Column(name = "evidence_summary", nullable = false, length = 1000)
    private String evidenceSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "progress_mode", length = 32)
    private SprintProgressMode progressMode;
}
