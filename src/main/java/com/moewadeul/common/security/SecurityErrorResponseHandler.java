package com.moewadeul.common.security;

import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.ErrorCode;
import com.moewadeul.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

final class SecurityErrorResponseHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        write(response, CommonErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        if (EmailVerifiedAuthorizationManager.isEmailVerificationRequired(accessDeniedException)) {
            write(response, CommonErrorCode.MEMBER_NOT_VERIFIED);
            return;
        }
        write(response, CommonErrorCode.ACCESS_DENIED);
    }

    private static void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getOutputStream().write(JSON_MAPPER.writeValueAsBytes(ErrorResponse.of(errorCode)));
    }
}
