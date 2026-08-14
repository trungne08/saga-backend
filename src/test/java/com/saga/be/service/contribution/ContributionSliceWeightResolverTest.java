package com.saga.be.service.contribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saga.be.entity.Course;
import com.saga.be.entity.Project;
import com.saga.be.entity.ProjectGroupWeightConfig;
import com.saga.be.entity.Team;
import com.saga.be.repository.ProjectGroupWeightConfigRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContributionSliceWeightResolverTest {

    @Test
    void fallsBackToCourseWeightsWhenNoConfigExistsForProject() {
        ProjectGroupWeightConfigRepository repository = mock(ProjectGroupWeightConfigRepository.class);
        ContributionSliceWeightResolver resolver = new ContributionSliceWeightResolver(repository);
        UUID projectId = UUID.randomUUID();
        Course course = new Course();
        course.setCodeContributionWeight(60.0);
        course.setDocumentContributionWeight(20.0);
        course.setDesignContributionWeight(20.0);
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setCourse(course);
        when(repository.findByProjectId(projectId)).thenReturn(Optional.empty());

        ContributionSliceWeights weights = resolver.resolve(projectId, team);

        assertThat(weights.code()).isEqualByComparingTo("60");
        assertThat(weights.document()).isEqualByComparingTo("20");
        assertThat(weights.design()).isEqualByComparingTo("20");
    }

    @Test
    void usesExactProjectTeamOverrideWhenPresent() {
        ProjectGroupWeightConfigRepository repository = mock(ProjectGroupWeightConfigRepository.class);
        ContributionSliceWeightResolver resolver = new ContributionSliceWeightResolver(repository);
        UUID projectId = UUID.randomUUID();
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setCourse(new Course());
        Project project = new Project();
        project.setId(projectId);
        when(repository.findByProjectId(projectId)).thenReturn(Optional.of(ProjectGroupWeightConfig.builder()
                .project(project)
                .team(team)
                .codeWeight(new BigDecimal("0.4"))
                .documentWeight(new BigDecimal("0.3"))
                .designWeight(new BigDecimal("0.3"))
                .build()));

        ContributionSliceWeights weights = resolver.resolve(projectId, team);

        assertThat(weights.code()).isEqualByComparingTo("40");
        assertThat(weights.document()).isEqualByComparingTo("30");
        assertThat(weights.design()).isEqualByComparingTo("30");
    }

    @Test
    void ignoresConfigThatBelongsToADifferentTeamAndFallsBackToCourseWeights() {
        ProjectGroupWeightConfigRepository repository = mock(ProjectGroupWeightConfigRepository.class);
        ContributionSliceWeightResolver resolver = new ContributionSliceWeightResolver(repository);
        UUID projectId = UUID.randomUUID();
        Course course = new Course();
        course.setCodeContributionWeight(60.0);
        course.setDocumentContributionWeight(20.0);
        course.setDesignContributionWeight(20.0);
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setCourse(course);
        Team otherTeam = new Team();
        otherTeam.setId(UUID.randomUUID());
        when(repository.findByProjectId(projectId)).thenReturn(Optional.of(ProjectGroupWeightConfig.builder()
                .team(otherTeam)
                .codeWeight(new BigDecimal("0.4"))
                .documentWeight(new BigDecimal("0.3"))
                .designWeight(new BigDecimal("0.3"))
                .build()));

        ContributionSliceWeights weights = resolver.resolve(projectId, team);

        assertThat(weights.code()).isEqualByComparingTo("60");
        assertThat(weights.document()).isEqualByComparingTo("20");
        assertThat(weights.design()).isEqualByComparingTo("20");
    }

    @Test
    void repeatedResolveReflectsLatestPersistedConfigValue() {
        ProjectGroupWeightConfigRepository repository = mock(ProjectGroupWeightConfigRepository.class);
        ContributionSliceWeightResolver resolver = new ContributionSliceWeightResolver(repository);
        UUID projectId = UUID.randomUUID();
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setCourse(new Course());
        ProjectGroupWeightConfig first = ProjectGroupWeightConfig.builder()
                .team(team)
                .codeWeight(new BigDecimal("0.5"))
                .documentWeight(new BigDecimal("0.3"))
                .designWeight(new BigDecimal("0.2"))
                .build();
        ProjectGroupWeightConfig second = ProjectGroupWeightConfig.builder()
                .team(team)
                .codeWeight(new BigDecimal("0.2"))
                .documentWeight(new BigDecimal("0.3"))
                .designWeight(new BigDecimal("0.5"))
                .build();
        when(repository.findByProjectId(projectId)).thenReturn(Optional.of(first), Optional.of(second));

        assertThat(resolver.resolve(projectId, team).code()).isEqualByComparingTo("50");
        assertThat(resolver.resolve(projectId, team).code()).isEqualByComparingTo("20");
    }
}
