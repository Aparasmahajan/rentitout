package io.radius.user.api;

import io.radius.common.security.AuthUser;
import io.radius.user.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Verification", description = "ID checks, and the professional profile behind a home visit")
public class VerificationController {

    private final VerificationService verification;

    public VerificationController(VerificationService verification) {
        this.verification = verification;
    }

    @GetMapping("/api/me/verification")
    @Operation(summary = "Where the caller's checks stand, including why anything was refused")
    public VerificationDtos.MyVerification mine(AuthUser me) {
        return verification.mine(me.id());
    }

    @PostMapping("/api/me/verification")
    @Operation(summary = "Ask for an ID or professional check")
    public VerificationDtos.VerificationStatus submit(AuthUser me,
                                                      @Valid @RequestBody VerificationDtos.SubmitVerification req) {
        return verification.submit(me.id(), req);
    }

    @DeleteMapping("/api/me/verification/{id}")
    @Operation(summary = "Withdraw a check that has not been decided yet")
    public ResponseEntity<Void> withdraw(AuthUser me, @PathVariable UUID id) {
        verification.withdraw(me.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/me/professional")
    @Operation(summary = "Create or update the caller's trade details — needs an ID check first")
    public VerificationDtos.ProfessionalResponse upsert(AuthUser me,
                                                        @Valid @RequestBody VerificationDtos.ProfessionalUpsert req) {
        return verification.upsertProfessional(me.id(), req);
    }

    @GetMapping("/api/me/professional")
    public VerificationDtos.ProfessionalResponse mineProfessional(AuthUser me) {
        return verification.professional(me.id());
    }

    /** Public: this is the shopfront a neighbour reads before letting someone in. */
    @GetMapping("/api/professionals/{id}")
    @Operation(summary = "A professional's public details")
    public VerificationDtos.ProfessionalResponse publicProfile(@PathVariable UUID id) {
        return verification.professional(id);
    }
}
