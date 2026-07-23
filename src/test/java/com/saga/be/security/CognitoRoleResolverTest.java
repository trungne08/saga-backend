package com.saga.be.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CognitoRoleResolverTest {

    private final CognitoRoleResolver roleResolver = new CognitoRoleResolver();

    @Test
    void adminTakesPrecedenceOverLecturerAndStudent() {
        Optional<ApplicationRole> result = roleResolver.resolve(
                List.of("STUDENT", "lecturer", "ADMIN")
        );

        assertEquals(Optional.of(ApplicationRole.ADMIN), result);
    }

    @Test
    void lecturerTakesPrecedenceOverStudent() {
        Optional<ApplicationRole> result = roleResolver.resolve(
                List.of("student", " LECTURER ")
        );

        assertEquals(Optional.of(ApplicationRole.LECTURER), result);
    }

    @Test
    void resolvesStudentWhenItIsTheOnlyApplicationRole() {
        assertEquals(
                Optional.of(ApplicationRole.STUDENT),
                roleResolver.resolve(List.of("unrelated-group", "student"))
        );
    }

    @Test
    void acceptsASingleCaseInsensitiveGroupClaim() {
        assertEquals(
                Optional.of(ApplicationRole.LECTURER),
                roleResolver.resolve("  lecturer  ")
        );
    }

    @Test
    void returnsEmptyWhenNoTrustedApplicationGroupExists() {
        assertEquals(Optional.empty(), roleResolver.resolve(List.of("users", "reviewers")));
        assertEquals(Optional.empty(), roleResolver.resolve(null));
    }
}
