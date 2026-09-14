package io.radius.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.time.Instant;

/**
 * Without this, Spring Security answers an unauthenticated API call with 403.
 * For a bearer-token API that is the wrong answer: 403 tells the client "you
 * are signed in and still not allowed", so it will not try to refresh the
 * token. 401 with a WWW-Authenticate header is what makes the refresh flow in
 * both clients work.
 *
 * The body matches {@code GlobalExceptionHandler}'s shape, so clients parse one
 * error format no matter which layer rejected them.
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"radius\"");
        write(response, request, HttpStatus.UNAUTHORIZED, "unauthenticated", "Sign in to continue");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, request, HttpStatus.FORBIDDEN, "forbidden", "Not yours to touch");
    }

    private static void write(HttpServletResponse response, HttpServletRequest request,
                              HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"code":"%s","message":"%s","fields":{},"path":"%s","at":"%s"}"""
                .formatted(code, message, request.getRequestURI(), Instant.now()));
    }
}
