package com.saga.be.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AiAgentDelegationMigrationContractTest {

    @Test
    void migrationStoresOnlyHashedOpaqueContextAndNoBusinessForeignKey() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V30__add_ai_agent_delegation_context.sql"
        ));
        assertTrue(sql.contains("token_hash CHAR(64)"));
        assertTrue(sql.contains("conversation_id CHAR(36)"));
        assertTrue(sql.contains("expires_at DATETIME(6)"));
        assertTrue(sql.contains("uk_ai_agent_delegation_token_hash"));
        assertTrue(!sql.toLowerCase().contains("service_token"));
        assertTrue(!sql.toLowerCase().contains("api_key"));
    }
}
