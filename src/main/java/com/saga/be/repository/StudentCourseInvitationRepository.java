package com.saga.be.repository;

import com.saga.be.entity.StudentCourseInvitation;
import com.saga.be.entity.enums.StudentInvitationStatus;
import com.saga.be.entity.enums.StudentInvitationType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentCourseInvitationRepository
        extends JpaRepository<StudentCourseInvitation, UUID> {

    Optional<StudentCourseInvitation> findByStudentIdAndCourseIdAndInvitationType(
            UUID studentId,
            UUID courseId,
            StudentInvitationType invitationType
    );

    @EntityGraph(attributePaths = "student")
    Page<StudentCourseInvitation> findByCourseId(UUID courseId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from StudentCourseInvitation invitation where invitation.id = :id")
    Optional<StudentCourseInvitation> findForUpdateById(@Param("id") UUID id);

    @Query("select invitation.id from StudentCourseInvitation invitation "
            + "where invitation.invitationStatus in :statuses order by invitation.createdAt asc")
    List<UUID> findTop100IdsByInvitationStatusInOrderByCreatedAtAsc(
            @Param("statuses") List<StudentInvitationStatus> statuses
    );

    @Query("select invitation.id from StudentCourseInvitation invitation "
            + "where invitation.invitationStatus = :status "
            + "and invitation.processingStartedAt < :staleBefore "
            + "order by invitation.processingStartedAt asc")
    List<UUID> findTop100IdsByProcessingStartedAtBefore(
            @Param("status") StudentInvitationStatus status,
            @Param("staleBefore") java.time.LocalDateTime staleBefore
    );
}
