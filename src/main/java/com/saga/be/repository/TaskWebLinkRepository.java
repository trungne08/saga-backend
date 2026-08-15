package com.saga.be.repository;

import com.saga.be.entity.TaskWebLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskWebLinkRepository extends JpaRepository<TaskWebLink, UUID> {

    List<TaskWebLink> findByTaskId(UUID taskId);

    List<TaskWebLink> findByTask_Project_Id(UUID projectId);

    Optional<TaskWebLink> findByTaskIdAndUrl(UUID taskId, String url);
}
