package io.radius.gateway;

import io.radius.common.security.AuthUser;
import io.radius.common.security.JwtService;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Rejects unauthenticated traffic at the edge and stamps the caller onto the
 * request so downstream logs and traces carry it. Services still verify the
 * token themselves — this is the fast path, not the only check.
 */
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    /** Prefixes that never need a token. Keep this list short and boring. */
    private static final List<String> PUBLIC = List.of(
            "/api/auth/", "/actuator/health", "/actuator/prometheus", "/v3/api-docs", "/swagger-ui",
            // Browsing is open: a visitor sees real listings before being asked
            // for anything. Search is a POST but reads nothing but the index.
            "/api/search", "/api/tags", "/api/users/", "/api/professionals/");

    /**
     * Open on GET only, so a guest can read a listing while POST and PATCH on
     * the same path still need a token. The services check this again — this is
     * the fast path at the edge, not the only gate.
     */
    private static final List<String> PUBLIC_GET = List.of(
            // /api/listings covers the comment thread and the ratings on a
            // listing too — both hang off that prefix.
            "/api/feed", "/api/listings", "/api/reports/reasons");

    static final String HEADER_USER_ID = "X-Radius-User-Id";
    static final String HEADER_USER_NAME = "X-Radius-User-Name";

    private final JwtService jwt;

    public AuthenticationFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Never let a client forge identity headers.
        ServerHttpRequest.Builder mutated = request.mutate()
                .headers(h -> { h.remove(HEADER_USER_ID); h.remove(HEADER_USER_NAME); });

        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        AuthUser user = header != null && header.startsWith("Bearer ")
                ? jwt.verifyAccess(header.substring(7))
                : null;

        if (user != null) {
            mutated.header(HEADER_USER_ID, user.id().toString());
            mutated.header(HEADER_USER_NAME, user.displayName() == null ? "" : user.displayName());
        } else if (!isPublic(path, request.getMethod())) {
            return unauthorized(exchange);
        }

        return chain.filter(exchange.mutate().request(mutated.build()).build());
    }

    private boolean isPublic(String path, HttpMethod method) {
        if (PUBLIC.stream().anyMatch(path::startsWith)) return true;
        return HttpMethod.GET.equals(method) && PUBLIC_GET.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = """
                {"code":"unauthenticated","message":"Sign in to continue"}"""
                .getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return -100;   // before routing, after the rate limiter's own ordering
    }
}
