package com.saga.be.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import java.util.List;
import lombok.Data;

@Data
public class PeerReviewRequest {
    @NotNull
    private UUID revieweeId;

    @Min(0)
    @Max(5)
    private Integer starRating;

    @Valid
    @Size(max = 4)
    private List<PeerReviewCriterionRequest> criteriaRatings;

    @Size(max = 2000)
    private String comment;
}
