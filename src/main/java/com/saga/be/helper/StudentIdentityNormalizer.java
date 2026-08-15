package com.saga.be.helper;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class StudentIdentityNormalizer {

    public String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public String normalizeStudentCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public String normalizeFullName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
