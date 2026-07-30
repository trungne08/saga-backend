package com.saga.be.tools;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class LocalIntegrationSchemaToolTest {

    @Test
    void parsesMySqlJdbcUrlWithoutConnecting() {
        var target = LocalIntegrationSchemaTool.parseDatabaseTarget(
                "jdbc:mysql://dev-aiven.example:27813/saga_dev?sslMode=REQUIRED");

        assertEquals("dev-aiven.example", target.host());
        assertEquals("saga_dev", target.database());
    }

    @Test
    void remoteAivenStyleHostRequiresExactApproval() {
        var target = LocalIntegrationSchemaTool.parseDatabaseTarget("jdbc:mysql://mysql-dev.aivencloud.com/saga");

        assertThrows(IllegalArgumentException.class,
                () -> LocalIntegrationSchemaTool.assertSafeTarget(target, Optional.empty()));
        assertDoesNotThrow(() -> LocalIntegrationSchemaTool.assertSafeTarget(
                target, Optional.of("mysql-dev.aivencloud.com")));
    }

    @Test
    void productionProfileIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> LocalIntegrationSchemaTool.assertLocalProfile("local,prod"));
    }

    @Test
    void railwayAndAwsRdsHostsAreRejectedEvenWhenApproved() {
        var railway = LocalIntegrationSchemaTool.parseDatabaseTarget("jdbc:mysql://mysql.railway.app/saga");
        var rds = LocalIntegrationSchemaTool.parseDatabaseTarget("jdbc:mysql://saga.cluster.us-east-1.rds.amazonaws.com/saga");

        assertThrows(IllegalArgumentException.class,
                () -> LocalIntegrationSchemaTool.assertSafeTarget(railway, Optional.of(railway.host())));
        assertThrows(IllegalArgumentException.class,
                () -> LocalIntegrationSchemaTool.assertSafeTarget(rds, Optional.of(rds.host())));
    }
}
