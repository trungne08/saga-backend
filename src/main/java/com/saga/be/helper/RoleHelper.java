package com.saga.be.helper;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class RoleHelper {

    private static final String FE_DOMAIN = "@fe.edu.vn";
    private static final String FPT_DOMAIN = "@fpt.edu.vn";

    private static final Pattern STUDENT_USERNAME_PATTERN =
            Pattern.compile(".*(se|ss|sa|ia|ai|it|gd)\\d{5,6}$");

    public String determineRole(String email) {
        if (email == null) {
            return "STUDENT";
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (normalizedEmail.endsWith(FE_DOMAIN)) {
            return "LECTURER";
        }

        if (normalizedEmail.endsWith(FPT_DOMAIN)) {
            String username = normalizedEmail.substring(
                    0,
                    normalizedEmail.length() - FPT_DOMAIN.length()
            );

            return STUDENT_USERNAME_PATTERN.matcher(username).matches()
                    ? "STUDENT"
                    : "LECTURER";
        }

        return "STUDENT";
    }
}
