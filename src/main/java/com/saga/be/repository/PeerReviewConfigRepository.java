package com.saga.be.repository;

import com.saga.be.entity.PeerReviewConfig;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PeerReviewConfigRepository extends JpaRepository<PeerReviewConfig, UUID> {

    @Query("select config from PeerReviewConfig config left join config.subject subject "
            + "where config.starRating = :starRating "
            + "and (subject.id = :subjectId or subject is null)")
    List<PeerReviewConfig> findApplicableBySubjectIdAndStarRating(
            @Param("subjectId") UUID subjectId,
            @Param("starRating") Integer starRating
    );
}
