package io.radius.user.service;

import io.radius.common.events.RadiusEvents;
import io.radius.common.events.Topics;
import io.radius.common.outbox.OutboxService;
import io.radius.common.support.Geo;
import io.radius.common.web.ApiException;
import io.radius.user.api.Dtos;
import io.radius.user.domain.ProfessionalProfile;
import io.radius.user.domain.Profile;
import io.radius.user.domain.ProfileTag;
import io.radius.user.domain.Tag;
import io.radius.user.domain.UserAccount;
import io.radius.user.repo.ProfessionalProfileRepository;
import io.radius.user.repo.ProfileRepository;
import io.radius.user.repo.ProfileTagRepository;
import io.radius.user.repo.TagRepository;
import io.radius.user.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProfileService {

    private static final int MAX_TAGS = 30;

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final TagRepository tags;
    private final ProfileTagRepository profileTags;
    private final ProfessionalProfileRepository professionals;
    private final OutboxService outbox;

    public ProfileService(UserRepository users, ProfileRepository profiles, TagRepository tags,
                          ProfileTagRepository profileTags, ProfessionalProfileRepository professionals,
                          OutboxService outbox) {
        this.users = users;
        this.profiles = profiles;
        this.tags = tags;
        this.profileTags = profileTags;
        this.professionals = professionals;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public Dtos.MeResponse me(UUID userId) {
        UserAccount user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Account"));
        Profile profile = profiles.findById(userId).orElseGet(() -> new Profile(userId));
        return new Dtos.MeResponse(user.getId(), user.getDisplayName(), user.getPhone(), user.getPhotoUrl(),
                profile.getAreaLabel(), profile.getLat(), profile.getLon(), profile.getSearchRadiusKm(),
                profile.isOpenToRequests(), profile.getBio(), profile.getRatingAvg(),
                profile.getCompletedCount(), profile.isVerified(), profile.getVerifiedAt(),
                profile.isProfessional(), user.getRole(), tagsOf(userId));
    }

    /** What another member sees, optionally with the distance from the viewer. */
    @Transactional(readOnly = true)
    public Dtos.PublicProfileResponse publicProfile(UUID userId, UUID viewerId) {
        UserAccount user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Member"));
        Profile profile = profiles.findById(userId).orElseGet(() -> new Profile(userId));

        Double distanceKm = null;
        if (viewerId != null && !viewerId.equals(userId)) {
            Profile viewer = profiles.findById(viewerId).orElse(null);
            if (viewer != null && viewer.getLat() != null && profile.getLat() != null) {
                distanceKm = Math.round(Geo.distanceMetres(viewer.getLat(), viewer.getLon(),
                        profile.getLat(), profile.getLon()) / 100d) / 10d;
            }
        }
        ProfessionalProfile pro = professionals.findById(userId).filter(ProfessionalProfile::isActive).orElse(null);
        return new Dtos.PublicProfileResponse(user.getId(), user.getDisplayName(), user.getPhotoUrl(),
                profile.getAreaLabel(), profile.getBio(), profile.getRatingAvg(), profile.getCompletedCount(),
                profile.isVerified(), profile.getVerifiedAt(), pro != null,
                pro == null ? null : pro.getTrade(), pro == null ? null : pro.getBusinessName(),
                profile.isOpenToRequests(), tagsOf(userId), distanceKm);
    }

    @Transactional
    public Dtos.MeResponse patch(UUID userId, Dtos.ProfilePatch patch) {
        UserAccount user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Account"));
        Profile profile = profiles.findById(userId).orElseGet(() -> profiles.save(new Profile(userId)));

        if (patch.displayName() != null && !patch.displayName().isBlank()) {
            user.setDisplayName(patch.displayName().trim());
        }
        if (patch.photoUrl() != null) user.setPhotoUrl(patch.photoUrl());
        if (patch.areaLabel() != null) profile.setAreaLabel(patch.areaLabel().trim());
        if (patch.bio() != null) profile.setBio(patch.bio().trim());
        if (patch.searchRadiusKm() != null) profile.setSearchRadiusKm(patch.searchRadiusKm());
        if (patch.openToRequests() != null) profile.setOpenToRequests(patch.openToRequests());

        boolean oneOfLatLon = (patch.lat() == null) != (patch.lon() == null);
        if (oneOfLatLon) {
            throw ApiException.badRequest("bad_point", "Send lat and lon together");
        }
        if (patch.lat() != null) {
            profile.setHome(patch.lat(), patch.lon());
        }

        users.save(user);
        profiles.save(profile);
        publishProfile(user, profile);
        return me(userId);
    }

    @Transactional
    public List<Dtos.TagRef> addTag(UUID userId, Dtos.AddTagRequest req) {
        Profile profile = profiles.findById(userId).orElseGet(() -> profiles.save(new Profile(userId)));
        if (profileTags.countForProfile(userId) >= MAX_TAGS) {
            throw ApiException.conflict("too_many_tags", "A profile can carry " + MAX_TAGS + " tags");
        }
        Tag tag = tags.findBySlug(req.slug()).orElseThrow(() -> ApiException.notFound("Tag " + req.slug()));
        profileTags.save(new ProfileTag(profile.getUserId(), tag.getId(),
                ProfileTag.Relation.valueOf(req.relation())));

        users.findById(userId).ifPresent(u -> publishProfile(u, profile));
        return tagsOf(userId);
    }

    @Transactional
    public List<Dtos.TagRef> removeTag(UUID userId, String slug, String relation) {
        Tag tag = tags.findBySlug(slug).orElseThrow(() -> ApiException.notFound("Tag " + slug));
        profileTags.remove(userId, tag.getId(), relation);
        profiles.findById(userId).ifPresent(p ->
                users.findById(userId).ifPresent(u -> publishProfile(u, p)));
        return tagsOf(userId);
    }

    @Transactional(readOnly = true)
    public List<Dtos.TagRef> vocabulary(String kind) {
        List<Tag> found = kind == null || kind.isBlank()
                ? tags.findAll()
                : tags.findAllByKindOrderByLabelAsc(kind);
        return found.stream()
                .map(t -> new Dtos.TagRef(t.getId(), t.getSlug(), t.getLabel(), t.getKind(), null))
                .sorted(java.util.Comparator.comparing(Dtos.TagRef::label))
                .toList();
    }

    private List<Dtos.TagRef> tagsOf(UUID userId) {
        List<ProfileTag> links = profileTags.findByProfile(userId);
        if (links.isEmpty()) return List.of();
        Map<UUID, Tag> byId = tags.findAllByIdIn(links.stream().map(ProfileTag::getTagId).toList())
                .stream().collect(Collectors.toMap(Tag::getId, Function.identity()));
        return links.stream()
                .filter(l -> byId.containsKey(l.getTagId()))
                .map(l -> {
                    Tag t = byId.get(l.getTagId());
                    return new Dtos.TagRef(t.getId(), t.getSlug(), t.getLabel(), t.getKind(), l.getRelation());
                })
                .toList();
    }

    /**
     * Search-service keeps its own copy of who is where and what they can do.
     * This is the event that keeps it honest.
     */
    private void publishProfile(UserAccount user, Profile profile) {
        outbox.publish(Topics.USER, user.getId(), new RadiusEvents.ProfileUpdated(
                user.getId(), user.getDisplayName(), user.getPhotoUrl(), profile.getAreaLabel(),
                profile.getLat(), profile.getLon(), profile.getSearchRadiusKm(),
                profile.isOpenToRequests(),
                tagsOf(user.getId()).stream().map(Dtos.TagRef::slug).toList(),
                Instant.now()));
    }
}
