package com.saga.be.dto.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.entity.enums.SyncJobType;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SyncStatusResponseTest {

    @Test
    void mapsUtcLocalDateTimesToInstantsAndSerializesAnExplicitUtcOffset()
            throws Exception {
        SyncJobLog log = SyncJobLog.builder()
                .targetSystem("JIRA")
                .targetId(UUID.randomUUID())
                .jobType(SyncJobType.INITIAL_BACKFILL)
                .status(SyncJobStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.of(2026, 8, 4, 5, 13, 49))
                .build();
        log.setId(UUID.randomUUID());

        SyncStatusResponse.Job job = SyncStatusResponse.Job.from(log);
        String json = JsonMapper.builder()
                .build()
                .writeValueAsString(new SyncStatusResponse(
                        UUID.randomUUID(),
                        List.of(job)
                ));

        assertEquals(Instant.parse("2026-08-04T05:13:49Z"), job.startedAt());
        assertNull(job.completedAt());
        org.assertj.core.api.Assertions.assertThat(json)
                .contains("\"startedAt\":\"2026-08-04T05:13:49Z\"")
                .contains("\"completedAt\":null");
    }
}
