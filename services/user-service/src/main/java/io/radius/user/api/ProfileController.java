package io.radius.user.api;

import io.radius.common.security.AuthUser;
import org.springframework.lang.Nullable;
import io.radius.user.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Profile", description = "One profile per member, carrying both sides of the market")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/api/me")
    @Operation(summary = "The caller's own profile")
    public Dtos.MeResponse me(AuthUser me) {
        return profiles.me(me.id());
    }

    @PatchMapping("/api/me/profile")
    @Operation(summary = "Update name, photo, area, home point, radius or bio")
    public Dtos.MeResponse patch(AuthUser me, @Valid @RequestBody Dtos.ProfilePatch patch) {
        return profiles.patch(me.id(), patch);
    }

    @PostMapping("/api/me/tags")
    @Operation(summary = "Add a tag under has · teaches · needs · enjoys")
    public List<Dtos.TagRef> addTag(AuthUser me, @Valid @RequestBody Dtos.AddTagRequest req) {
        return profiles.addTag(me.id(), req);
    }

    @DeleteMapping("/api/me/tags/{slug}")
    @Operation(summary = "Remove one tag relation")
    public List<Dtos.TagRef> removeTag(AuthUser me, @PathVariable String slug,
                                       @RequestParam String relation) {
        return profiles.removeTag(me.id(), slug, relation);
    }

    @GetMapping("/api/users/{id}")
    @Operation(summary = "Another member's public profile, with distance when both points are known")
    public Dtos.PublicProfileResponse publicProfile(@PathVariable UUID id, @Nullable AuthUser me) {
        return profiles.publicProfile(id, me == null ? null : me.id());
    }

    @GetMapping("/api/tags")
    @Operation(summary = "The tag vocabulary, optionally filtered by kind")
    public List<Dtos.TagRef> vocabulary(@RequestParam(required = false) String kind) {
        return profiles.vocabulary(kind);
    }
}
