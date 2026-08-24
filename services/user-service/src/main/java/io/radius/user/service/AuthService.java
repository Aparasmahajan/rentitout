package io.radius.user.service;

import io.jsonwebtoken.Claims;
import io.radius.common.events.RadiusEvents;
import io.radius.common.events.Topics;
import io.radius.common.outbox.OutboxService;
import io.radius.common.security.JwtService;
import io.radius.common.web.ApiException;
import io.radius.user.api.Dtos;
import io.radius.user.domain.Profile;
import io.radius.user.domain.RefreshToken;
import io.radius.user.domain.UserAccount;
import io.radius.user.repo.ProfileRepository;
import io.radius.user.repo.RefreshTokenRepository;
import io.radius.user.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final OtpService otp;
    private final JwtService jwt;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final RefreshTokenRepository refreshTokens;
    private final ProfileService profileService;
    private final OutboxService outbox;

    public AuthService(OtpService otp, JwtService jwt, UserRepository users, ProfileRepository profiles,
                       RefreshTokenRepository refreshTokens, ProfileService profileService,
                       OutboxService outbox) {
        this.otp = otp;
        this.jwt = jwt;
        this.users = users;
        this.profiles = profiles;
        this.refreshTokens = refreshTokens;
        this.profileService = profileService;
        this.outbox = outbox;
    }

    public Dtos.OtpStartResponse startOtp(String phone) {
        OtpService.Issued issued = otp.start(phone);
        return new Dtos.OtpStartResponse(phone, issued.expiresInSeconds(), issued.code());
    }

    /** Verifying an OTP for an unknown number signs that number up. */
    @Transactional
    public Dtos.TokenResponse verifyOtp(Dtos.OtpVerifyRequest req, String userAgent) {
        otp.verify(req.phone(), req.code());

        UserAccount user = users.findByPhone(req.phone()).orElseGet(() -> {
            String name = req.displayName() == null || req.displayName().isBlank()
                    ? "Neighbour " + req.phone().substring(Math.max(0, req.phone().length() - 4))
                    : req.displayName().trim();
            UserAccount created = users.save(UserAccount.fromPhone(req.phone(), name));
            profiles.save(new Profile(created.getId()));
            outbox.publish(Topics.USER, created.getId(),
                    new RadiusEvents.UserRegistered(created.getId(), created.getPhone(),
                            created.getDisplayName(), Instant.now()));
            log.info("new member {}", created.getId());
            return created;
        });

        if (!user.isActive()) {
            throw ApiException.forbidden("This account is not active");
        }
        return issueTokens(user, userAgent);
    }

    @Transactional
    public Dtos.TokenResponse refresh(String refreshToken, String userAgent) {
        Claims claims;
        try {
            claims = jwt.parse(refreshToken);
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_refresh", "Sign in again");
        }
        if (!JwtService.TYPE_REFRESH.equals(claims.get("typ", String.class))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_refresh", "Sign in again");
        }

        UUID jti = UUID.fromString(claims.getId());
        RefreshToken stored = refreshTokens.findById(jti)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid_refresh", "Sign in again"));

        if (!stored.isUsable()) {
            // A revoked token coming back means it leaked. Drop the whole family.
            refreshTokens.revokeAllForUser(stored.getUserId());
            log.warn("refresh replay for user {} — all sessions revoked", stored.getUserId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "refresh_replayed", "Sign in again");
        }

        UserAccount user = users.findById(stored.getUserId())
                .orElseThrow(() -> ApiException.notFound("Account"));
        Dtos.TokenResponse tokens = issueTokens(user, userAgent);
        stored.revoke(null);
        return tokens;
    }

    @Transactional
    public void logout(UUID userId) {
        int revoked = refreshTokens.revokeAllForUser(userId);
        log.info("revoked {} sessions for {}", revoked, userId);
    }

    private Dtos.TokenResponse issueTokens(UserAccount user, String userAgent) {
        RefreshToken token = refreshTokens.save(new RefreshToken(user.getId(),
                Instant.now().plusSeconds(jwt.refreshTtlSeconds()), userAgent));
        return new Dtos.TokenResponse(
                jwt.issueAccess(user.getId(), user.getDisplayName(), user.getRole()),
                jwt.issueRefresh(user.getId(), token.getId().toString()),
                jwt.accessTtlSeconds(),
                profileService.me(user.getId()));
    }
}
