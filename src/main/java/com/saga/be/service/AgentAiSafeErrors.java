package com.saga.be.service;

public final class AgentAiSafeErrors {

    public static final String FORBIDDEN_MESSAGE = "Bạn không có quyền truy cập hoặc thực hiện thao tác này.";
    public static final String SESSION_EXPIRED_MESSAGE = "Phiên đăng nhập đã hết hạn.";
    public static final String ACCESS_DENIED_CODE = "ACCESS_DENIED";
    public static final String AUTHENTICATION_REQUIRED_CODE = "AUTHENTICATION_REQUIRED";

    private AgentAiSafeErrors() {
    }

    public static boolean isPublicAgentPath(String uri) {
        return uri != null && uri.startsWith("/api/v1/ai/");
    }

    public static boolean isInternalAgentPath(String uri) {
        return uri != null && uri.startsWith("/internal/ai/");
    }

    public static boolean isAgentPath(String uri) {
        return isPublicAgentPath(uri) || isInternalAgentPath(uri);
    }
}
