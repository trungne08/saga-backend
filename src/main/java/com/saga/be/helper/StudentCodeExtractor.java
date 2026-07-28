package com.saga.be.helper;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class StudentCodeExtractor {

    private static final Pattern STUDENT_CODE_PATTERN = Pattern.compile(
            "([A-Za-z]{2}\\d{6})$",
            Pattern.CASE_INSENSITIVE
    );

    public Optional<String> extract(String email) {
        if (email == null || email.isBlank() || !email.equals(email.trim())) {
            return Optional.empty();
        }

        int atIndex = email.indexOf('@');
        if (atIndex <= 0
                || atIndex != email.lastIndexOf('@')
                || atIndex == email.length() - 1) {
            return Optional.empty();
        }

        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex + 1);
        if (localPart.isBlank()
                || domainPart.isBlank()
                || localPart.chars().anyMatch(Character::isWhitespace)
                || domainPart.chars().anyMatch(Character::isWhitespace)) {
            return Optional.empty();
        }

        Matcher matcher = STUDENT_CODE_PATTERN.matcher(localPart);
        if (!matcher.find()) {
            return Optional.empty();
        }

        return Optional.of(matcher.group(1).toUpperCase(Locale.ROOT));
    }
}
