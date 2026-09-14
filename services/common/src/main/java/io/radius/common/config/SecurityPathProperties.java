package io.radius.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** Paths reachable without a token. Everything not listed needs one. */
@ConfigurationProperties(prefix = "radius.security")
public class SecurityPathProperties {

    private List<String> publicPaths = new ArrayList<>(List.of(
            "/actuator/health/**", "/actuator/prometheus", "/actuator/info",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"));

    /**
     * Reachable without a token on GET only. This is what lets a guest browse
     * a listing at {@code /api/listings/{id}} while POST and PATCH on the same
     * path still demand one — a single all-methods entry there would quietly
     * open up editing.
     */
    private List<String> publicGetPaths = new ArrayList<>();

    public List<String> getPublicPaths() { return publicPaths; }
    public void setPublicPaths(List<String> publicPaths) { this.publicPaths = publicPaths; }

    public List<String> getPublicGetPaths() { return publicGetPaths; }
    public void setPublicGetPaths(List<String> publicGetPaths) { this.publicGetPaths = publicGetPaths; }
}
