package com.saga.be.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.saga.be.entity.Task;
import com.saga.be.entity.enums.TaskType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class TaskTypePersistenceTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private EntityManager entityManager;

    @ParameterizedTest
    @EnumSource(TaskType.class)
    void everyTaskTypeRoundTripsThroughPersistentTaskSchema(TaskType type) {
        Task task = new Task();
        task.setType(type);
        Task saved = taskRepository.saveAndFlush(task);

        entityManager.clear();

        assertEquals(type, taskRepository.findById(saved.getId())
                .orElseThrow()
                .getType());
    }

    @Test
    void nullableTaskTypeRemainsSupported() {
        Task saved = taskRepository.saveAndFlush(new Task());

        entityManager.clear();

        assertNull(taskRepository.findById(saved.getId())
                .orElseThrow()
                .getType());
    }
}
