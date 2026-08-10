package com.saga.be.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.saga.be.entity.Task;
import com.saga.be.entity.enums.TaskStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class TaskDeadlineEligibilityRepositoryTest {
    @Autowired
    private TaskRepository tasks;

    @Test
    void queryExcludesNullDueTerminalAndDeletedTasks() {
        Task eligibleTodo = save(TaskStatus.TODO, LocalDateTime.of(2026, 8, 11, 15, 37), null);
        Task eligibleReview = save(TaskStatus.IN_REVIEW, LocalDateTime.of(2026, 8, 12, 0, 0), null);
        save(TaskStatus.DONE, LocalDateTime.of(2026, 8, 10, 0, 0), null);
        save(TaskStatus.CANCELLED, LocalDateTime.of(2026, 8, 10, 0, 0), null);
        save(TaskStatus.IN_PROGRESS, null, null);
        save(TaskStatus.IN_PROGRESS, LocalDateTime.of(2026, 8, 10, 0, 0), LocalDateTime.now());

        var result = tasks.findDeadlineEligibleTasksAfter(
                List.of(TaskStatus.DONE, TaskStatus.CANCELLED),
                null,
                PageRequest.of(0, 100)
        );

        assertThat(result)
                .extracting(Task::getId)
                .containsExactlyInAnyOrder(eligibleTodo.getId(), eligibleReview.getId());
    }

    private Task save(TaskStatus status, LocalDateTime dueDate, LocalDateTime deletedAt) {
        return tasks.saveAndFlush(Task.builder()
                .title("Deadline fixture")
                .status(status)
                .dueDate(dueDate)
                .deletedAt(deletedAt)
                .build());
    }
}
