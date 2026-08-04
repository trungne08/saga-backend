package com.saga.be.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "peer_review_detail")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeerReviewDetail extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "peer_review_id", nullable = false)
    private PeerReview peerReview;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rubric_id", nullable = false)
    private RubricTemplate rubricTemplate;

    @Column(name = "criteria_name", nullable = false)
    private String criteriaName;

    @Column(name = "criteria_order", nullable = false)
    private Integer criteriaOrder;

    @Column(name = "star_rating", nullable = false)
    private Integer starRating;
}
