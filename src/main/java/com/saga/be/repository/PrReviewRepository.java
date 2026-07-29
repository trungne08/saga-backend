package com.saga.be.repository;

import com.saga.be.entity.PrReview;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PrReviewRepository extends JpaRepository<PrReview, UUID> {
    Optional<PrReview> findByGithubReviewId(Long githubReviewId);
}
