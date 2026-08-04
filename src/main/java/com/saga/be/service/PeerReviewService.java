package com.saga.be.service;

import com.saga.be.dto.request.PeerReviewCriterionRequest;
import com.saga.be.dto.request.PeerReviewRequest;
import com.saga.be.dto.response.PeerReviewCandidateResponse;
import com.saga.be.dto.response.PeerReviewCandidatesResponse;
import com.saga.be.dto.response.PeerReviewResponse;
import com.saga.be.dto.response.PeerReviewRubricItemResponse;
import com.saga.be.dto.response.PeerReviewRubricResponse;
import com.saga.be.dto.response.SprintPeerReviewResponse;
import com.saga.be.entity.PeerReview;
import com.saga.be.entity.PeerReviewDetail;
import com.saga.be.entity.RubricTemplate;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.repository.PeerReviewRepository;
import com.saga.be.repository.RubricTemplateRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PeerReviewService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final SprintRepository sprintRepository;
    private final PeerReviewRepository peerReviewRepository;
    private final RubricTemplateRepository rubricTemplateRepository;

    @Transactional
    public PeerReviewResponse submit(
            SagaPrincipal principal,
            UUID teamId,
            UUID sprintId,
            PeerReviewRequest request
    ) {
        Team team = requireTeam(teamId);
        Sprint sprint = requireSprint(team, sprintId);
        Map<UUID, TeamMember> membersByStudentId = teamMemberRepository.findByTeamId(teamId)
                .stream()
                .filter(member -> member.getStudent() != null && member.getStudent().getId() != null)
                .collect(Collectors.toMap(
                        member -> member.getStudent().getId(),
                        Function.identity()
                ));

        requireStudentReviewAccess(principal, team, membersByStudentId);
        UUID reviewerId = principal.localProfileId();
        UUID revieweeId = request.getRevieweeId();

        if (Objects.equals(reviewerId, revieweeId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A student cannot peer review themselves"
            );
        }

        TeamMember reviewerMembership = membersByStudentId.get(reviewerId);
        TeamMember revieweeMembership = membersByStudentId.get(revieweeId);
        if (reviewerMembership == null || revieweeMembership == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reviewer and reviewee must belong to the same team"
            );
        }

        PeerReview peerReview = peerReviewRepository
                .findBySprintIdAndReviewerIdAndRevieweeId(
                        sprintId,
                        reviewerId,
                        revieweeId
                )
                .orElseGet(PeerReview::new);
        peerReview.setSprint(sprint);
        peerReview.setReviewer(reviewerMembership.getStudent());
        peerReview.setReviewee(revieweeMembership.getStudent());
        applyRatings(team, peerReview, request);
        peerReview.setComment(request.getComment());
        return PeerReviewResponse.from(peerReviewRepository.saveAndFlush(peerReview));
    }

    @Transactional
    public SprintPeerReviewResponse getSprintReviews(
            SagaPrincipal principal,
            UUID teamId,
            UUID sprintId
    ) {
        Team team = requireTeam(teamId);
        Sprint sprint = requireSprint(team, sprintId);
        List<TeamMember> teamMembers = teamMemberRepository.findByTeamId(teamId);
        requireReadAccess(principal, team, teamMembers);
        List<UUID> memberIds = teamMembers.stream()
                .map(TeamMember::getStudent)
                .filter(student -> student != null && student.getId() != null)
                .map(Student::getId)
                .toList();
        List<PeerReviewResponse> reviews = memberIds.isEmpty()
                ? List.of()
                : peerReviewRepository
                        .findBySprintIdAndRevieweeIdInAndReviewerIdInOrderByCreatedAtAsc(
                                sprintId,
                                memberIds,
                                memberIds
                        )
                        .stream()
                        .map(PeerReviewResponse::from)
                        .toList();
        return new SprintPeerReviewResponse(
                teamId,
                sprintId,
                sprint.getName(),
                reviews
        );
    }

    @Transactional
    public PeerReviewCandidatesResponse getReviewCandidates(
            SagaPrincipal principal,
            UUID teamId,
            UUID sprintId
    ) {
        Team team = requireTeam(teamId);
        Sprint sprint = requireSprint(team, sprintId);
        Map<UUID, TeamMember> membersByStudentId = teamMemberRepository.findByTeamId(teamId)
                .stream()
                .filter(member -> member.getStudent() != null && member.getStudent().getId() != null)
                .collect(Collectors.toMap(
                        member -> member.getStudent().getId(),
                        Function.identity()
                ));
        requireStudentReviewAccess(principal, team, membersByStudentId);
        UUID reviewerId = principal.localProfileId();
        List<TeamMember> candidateMembers = membersByStudentId.values().stream()
                .filter(member -> !Objects.equals(member.getStudent().getId(), reviewerId))
                .sorted(Comparator.comparing(
                        member -> member.getStudent().getStudentCode(),
                        Comparator.nullsLast(String::compareToIgnoreCase)
                ))
                .toList();
        List<UUID> candidateIds = candidateMembers.stream()
                .map(member -> member.getStudent().getId())
                .toList();
        Map<UUID, PeerReview> existingReviewsByReviewee = peerReviewRepository
                .findBySprintIdAndReviewerIdAndRevieweeIdIn(sprintId, reviewerId, candidateIds)
                .stream()
                .collect(Collectors.toMap(
                        review -> review.getReviewee().getId(),
                        Function.identity()
                ));
        List<PeerReviewCandidateResponse> candidates = candidateMembers.stream()
                .map(member -> {
                    PeerReview existingReview = existingReviewsByReviewee.get(member.getStudent().getId());
                    return new PeerReviewCandidateResponse(
                            member.getStudent().getId(),
                            member.getStudent().getFullName(),
                            member.getStudent().getStudentCode(),
                            existingReview != null,
                            existingReview != null ? existingReview.getId() : null,
                            existingReview != null ? existingReview.getStarRating() : null
                    );
                })
                .toList();
        return new PeerReviewCandidatesResponse(teamId, sprint.getId(), reviewerId, candidates);
    }

    @Transactional
    public PeerReviewRubricResponse getPeerReviewRubric(
            SagaPrincipal principal,
            UUID teamId
    ) {
        Team team = requireTeam(teamId);
        List<TeamMember> teamMembers = teamMemberRepository.findByTeamId(teamId);
        requireReadAccess(principal, team, teamMembers);
        UUID subjectId = team.getCourse() != null && team.getCourse().getSubject() != null
                ? team.getCourse().getSubject().getId()
                : null;
        List<PeerReviewRubricItemResponse> criteria = subjectId == null
                ? List.of()
                : rubricTemplateRepository.findBySubjectIdOrderByCreatedAtAsc(subjectId).stream()
                        .map(PeerReviewRubricItemResponse::from)
                        .toList();
        return new PeerReviewRubricResponse(teamId, subjectId, criteria);
    }

    private Team requireTeam(UUID teamId) {
        return teamRepository.findWithCourseAndInstructorById(teamId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Team not found"
                ));
    }

    private Sprint requireSprint(Team team, UUID sprintId) {
        if (team.getProject() == null || team.getProject().getId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Team does not have a linked project"
            );
        }
        return sprintRepository.findByIdAndBoardProjectId(
                        sprintId,
                        team.getProject().getId()
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Sprint not found for team project"
                ));
    }

    private void requireStudentReviewAccess(
            SagaPrincipal principal,
            Team team,
            Map<UUID, TeamMember> membersByStudentId
    ) {
        if (principal == null) {
            throw new AccessDeniedException("Authentication is required");
        }
        if (principal.applicationRole() != ApplicationRole.STUDENT) {
            throw new AccessDeniedException("Only students can submit peer reviews");
        }
        if (!membersByStudentId.containsKey(principal.localProfileId())) {
            throw new AccessDeniedException("You do not belong to this team");
        }
    }

    private void requireReadAccess(
            SagaPrincipal principal,
            Team team,
            List<TeamMember> teamMembers
    ) {
        if (principal == null) {
            throw new AccessDeniedException("Authentication is required");
        }
        if (principal.applicationRole() == ApplicationRole.ADMIN) {
            return;
        }
        if (principal.applicationRole() == ApplicationRole.LECTURER
                && team.getCourse() != null
                && team.getCourse().getInstructor() != null
                && Objects.equals(
                        principal.localProfileId(),
                        team.getCourse().getInstructor().getId()
                )) {
            return;
        }
        if (principal.applicationRole() == ApplicationRole.STUDENT
                && teamMembers.stream().anyMatch(member ->
                        member.getStudent() != null
                                && Objects.equals(
                                        member.getStudent().getId(),
                                        principal.localProfileId()
                                ))) {
            return;
        }
        throw new AccessDeniedException("You do not have access to these peer reviews");
    }

    private void applyRatings(Team team, PeerReview peerReview, PeerReviewRequest request) {
        if (request.getCriteriaRatings() == null || request.getCriteriaRatings().isEmpty()) {
            if (request.getStarRating() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Peer review must include either detailed criteria ratings or a total star rating"
                );
            }
            peerReview.setStarRating(request.getStarRating());
            ensureCriteriaList(peerReview).clear();
            return;
        }

        UUID subjectId = team.getCourse() != null && team.getCourse().getSubject() != null
                ? team.getCourse().getSubject().getId()
                : null;
        if (subjectId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Team course does not have a subject rubric configured"
            );
        }

        List<RubricTemplate> rubrics = rubricTemplateRepository.findBySubjectIdOrderByCreatedAtAsc(subjectId);
        if (rubrics.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Peer review rubric is not configured for this subject"
            );
        }
        if (request.getCriteriaRatings().size() != rubrics.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "All rubric criteria must be rated exactly once"
            );
        }

        Map<UUID, RubricTemplate> rubricById = rubrics.stream()
                .collect(Collectors.toMap(RubricTemplate::getId, Function.identity()));
        Set<UUID> seenRubricIds = new HashSet<>();
        List<PeerReviewDetail> details = new ArrayList<>();
        int totalStars = 0;
        int order = 0;
        for (PeerReviewCriterionRequest criteriaRating : request.getCriteriaRatings()) {
            RubricTemplate rubric = rubricById.get(criteriaRating.getRubricId());
            if (rubric == null || !seenRubricIds.add(criteriaRating.getRubricId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "All rubric criteria must be rated exactly once"
                );
            }
            PeerReviewDetail detail = PeerReviewDetail.builder()
                    .peerReview(peerReview)
                    .rubricTemplate(rubric)
                    .criteriaName(rubric.getCriteriaName())
                    .criteriaOrder(order++)
                    .starRating(criteriaRating.getStarRating())
                    .build();
            details.add(detail);
            totalStars += criteriaRating.getStarRating();
        }
        if (seenRubricIds.size() != rubrics.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "All rubric criteria must be rated exactly once"
            );
        }

        peerReview.setStarRating(totalStars);
        List<PeerReviewDetail> managedDetails = ensureCriteriaList(peerReview);
        managedDetails.clear();
        managedDetails.addAll(details);
    }

    private List<PeerReviewDetail> ensureCriteriaList(PeerReview peerReview) {
        if (peerReview.getCriteriaRatings() == null) {
            peerReview.setCriteriaRatings(new ArrayList<>());
        }
        return peerReview.getCriteriaRatings();
    }
}
