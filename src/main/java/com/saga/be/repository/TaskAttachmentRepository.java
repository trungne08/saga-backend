package com.saga.be.repository;

import com.saga.be.entity.TaskAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, UUID> {

    List<TaskAttachment> findByTaskId(UUID taskId);

    List<TaskAttachment> findByTask_Project_Id(UUID projectId);

    Optional<TaskAttachment> findByTaskIdAndExternalId(UUID taskId, String externalId);
}
