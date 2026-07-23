package com.saga.be.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoleHelperTest {

    private RoleHelper roleHelper;

    @BeforeEach
    void setUp() {
        roleHelper = new RoleHelper();
    }

    @Test
    void returnsLecturerForFeDomain() {
        assertEquals("LECTURER", roleHelper.determineRole("teacher@fe.edu.vn"));
        assertEquals("LECTURER", roleHelper.determineRole("hieuthse150392@fe.edu.vn"));
    }

    @Test
    void returnsStudentForFptUsernameEndingWithSupportedMajorAndFiveOrSixDigits() {
        assertEquals("STUDENT", roleHelper.determineRole("hieuthse150392@fpt.edu.vn"));
        assertEquals("STUDENT", roleHelper.determineRole("studentss12345@fpt.edu.vn"));
        assertEquals("STUDENT", roleHelper.determineRole("studiosa123456@fpt.edu.vn"));
        assertEquals("STUDENT", roleHelper.determineRole("studentia12345@fpt.edu.vn"));
        assertEquals("STUDENT", roleHelper.determineRole("studentai123456@fpt.edu.vn"));
        assertEquals("STUDENT", roleHelper.determineRole("studentit12345@fpt.edu.vn"));
        assertEquals("STUDENT", roleHelper.determineRole("studentgd123456@fpt.edu.vn"));
    }

    @Test
    void returnsLecturerForFptUsernameNotMatchingStudentPattern() {
        assertEquals("LECTURER", roleHelper.determineRole("dungnt2@fpt.edu.vn"));
        assertEquals("LECTURER", roleHelper.determineRole("studentxx123456@fpt.edu.vn"));
        assertEquals("LECTURER", roleHelper.determineRole("studentse1234@fpt.edu.vn"));
        assertEquals("LECTURER", roleHelper.determineRole("studentse1234567@fpt.edu.vn"));
    }

    @Test
    void normalizesCaseAndWhitespace() {
        assertEquals("STUDENT", roleHelper.determineRole("  HIEUTHSE150392@FPT.EDU.VN  "));
        assertEquals("LECTURER", roleHelper.determineRole("  TEACHER@FE.EDU.VN  "));
    }

    @Test
    void defaultsOtherDomainsAndMissingEmailToStudent() {
        assertEquals("STUDENT", roleHelper.determineRole("user@gmail.com"));
        assertEquals("STUDENT", roleHelper.determineRole("user@yahoo.com"));
        assertEquals("STUDENT", roleHelper.determineRole(""));
        assertEquals("STUDENT", roleHelper.determineRole(null));
    }
}
