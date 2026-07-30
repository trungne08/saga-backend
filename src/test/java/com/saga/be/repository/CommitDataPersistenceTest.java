package com.saga.be.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.saga.be.entity.CommitData;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class CommitDataPersistenceTest {

    @Autowired
    private CommitDataRepository commitDataRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAndReadsLongCommitMessageWithoutTruncation() {
        String message = "a".repeat(10_000);
        CommitData saved = commitDataRepository.saveAndFlush(
                CommitData.builder()
                        .message(message)
                        .build()
        );

        entityManager.clear();

        CommitData reloaded = commitDataRepository.findById(saved.getId())
                .orElseThrow();

        assertEquals(message, reloaded.getMessage());
    }
}
