package com.saga.be.entity;

import com.saga.be.entity.enums.PrReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "pr_review")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrReview extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pr_id")
    private PullRequest pullRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private Student reviewer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PrReviewStatus status;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;
}
