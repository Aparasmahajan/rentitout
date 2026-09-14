package io.radius.user.api;

import io.radius.common.security.AuthUser;
import io.radius.user.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The review queue. Every method starts with requireAdmin() rather than relying
 * on a URL prefix being protected somewhere else — an admin check that lives
 * far from the action it guards is an admin check waiting to be forgotten.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Reviewing ID and trade checks")
public class AdminController {

    private final VerificationService verification;

    public AdminController(VerificationService verification) {
        this.verification = verification;
    }

    @GetMapping("/verifications")
    @Operation(summary = "Open checks, oldest first; pass state to see decided ones")
    public List<VerificationDtos.ReviewItem> queue(AuthUser me,
                                                   @RequestParam(required = false) String state) {
        me.requireAdmin();
        return verification.queue(state);
    }

    @PostMapping("/verifications/{id}/claim")
    @Operation(summary = "Mark a check as being looked at, so two admins do not both do it")
    public VerificationDtos.ReviewItem claim(AuthUser me, @PathVariable UUID id) {
        me.requireAdmin();
        return verification.claim(me.id(), id);
    }

    @PostMapping("/verifications/{id}/approve")
    public VerificationDtos.ReviewItem approve(AuthUser me, @PathVariable UUID id,
                                               @Valid @RequestBody(required = false) VerificationDtos.Decision body) {
        me.requireAdmin();
        return verification.approve(me.id(), id, body == null ? null : body.note());
    }

    @PostMapping("/verifications/{id}/reject")
    @Operation(summary = "Refuse a check — a reason is required, so the member can fix it")
    public VerificationDtos.ReviewItem reject(AuthUser me, @PathVariable UUID id,
                                              @Valid @RequestBody VerificationDtos.Decision body) {
        me.requireAdmin();
        return verification.reject(me.id(), id, body.note());
    }

    @PostMapping("/professionals/{userId}/suspend")
    @Operation(summary = "Take a professional out of circulation without deleting their history")
    public VerificationDtos.ProfessionalResponse suspend(AuthUser me, @PathVariable UUID userId,
                                                         @Valid @RequestBody(required = false) VerificationDtos.Decision body) {
        me.requireAdmin();
        return verification.suspend(me.id(), userId,
                body == null || body.note() == null ? "Paused by an administrator" : body.note());
    }
}
