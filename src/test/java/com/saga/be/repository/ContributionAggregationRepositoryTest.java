package com.saga.be.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.saga.be.entity.CommitData;
import com.saga.be.entity.Document;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Project;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Student;
import com.saga.be.entity.Task;
import com.saga.be.entity.enums.DocumentType;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.TaskStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class ContributionAggregationRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CommitDataRepository commitDataRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void aggregatesOnlyTheSelectedProjectStudentAndDoneTasksWithNullStoryPointAsOne() {
        Student student = persist(Student.builder().studentCode("S001").email("s001@test.local").build());
        Student otherStudent = persist(Student.builder().studentCode("S002").email("s002@test.local").build());
        Project project = persist(Project.builder().name("Project A").build());
        Project otherProject = persist(Project.builder().name("Project B").build());
        GitRepo repo = persist(GitRepo.builder()
                .project(project).provider("github").repositoryId(1L).fullName("org/a")
                .connectionStatus(IntegrationStatus.ACTIVE).build());
        GitRepo otherRepo = persist(GitRepo.builder()
                .project(otherProject).provider("github").repositoryId(2L).fullName("org/b")
                .connectionStatus(IntegrationStatus.ACTIVE).build());
        persist(CommitData.builder().repo(repo).author(student).shaHash("sha-1").build());
        persist(CommitData.builder().repo(repo).author(student).shaHash("sha-2").build());
        persist(CommitData.builder().repo(repo).author(otherStudent).shaHash("sha-3").build());
        persist(CommitData.builder().repo(otherRepo).author(student).shaHash("sha-4").build());
        persist(Document.builder().project(project).author(student).type(DocumentType.REPORT).build());
        persist(Document.builder().project(project).author(student).type(DocumentType.DESIGN).build());
        persist(Document.builder().project(otherProject).author(student).type(DocumentType.REPORT).build());

        JiraBoard board = persist(JiraBoard.builder()
                .project(project).connectionStatus(IntegrationStatus.ACTIVE).build());
        Sprint sprint = persist(Sprint.builder().board(board).externalSprintId("sprint-a").build());
        persist(Task.builder().project(project).sprint(sprint).assignee(student)
                .status(TaskStatus.DONE).storyPoint(3).build());
        persist(Task.builder().project(project).sprint(sprint).assignee(student)
                .status(TaskStatus.DONE).storyPoint(null).build());
        persist(Task.builder().project(project).sprint(sprint).assignee(student)
                .status(TaskStatus.IN_PROGRESS).storyPoint(99).build());
        entityManager.flush();
        entityManager.clear();

        assertEquals(2L, commitDataRepository.countByProjectIdAndAuthorId(project.getId(), student.getId()));
        assertEquals(1L, documentRepository.countByProjectIdAndAuthorIdAndTypeNot(
                project.getId(), student.getId(), DocumentType.DESIGN));
        assertEquals(1L, documentRepository.countByProjectIdAndAuthorIdAndType(
                project.getId(), student.getId(), DocumentType.DESIGN));
        assertEquals(4L, taskRepository.sumDoneEffectiveStoryPoints(
                project.getId(), sprint.getId(), student.getId()));
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }
}
