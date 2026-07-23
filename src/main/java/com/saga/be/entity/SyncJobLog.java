package com.saga.be.entity;

import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.entity.enums.SyncJobType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;
import java.sql.Types;

@Entity
@Table(name = "sync_job_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncJobLog extends BaseEntity {

    @Column(name = "target_system")
    private String targetSystem;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "target_id", columnDefinition = "char(36)")
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type")
    private SyncJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private SyncJobStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
