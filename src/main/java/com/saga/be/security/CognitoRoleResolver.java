package com.saga.be.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CognitoRoleResolver {

    public Optional<ApplicationRole> resolve(Object groupsClaim) {
        Set<String> groups = normalize(groupsClaim);

        if (groups.contains(ApplicationRole.ADMIN.name())) {
            return Optional.of(ApplicationRole.ADMIN);
        }
        if (groups.contains(ApplicationRole.LECTURER.name())) {
            return Optional.of(ApplicationRole.LECTURER);
        }
        if (groups.contains(ApplicationRole.STUDENT.name())) {
            return Optional.of(ApplicationRole.STUDENT);
        }
        return Optional.empty();
    }

    Set<String> normalize(Object groupsClaim) {
        Set<String> groups = new LinkedHashSet<>();
        if (groupsClaim instanceof Collection<?> values) {
            values.forEach(value -> addNormalized(groups, value));
        } else if (groupsClaim != null) {
            addNormalized(groups, groupsClaim);
        }
        return groups;
    }

    private void addNormalized(Set<String> groups, Object value) {
        String normalized = String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        if (!normalized.isEmpty()) {
            groups.add(normalized);
        }
    }
}
