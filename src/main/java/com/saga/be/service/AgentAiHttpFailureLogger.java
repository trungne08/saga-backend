package com.saga.be.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

final class AgentAiHttpFailureLogger {

    private static final Logger log = LoggerFactory.getLogger(AgentAiHttpFailureLogger.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SAFE_CODE = "^[A-Z0-9_]{1,128}$";

    private AgentAiHttpFailureLogger() {
    }

    static void logResponse(
            String operation,
            String path,
            RestClientResponseException exception,
            String mappedCode
    ) {
        log.warn(
                "AI agent downstream failed operation={} path={} kind=HTTP_STATUS "
                        + "downstreamStatus={} downstreamSafeCode={} mappedCode={} "
                        + "exceptionClass={} rootCauseClass={}",
                operation,
                path,
                exception.getStatusCode().value(),
                safeDetailCode(exception),
                mappedCode,
                exception.getClass().getSimpleName(),
                rootCauseClass(exception)
        );
    }

    static void logTransport(
            String operation,
            String path,
            RestClientException exception,
            String mappedCode
    ) {
        log.warn(
                "AI agent downstream failed operation={} path={} kind={} "
                        + "downstreamStatus=NONE downstreamSafeCode=NONE mappedCode={} "
                        + "exceptionClass={} rootCauseClass={}",
                operation,
                path,
                transportKind(exception),
                mappedCode,
                exception.getClass().getSimpleName(),
                rootCauseClass(exception)
        );
    }

    static void logInvalidResponse(String operation, String path, String mappedCode) {
        log.warn(
                "AI agent downstream failed operation={} path={} kind=INVALID_RESPONSE "
                        + "downstreamStatus=NONE downstreamSafeCode=NONE mappedCode={} "
                        + "exceptionClass=NONE rootCauseClass=NONE",
                operation,
                path,
                mappedCode
        );
    }

    static String safeDetailCode(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "NONE";
        }
        try {
            JsonNode root = MAPPER.readTree(body);
            String detail = textualSafeCode(root.get("detail"));
            if (detail != null) {
                return detail;
            }
            String error = textualSafeCode(root.get("error"));
            if (error != null) {
                return error;
            }
            return "UNSAFE_OMITTED";
        } catch (Exception ignored) {
            return "UNPARSEABLE";
        }
    }

    private static String textualSafeCode(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.asText();
        return value != null && value.matches(SAFE_CODE) ? value : null;
    }

    private static String transportKind(RestClientException exception) {
        return exception instanceof ResourceAccessException ? "CONNECT_OR_IO" : "CLIENT";
    }

    private static String rootCauseClass(Throwable exception) {
        Throwable cause = exception.getCause();
        return cause == null ? "NONE" : cause.getClass().getSimpleName();
    }
}
