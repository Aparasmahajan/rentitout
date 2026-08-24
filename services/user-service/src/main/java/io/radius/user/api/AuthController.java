package io.radius.user.api;

import io.radius.common.security.AuthUser;
import io.radius.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Phone OTP sign-in with rotating refresh tokens")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/otp")
    @Operation(summary = "Send a one-time code to a phone number")
    public Dtos.OtpStartResponse startOtp(@Valid @RequestBody Dtos.OtpStartRequest req) {
        return auth.startOtp(req.phone());
    }

    @PostMapping("/verify")
    @Operation(summary = "Exchange a code for tokens — signs up if the number is new")
    public Dtos.TokenResponse verify(@Valid @RequestBody Dtos.OtpVerifyRequest req,
                                     @RequestHeader(value = "User-Agent", required = false) String ua) {
        return auth.verifyOtp(req, ua);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh token and mint a new access token")
    public Dtos.TokenResponse refresh(@Valid @RequestBody Dtos.RefreshRequest req,
                                      @RequestHeader(value = "User-Agent", required = false) String ua) {
        return auth.refresh(req.refreshToken(), ua);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke every session for the caller")
    public ResponseEntity<Void> logout(AuthUser me) {
        auth.logout(me.id());
        return ResponseEntity.noContent().build();
    }
}
