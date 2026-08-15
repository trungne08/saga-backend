package com.saga.be.repository;

import com.saga.be.entity.BusinessWarning;
import com.saga.be.entity.enums.NotificationType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessWarningRepository extends JpaRepository<BusinessWarning, UUID> {

    Optional<BusinessWarning> findByEventKey(String eventKey);

    List<BusinessWarning> findByTeamIdOrderByCreatedAtDescIdDesc(UUID teamId);

    List<BusinessWarning> findByProjectIdOrderByCreatedAtDescIdDesc(UUID projectId);

    List<BusinessWarning> findByStudentIdOrderByCreatedAtDescIdDesc(UUID studentId);

    List<BusinessWarning> findByTeamIdInOrderByCreatedAtDescIdDesc(Collection<UUID> teamIds);

    long countByWarningType(NotificationType warningType);

    long countByWarningTypeAndTeamIdIn(NotificationType warningType, Collection<UUID> teamIds);
}
