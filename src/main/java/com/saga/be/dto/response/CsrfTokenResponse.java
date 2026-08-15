package com.saga.be.dto.response;

public record CsrfTokenResponse(
        String token,
        String headerName,
        String parameterName
) {

    public static CsrfTokenResponse from(
            org.springframework.security.web.csrf.CsrfToken csrfToken
    ) {
        return new CsrfTokenResponse(
                csrfToken.getToken(),
                csrfToken.getHeaderName(),
                csrfToken.getParameterName()
        );
    }

    @Override
    public String toString() {
        return "CsrfTokenResponse[token=<redacted>, headerName="
                + headerName
                + ", parameterName="
                + parameterName
                + "]";
    }
}
