package com.saga.be.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.saga.be.entity.Task;
import com.saga.be.entity.value.TaskComponentSnapshot;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class TaskLabelsPersistenceTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void roundTripPreservesRawJiraSnapshotsAndReadsLegacyNullCollectionsAsEmpty() {
        Task task = new Task();
        task.setLabels(List.of("Backend", "Needs review"));
        task.setComponents(List.of(new TaskComponentSnapshot("10", "Backend")));
        task.setDescription("Canonical Jira description");
        Task saved = taskRepository.saveAndFlush(task);

        entityManager.clear();
        Task reloaded = taskRepository.findById(saved.getId()).orElseThrow();
        assertEquals(List.of("Backend", "Needs review"), reloaded.getLabels());
        assertEquals(List.of(new TaskComponentSnapshot("10", "Backend")), reloaded.getComponents());
        assertEquals("Canonical Jira description", reloaded.getDescription());

        entityManager.createNativeQuery(
                        "update task set labels_json = null, components_json = null where id = :id"
                )
                .setParameter("id", saved.getId())
                .executeUpdate();
        entityManager.clear();

        assertEquals(List.of(), taskRepository.findById(saved.getId())
                .orElseThrow()
                .getLabels());
        assertEquals(List.of(), taskRepository.findById(saved.getId())
                .orElseThrow()
                .getComponents());
    }
}
