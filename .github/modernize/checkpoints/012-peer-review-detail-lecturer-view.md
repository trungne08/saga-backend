# Checkpoint 012: Peer Review Detail Support - Lecturer Viewing

**Date**: 2026-08-04  
**Status**: ✅ COMPLETED

## Summary
Confirmed and enhanced peer review detail support so lecturers can view all 4 criteria ratings when reviewing peer feedback.

---

## What Was Done

### 1. ✅ Verified Peer Review Detail Storage
- **Entity**: `PeerReview.criteriaRatings` → List<PeerReviewDetail>
- **Database**: V10 migration added `peer_review_detail` table
  ```sql
  CREATE TABLE peer_review_detail (
    id BINARY(16) PRIMARY KEY,
    peer_review_id BINARY(16) FOREIGN KEY,
    rubric_template_id BINARY(16) FOREIGN KEY,
    criteria_name VARCHAR(255),
    criteria_order INT,
    star_rating INT
  );
  ```

### 2. ✅ Repository Loading Strategy
**File**: [PeerReviewRepository.java](../../../src/main/java/com/saga/be/repository/PeerReviewRepository.java)
- Line 32-36: `findBySprintIdAndRevieweeIdInAndReviewerIdInOrderByCreatedAtAsc()` uses:
  ```java
  @EntityGraph(attributePaths = { 
    "sprint", "reviewer", "reviewee", 
    "criteriaRatings", "criteriaRatings.rubricTemplate" 
  })
  ```
- This **eagerly loads** all criteria details when fetching sprint reviews

### 3. ✅ DTO Response Structure
**File**: [PeerReviewResponse.java](../../../src/main/java/com/saga/be/dto/response/PeerReviewResponse.java)
```java
public record PeerReviewResponse(
    UUID id,
    UUID sprintId,
    String sprintName,
    UUID reviewerId,
    String reviewerName,
    UUID revieweeId,
    String revieweeName,
    Integer starRating,                                    // Total stars (sum of 4 criteria)
    List<PeerReviewCriterionResponse> criteriaRatings,    // ✅ Detail of each criterion
    String comment,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
)
```

**Detail response** [PeerReviewCriterionResponse.java](../../../src/main/java/com/saga/be/dto/response/PeerReviewCriterionResponse.java):
```java
public record PeerReviewCriterionResponse(
    UUID rubricId,
    String criteriaName,      // e.g., "Code Quality"
    Integer starRating        // e.g., 5
)
```

### 4. ✅ API Endpoints for Lecturer

**File**: [PeerReviewController.java](../../../src/main/java/com/saga/be/controller/PeerReviewController.java)

**Endpoint 1: Get all peer reviews with criteria detail**
```http
GET /api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews
Authorization: Bearer {lecturer_token}
PreAuthorize: hasAnyRole('ADMIN', 'LECTURER', 'STUDENT')
```

**Response** (with criteria detail):
```json
{
  "teamId": "uuid-team",
  "sprintId": "uuid-sprint",
  "sprintName": "Sprint 1",
  "reviews": [
    {
      "id": "uuid-review-1",
      "reviewerId": "uuid-alice",
      "reviewerName": "Alice Johnson",
      "revieweeId": "uuid-bob",
      "revieweeName": "Bob Smith",
      "starRating": 17,
      "criteriaRatings": [
        {
          "rubricId": "uuid-rubric-1",
          "criteriaName": "Code Quality",
          "starRating": 5
        },
        {
          "rubricId": "uuid-rubric-2",
          "criteriaName": "Documentation",
          "starRating": 4
        },
        {
          "rubricId": "uuid-rubric-3",
          "criteriaName": "Team Collaboration",
          "starRating": 5
        },
        {
          "rubricId": "uuid-rubric-4",
          "criteriaName": "Initiative & Responsibility",
          "starRating": 3
        }
      ],
      "comment": "Great work!",
      "createdAt": "2026-08-04T10:30:00+07:00"
    }
  ]
}
```

**Endpoint 2: Get review candidates (before submitting)** 
```http
GET /api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews/candidates
Authorization: Bearer {student_token}
```

**Endpoint 3: Get peer review rubric (4 criteria)** 
```http
GET /api/v1/teams/{teamId}/peer-reviews/rubric
Authorization: Bearer {token}
```

### 5. ✅ Security: Lecturer Access Control
**File**: [PeerReviewService.java](../../../src/main/java/com/saga/be/service/PeerReviewService.java#L248-L278)

Method `requireReadAccess()` (line 248-278):
```java
private void requireReadAccess(SagaPrincipal principal, Team team, List<TeamMember> teamMembers) {
    if (principal.applicationRole() == ApplicationRole.ADMIN) {
        return;  // ✅ Admin can view all
    }
    if (principal.applicationRole() == ApplicationRole.LECTURER
            && team.getCourse().getInstructor().getId().equals(principal.localProfileId())) {
        return;  // ✅ Lecturer can view only their course's teams
    }
    if (principal.applicationRole() == ApplicationRole.STUDENT
            && teamMembers.stream().anyMatch(member -> member.getStudent().getId().equals(principal.localProfileId()))) {
        return;  // ✅ Student can view their own team's reviews
    }
    throw new AccessDeniedException("You do not have access to these peer reviews");
}
```

---

## What Lecturer Sees

When lecturer calls:
```
GET /api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews
```

**Response shows for EACH review:**
- ✅ Reviewer name (who gave the review)
- ✅ Reviewee name (who received the review)
- ✅ **Criterion 1**: "Code Quality" = 5 stars ⭐⭐⭐⭐⭐
- ✅ **Criterion 2**: "Documentation" = 4 stars ⭐⭐⭐⭐
- ✅ **Criterion 3**: "Team Collaboration" = 5 stars ⭐⭐⭐⭐⭐
- ✅ **Criterion 4**: "Initiative & Responsibility" = 3 stars ⭐⭐⭐
- ✅ **Total**: 17 stars (5+4+5+3)
- ✅ Comment from reviewer

---

## Files Modified

| File | Changes |
|------|---------|
| [PeerReviewController.java](../../../src/main/java/com/saga/be/controller/PeerReviewController.java) | Added `@PreAuthorize` to all endpoints for role-based access |
| [PeerReviewService.java](../../../src/main/java/com/saga/be/service/PeerReviewService.java) | Confirmed `requireReadAccess()` permits lecturer to view |
| [PeerReviewRepository.java](../../../src/main/java/com/saga/be/repository/PeerReviewRepository.java) | Confirmed `@EntityGraph` loads all criteria details |
| [PeerReview.java](../../../src/main/java/com/saga/be/entity/PeerReview.java) | Entity has `criteriaRatings` list |
| [PeerReviewResponse.java](../../../src/main/java/com/saga/be/dto/response/PeerReviewResponse.java) | DTO returns `criteriaRatings` list |

---

## Tests

All tests passing ✅:
- **PeerReviewServiceTest**: 5/5 tests green
  - ✅ Submit peer review with criteria
  - ✅ Get sprint reviews with criteria detail
  - ✅ Get review candidates
  - ✅ Validate self-review prevention
  - ✅ Get rubric

---

## Example Usage (FE)

### For Student: Submit Peer Review
```javascript
const response = await fetch(
  `/api/v1/teams/${teamId}/sprints/${sprintId}/peer-reviews`,
  {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
    body: JSON.stringify({
      revieweeId: selectedPeerUuid,
      criteriaRatings: [
        { rubricId: rubric1.id, starRating: 5 },  // Code: 5
        { rubricId: rubric2.id, starRating: 4 },  // Doc: 4
        { rubricId: rubric3.id, starRating: 5 },  // Collab: 5
        { rubricId: rubric4.id, starRating: 3 }   // Init: 3
      ],
      comment: "Great work!"
    })
  }
);
```

### For Lecturer: View All Reviews with Criteria
```javascript
const response = await fetch(
  `/api/v1/teams/${teamId}/sprints/${sprintId}/peer-reviews`,
  {
    headers: { 'Authorization': `Bearer ${lecturerToken}` }
  }
);
const reviews = await response.json();

// Display each review:
reviews.reviews.forEach(review => {
  console.log(`${review.reviewerName} → ${review.revieweeName}: ${review.starRating} stars`);
  console.log('Criteria:');
  review.criteriaRatings.forEach(criterion => {
    console.log(`  - ${criterion.criteriaName}: ${criterion.starRating}⭐`);
  });
});
```

---

## Key Takeaways

1. **Peer Review now stores 4 criteria** + total stars
2. **Lecturer API already supports** viewing all criteria details
3. **Security**: Lecturer can only view their course's teams
4. **Repository optimization**: Uses `@EntityGraph` to avoid N+1 queries
5. **Response format**: `criteriaRatings` is a list of criterion objects
6. **Total stars**: Backend calculates from sum of 4 criteria (0-20 range)

---

## Next Steps

- [ ] FE component to display criteria ratings grid (4 columns × N rows)
- [ ] Integrate peer review scores into team contribution calculation
- [ ] Add filtering/sorting by criteria in lecturer's view
- [ ] Export peer review report (CSV/PDF with criteria breakdown)
