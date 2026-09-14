package io.radius.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Symmetric signing is fine for a single-team POC. Move to RS256 with a JWKS
 * endpoint on user-service before anything outside this repo verifies a token.
 */
@ConfigurationProperties(prefix = "radius.jwt")
public class JwtProperties {

    /** HMAC secret, at least 32 bytes. Injected from the environment, never committed. */
    private String secret = "dev-only-secret-change-me-at-least-32-bytes-long";
    private String issuer = "radius";
    private Duration accessTtl = Duration.ofMinutes(15);
    private Duration refreshTtl = Duration.ofDays(30);

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public Duration getAccessTtl() { return accessTtl; }
    public void setAccessTtl(Duration accessTtl) { this.accessTtl = accessTtl; }

    public Duration getRefreshTtl() { return refreshTtl; }
    public void setRefreshTtl(Duration refreshTtl) { this.refreshTtl = refreshTtl; }
}
