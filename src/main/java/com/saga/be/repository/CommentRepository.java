package com.saga.be.repository;

import com.saga.be.entity.Comment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {
    Optional<Comment> findBySourceSystemAndExternalCommentId(
            String sourceSystem,
            String externalCommentId
    );
}
