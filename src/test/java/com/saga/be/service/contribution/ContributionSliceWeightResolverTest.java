package com.saga.be.service.contribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saga.be.entity.Course;
import com.saga.be.entity.Project;
import com.saga.be.entity.ProjectGroupWeightConfig;
import com.saga.be.entity.Team;
import com.saga.be.entity.enums.ContributionConfigMode;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.ProjectGroupWeightConfigRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContributionSliceWeightResolverTest {

    @Test
    void courseModeAlwaysUsesTheTeamsOwningCourseWeights() {
        ProjectGroupWeightConfigRepository repository = mock(ProjectGroupWeightConfigRepository.class);
        ContributionSliceWeightResolver resolver = new ContributionSliceWeightResolver(repository);
        Course course = new Course();
        course.setContributionConfigMode(ContributionConfigMode.COURSE);
        course.setCodeContributionWeight(40.0);
        course.setTestContributionWeight(20.0);
        course.setDocumentContributionWeight(20.0);
        course.setResearchContributionWeight(20.0);
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setCourse(course);

        ContributionSliceWeights weights = resolver.resolve(team);

        assertThat(weights.code()).isEqualByComparingTo("40");
        assertThat(weights.test()).isEqualByComparingTo("20");
        assertThat(weights.document()).isEqualByComparingTo("20");
        assertThat(weights.research()).isEqualByComparingTo("20");
    }

    @Test
    void everyTeamInTheSameCourseCourseModeResolvesTheIdenticalWeights() {
        ProjectGroupWeightConfigRepository repository = mock(ProjectGroupWeightConfigRepository.class);
        ContributionSliceWeightResolver resolver = new ContributionSliceWeightResolver(repository);
        Course course = new Course();
        course.setContributionConfigMode(ContributionConfigMode.COURSE);
        course.setCodeContributionWeight(10.0);
        course.setTestContributionWeight(40.0);
        course.setDocumentContributionWeight(30.0);
        course.setResearchContributionWeight(20.0);
        Team first = new Team();
        first.setId(UUID.randomUUID());
        first.setCourse(course);
        Team second = new Team();
        second.setId(UUID.randomUUID());
        second.setCourse(course);

        assertThat(resolver.resolve(first)).isEqualTo(resolver.resolve(second));
    }

    @Test
    void teamModeUsesTheExactProjectTeamOverride() {
        ProjectGroupWeightConfigRepository repository = mock(ProjectGroupWeightConfigRepository.class);
        ContributionSliceWeightResolver resolver = new ContributionSliceWeightResolver(repository);
        UUID projectId = UUID.randomUUID();
        Course course = new Course();
        course.setContributionConfigMode(ContributionConfigMode.TEAM);
        Project project = new Project();
        project.setId(projectId);
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setCourse(course);
        team.setProject(project);
        when(repository.findByProjectId(projectId)).thenReturn(Optional.of(ProjectGroupWeightConfig.builder()
                .project(project)
                .team(team)
                .codeWeight(new BigDecimal("0.4"))
                .testWeight(new BigDecimal("0.1"))
                .documentWeight(new BigDecimal("0.3"))
                .researchWeight(new BigDecimal("0.2"))
                .build()));

        ContributionSliceWeights weights = resolver.resolve(team);

        assertThat(weights.code()).isEqualByComparingTo("40");
        assertThat(weights.test()).isEqualByComparingTo("10");
        assertThat(weights.document()).isEqualByComparingTo("30");
        assertThat(weights.research()).isEqualByComparingTo("20");
    }

    @Test
    void teamModeFailsClosedInsteadOfFallingBackToCourseWeightsWhenOverrideMissing() {
        ProjectGroupWeightConfigRepository repository = mock(ProjectGroupWeightConfigRepository.class);
        ContributionSliceWeightResolver resolver = new ContributionSliceWeightResolver(repository);
        UUID projectId = UUID.randomUUID();
        Course course = new Course();
        course.setContributionConfigMode(ContributionConfigMode.TEAM);
        course.setCodeContributionWeight(70.0);
        course.setDocumentContributionWeight(30.0);
        Project project = new Project();
        project.setId(projectId);
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setCourse(course);
        team.setProject(project);
        when(repository.findByProjectId(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(team))
                .isInstanceOf(IntegrationException.class)
                .satisfies(exception -> assertThat(((IntegrationException) exception).getCode())
                        .isEqualTo("TEAM_WEIGHT_CONFIG_INCOMPLETE"));
    }

    @Test
    void teamModeDoesNotLeakAnotherTeamsOverride() {
        ProjectGroupWeightConfigRepository repository = mock(ProjectGroupWeightConfigRepository.class);
        ContributionSliceWeightResolver resolver = new ContributionSliceWeightResolver(repository);
        UUID projectId = UUID.randomUUID();
        Course course = new Course();
        course.setContributionConfigMode(ContributionConfigMode.TEAM);
        Project project = new Project();
        project.setId(projectId);
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setCourse(course);
        team.setProject(project);
        Team otherTeam = new Team();
        otherTeam.setId(UUID.randomUUID());
        when(repository.findByProjectId(projectId)).thenReturn(Optional.of(ProjectGroupWeightConfig.builder()
                .project(project)
                .team(otherTeam)
                .codeWeight(new BigDecimal("0.4"))
                .testWeight(new BigDecimal("0.1"))
                .documentWeight(new BigDecimal("0.3"))
                .researchWeight(new BigDecimal("0.2"))
                .build()));

        assertThatThrownBy(() -> resolver.resolve(team))
                .isInstanceOf(IntegrationException.class);
    }
}
