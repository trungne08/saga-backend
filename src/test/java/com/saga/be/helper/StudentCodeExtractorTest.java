package com.saga.be.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class StudentCodeExtractorTest {

    private final StudentCodeExtractor extractor = new StudentCodeExtractor();

    @Test
    void extractsAndNormalizesMultipleStudentCodePrefixes() {
        assertEquals(
                Optional.of("SE170506"),
                extractor.extract("trungtdse170506@fpt.edu.vn")
        );
        assertEquals(
                Optional.of("HE123456"),
                extractor.extract("studenthe123456@fpt.edu.vn")
        );
        assertEquals(
                Optional.of("IA180001"),
                extractor.extract("userIA180001@fpt.edu.vn")
        );
        assertEquals(
                Optional.of("SE123456"),
                extractor.extract("userSE123456@fpt.edu.vn")
        );
    }

    @Test
    void rejectsMalformedStudentCodesAndCodesNotAtTheEnd() {
        assertTrue(extractor.extract("student-S123456@fpt.edu.vn").isEmpty());
        assertTrue(extractor.extract("student-123456@fpt.edu.vn").isEmpty());
        assertTrue(extractor.extract("abcse12345@fpt.edu.vn").isEmpty());
        assertTrue(extractor.extract("abcse1234567@fpt.edu.vn").isEmpty());
        assertTrue(extractor.extract("abcse123456xyz@fpt.edu.vn").isEmpty());
        assertTrue(extractor.extract("abcse12345x@fpt.edu.vn").isEmpty());
    }

    @Test
    void rejectsNullBlankAndMalformedEmails() {
        assertTrue(extractor.extract(null).isEmpty());
        assertTrue(extractor.extract("").isEmpty());
        assertTrue(extractor.extract("   ").isEmpty());
        assertTrue(extractor.extract("trungtdse170506").isEmpty());
        assertTrue(extractor.extract("@fpt.edu.vn").isEmpty());
        assertTrue(extractor.extract("trungtdse170506@").isEmpty());
        assertTrue(extractor.extract("trungtdse170506@@fpt.edu.vn").isEmpty());
        assertTrue(extractor.extract(" trungtdse170506@fpt.edu.vn").isEmpty());
        assertTrue(extractor.extract("trungtdse170506@fpt.edu.vn ").isEmpty());
    }
}
