package com.saga.be.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.dto.response.PeerReviewCriterionResponse;
import com.saga.be.entity.Assessment;
import com.saga.be.entity.PeerReview;
import com.saga.be.entity.PeerReviewDetail;
import com.saga.be.entity.RubricTemplate;
import com.saga.be.entity.Subject;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.repository.AssessmentRepository;
import com.saga.be.repository.RubricTemplateRepository;
import com.saga.be.repository.SubjectRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@Transactional
class AdminPeerReviewRubricIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private RubricTemplateRepository rubricTemplateRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private AssessmentRepository assessmentRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void adminCreatesTrimmedGlobalRubricWithoutRebalancingOtherRubrics() throws Exception {
        RubricTemplate existing = globalRubric("Existing", "70");

        performAdminPost(rubricJson("  Delivery quality  ", "30", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.criteriaName").value("Delivery quality"))
                .andExpect(jsonPath("$.weight").value(30));

        List<RubricTemplate> active = rubricTemplateRepository
                .findBySubjectIdIsNullAndDeletedAtIsNullOrderByCreatedAtAsc();
        RubricTemplate created = active.stream()
                .filter(item -> "Delivery quality".equals(item.getCriteriaName()))
                .findFirst()
                .orElseThrow();
        assertNull(created.getSubject());
        assertNull(created.getDeletedAt());
        assertEquals(new BigDecimal("70"), rubricTemplateRepository.findById(existing.getId()).orElseThrow().getWeight());
    }

    @Test
    void createValidatesSecurityCsrfBlankNameAndMaximumActiveGlobalCount() throws Exception {
        Authentication admin = authenticationFor(ApplicationRole.ADMIN);
        Cookie csrf = csrfCookie(admin);
        String valid = rubricJson("Criterion", "25", null);

        mockMvc.perform(post("/api/admin/peer-review-rubrics").cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isUnauthorized());
        performPostAs(ApplicationRole.LECTURER, valid).andExpect(status().isForbidden());
        performPostAs(ApplicationRole.STUDENT, valid).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/peer-review-rubrics").with(authentication(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isForbidden());
        performAdminPost(rubricJson("   ", "25", null)).andExpect(status().isBadRequest());
        performAdminPost("{\"criteriaName\":\"Missing weight\"}").andExpect(status().isBadRequest());

        globalRubric("One", "10");
        globalRubric("Two", "20");
        globalRubric("Three", "30");
        performAdminPost(rubricJson("Four", "40", null)).andExpect(status().isCreated());
        performAdminPost(rubricJson("Five", "50", null)).andExpect(status().isConflict());
    }

    @Test
    void adminUpdatesOnlyActiveGlobalRubricWithoutTotalWeightRule() throws Exception {
        RubricTemplate target = globalRubric("Before", "25");
        RubricTemplate other = globalRubric("Other", "75");

        performAdminPut(target.getId(), rubricJson("  After  ", "99", "Updated"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rubricId").value(target.getId().toString()))
                .andExpect(jsonPath("$.criteriaName").value("After"))
                .andExpect(jsonPath("$.weight").value(99));

        RubricTemplate persisted = rubricTemplateRepository.findById(target.getId()).orElseThrow();
        assertNull(persisted.getSubject());
        assertNull(persisted.getDeletedAt());
        assertEquals(new BigDecimal("75"), rubricTemplateRepository.findById(other.getId()).orElseThrow().getWeight());
        performAdminPut(other.getId(), rubricJson("   ", "75", null)).andExpect(status().isBadRequest());
        assertEquals("Other", rubricTemplateRepository.findById(other.getId()).orElseThrow().getCriteriaName());

        performAdminPut(UUID.randomUUID(), rubricJson("Unknown", "1", null)).andExpect(status().isNotFound());
        persisted.setDeletedAt(LocalDateTime.now());
        rubricTemplateRepository.saveAndFlush(persisted);
        performAdminPut(target.getId(), rubricJson("Nope", "1", null)).andExpect(status().isNotFound());

        RubricTemplate subjectSpecific = subjectRubric("Subject only", "15");
        performAdminPut(subjectSpecific.getId(), rubricJson("Forbidden", "15", null)).andExpect(status().isBadRequest());
        assertEquals("Subject only", rubricTemplateRepository.findById(subjectSpecific.getId()).orElseThrow().getCriteriaName());
    }

    @Test
    void deleteSoftDeletesAllowsReplacementAndDefaultReadExcludesTombstones() throws Exception {
        RubricTemplate deleted = globalRubric("Deleted", "25");
        RubricTemplate active = globalRubric("Active", "75");

        performAdminDelete(deleted.getId()).andExpect(status().isNoContent());
        assertNotNull(rubricTemplateRepository.findById(deleted.getId()).orElseThrow().getDeletedAt());
        assertEquals(1, rubricTemplateRepository.findBySubjectIdIsNullAndDeletedAtIsNullOrderByCreatedAtAsc().size());
        performAdminDelete(deleted.getId()).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/peer-review-rubrics/default")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criteria[*].rubricId", not(hasItem(deleted.getId().toString()))))
                .andExpect(jsonPath("$.criteria[*].rubricId", hasItem(active.getId().toString())));

        for (int index = 0; index < 2; index++) {
            globalRubric("Additional " + index, "1");
        }
        performAdminPost(rubricJson("Replacement", "1", null)).andExpect(status().isCreated());
    }

    @Test
    void deleteLastGlobalRubricKeepsZeroActiveDefaultAndHistoricalReferences() throws Exception {
        RubricTemplate rubric = globalRubric("Historical", "25");
        Assessment assessment = assessmentRepository.saveAndFlush(Assessment.builder()
                .rubricTemplate(rubric).score(4.0f).note("Retain reference").build());
        PeerReview peerReview = new PeerReview();
        entityManager.persist(peerReview);
        PeerReviewDetail detail = PeerReviewDetail.builder()
                .peerReview(peerReview)
                .rubricTemplate(rubric)
                .criteriaName("Historical")
                .criteriaOrder(0)
                .starRating(4)
                .build();
        entityManager.persist(detail);
        entityManager.flush();

        performAdminDelete(rubric.getId()).andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        Assessment persistedAssessment = assessmentRepository.findById(assessment.getId()).orElseThrow();
        PeerReviewDetail persistedDetail = entityManager.find(PeerReviewDetail.class, detail.getId());
        assertNotNull(persistedAssessment);
        assertNotNull(persistedDetail);
        assertEquals(rubric.getId(), persistedAssessment.getRubricTemplate().getId());
        assertEquals(rubric.getId(), PeerReviewCriterionResponse.from(persistedDetail).rubricId());

        mockMvc.perform(get("/api/v1/peer-review-rubrics/default")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criteria").isEmpty());
    }

    private RubricTemplate globalRubric(String criteriaName, String weight) {
        return rubricTemplateRepository.saveAndFlush(RubricTemplate.builder()
                .criteriaName(criteriaName).weight(new BigDecimal(weight)).build());
    }

    private RubricTemplate subjectRubric(String criteriaName, String weight) {
        Subject subject = new Subject();
        subject.setSubjectCode("SUB-" + UUID.randomUUID());
        subject.setName("Subject rubric scope");
        subject = subjectRepository.saveAndFlush(subject);
        return rubricTemplateRepository.saveAndFlush(RubricTemplate.builder()
                .subject(subject).criteriaName(criteriaName).weight(new BigDecimal(weight)).build());
    }

    private org.springframework.test.web.servlet.ResultActions performAdminPost(String body) throws Exception {
        Authentication admin = authenticationFor(ApplicationRole.ADMIN);
        Cookie csrf = csrfCookie(admin);
        return mockMvc.perform(post("/api/admin/peer-review-rubrics").with(authentication(admin)).cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.ResultActions performPostAs(ApplicationRole role, String body) throws Exception {
        Authentication user = authenticationFor(role);
        Cookie csrf = csrfCookie(user);
        return mockMvc.perform(post("/api/admin/peer-review-rubrics").with(authentication(user)).cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.ResultActions performAdminPut(UUID rubricId, String body) throws Exception {
        Authentication admin = authenticationFor(ApplicationRole.ADMIN);
        Cookie csrf = csrfCookie(admin);
        return mockMvc.perform(put("/api/admin/peer-review-rubrics/{rubricId}", rubricId).with(authentication(admin)).cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.ResultActions performAdminDelete(UUID rubricId) throws Exception {
        Authentication admin = authenticationFor(ApplicationRole.ADMIN);
        Cookie csrf = csrfCookie(admin);
        return mockMvc.perform(delete("/api/admin/peer-review-rubrics/{rubricId}", rubricId).with(authentication(admin)).cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()));
    }

    private Cookie csrfCookie(Authentication authentication) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf").with(authentication(authentication)))
                .andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        if (cookie == null) throw new AssertionError("Missing XSRF-TOKEN");
        return cookie;
    }

    private Authentication authenticationFor(ApplicationRole role) {
        SagaPrincipal principal = new SagaPrincipal(role.name().toLowerCase() + "-rubric",
                role.name().toLowerCase() + "@example.test", role.name() + " User", role,
                UUID.randomUUID(), AccountStatus.ACTIVE);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private String rubricJson(String criteriaName, String weight, String description) {
        String descriptionValue = description == null ? "null" : "\"" + description + "\"";
        return """
                {"criteriaName":"%s","weight":%s,"description":%s}
                """.formatted(criteriaName, weight, descriptionValue);
    }
}
