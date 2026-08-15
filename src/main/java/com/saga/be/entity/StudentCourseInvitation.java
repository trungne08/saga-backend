package com.saga.be.entity;

import com.saga.be.entity.enums.StudentInvitationStatus;
import com.saga.be.entity.enums.StudentInvitationType;
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
        name = "student_course_invitation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_student_course_invitation_type",
                columnNames = {"student_id", "course_id", "invitation_type"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentCourseInvitation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "invitation_type", nullable = false, length = 32)
    private StudentInvitationType invitationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "invitation_status", nullable = false, length = 32)
    private StudentInvitationStatus invitationStatus;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
