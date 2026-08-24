package io.radius.user.service;

import io.radius.common.events.RadiusEvents;
import io.radius.common.events.Topics;
import io.radius.common.outbox.OutboxService;
import io.radius.common.web.ApiException;
import io.radius.user.api.VerificationDtos;
import io.radius.user.domain.ProfessionalProfile;
import io.radius.user.domain.Profile;
import io.radius.user.domain.UserAccount;
import io.radius.user.domain.VerificationRequest;
import io.radius.user.repo.ProfessionalProfileRepository;
import io.radius.user.repo.ProfileRepository;
import io.radius.user.repo.UserRepository;
import io.radius.user.repo.VerificationRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The rule this exists to enforce: nobody gets sent to a stranger's flat
 * without a person having looked at their ID first.
 *
 * Two gates, in order.
 *   IDENTITY     - we have seen government ID. Unlocks applying as a professional.
 *   PROFESSIONAL - we have also seen trade paperwork. Unlocks home-visit listings.
 *
 * The badge says "ID checked", never "approved" or "trusted": the platform
 * states what it looked at and takes no position on the quality of the work.
 */
@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);
    private static final String CHECKED_BY = "admin";

    private final VerificationRequestRepository requests;
    private final ProfessionalProfileRepository professionals;
    private final ProfileRepository profiles;
    private final UserRepository users;
    private final OutboxService outbox;

    public VerificationService(VerificationRequestRepository requests,
                               ProfessionalProfileRepository professionals,
                               ProfileRepository profiles, UserRepository users,
                               OutboxService outbox) {
        this.requests = requests;
        this.professionals = professionals;
        this.profiles = profiles;
        this.users = users;
        this.outbox = outbox;
    }

    // ---- the member's side -------------------------------------------------

    @Transactional
    public VerificationDtos.VerificationStatus submit(UUID userId, VerificationDtos.SubmitVerification req) {
        VerificationRequest.Kind kind = VerificationRequest.Kind.valueOf(req.kind());

        requests.findOpen(userId, kind.name()).ifPresent(open -> {
            throw ApiException.conflict("already_open",
                    "That check is already with us — we will come back to you");
        });

        if (kind == VerificationRequest.Kind.PROFESSIONAL) {
            if (!isIdChecked(userId)) {
                throw ApiException.conflict("id_check_first",
                        "Get your ID checked first, then apply as a professional");
            }
            professionals.findById(userId).orElseThrow(() -> ApiException.badRequest(
                    "no_professional_profile", "Fill in your trade details before applying"));
        }

        var saved = requests.save(new VerificationRequest(userId, kind,
                req.evidenceKeys() == null ? null : String.join(",", req.evidenceKeys()), req.note()));
        log.info("verification {} submitted by {}", kind, userId);
        notifyMember(userId, "verification_submitted", "We have your details",
                kind == VerificationRequest.Kind.IDENTITY
                        ? "Someone will look at your ID shortly."
                        : "Someone will look at your trade details shortly.");
        return toStatus(saved);
    }

    @Transactional
    public void withdraw(UUID userId, UUID requestId) {
        VerificationRequest request = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Request"));
        if (!request.getUserId().equals(userId)) throw ApiException.notFound("Request");
        request.withdraw();
    }

    /** "Where am I up to" — the whole answer in one call. */
    @Transactional(readOnly = true)
    public VerificationDtos.MyVerification mine(UUID userId) {
        List<VerificationRequest> all = requests.findByUserIdOrderBySubmittedAtDesc(userId);
        Profile profile = profiles.findById(userId).orElse(null);
        ProfessionalProfile pro = professionals.findById(userId).orElse(null);

        boolean idChecked = profile != null && profile.isVerified();
        return new VerificationDtos.MyVerification(
                idChecked,
                profile == null ? null : profile.getVerifiedAt(),
                latestState(all, VerificationRequest.Kind.IDENTITY),
                latestState(all, VerificationRequest.Kind.PROFESSIONAL),
                idChecked,
                pro != null && pro.isActive(),
                all.stream().map(VerificationService::toStatus).toList());
    }

    @Transactional(readOnly = true)
    public boolean isIdChecked(UUID userId) {
        return profiles.findById(userId).map(Profile::isVerified).orElse(false);
    }

    // ---- the professional profile ------------------------------------------

    @Transactional
    public VerificationDtos.ProfessionalResponse upsertProfessional(
            UUID userId, VerificationDtos.ProfessionalUpsert req) {

        if (!isIdChecked(userId)) {
            throw ApiException.conflict("id_check_first",
                    "We check ID before anyone lists a service that visits homes");
        }

        ProfessionalProfile pro = professionals.findById(userId)
                .orElseGet(() -> new ProfessionalProfile(userId,
                        ProfessionalProfile.Trade.valueOf(req.trade())));

        pro.setTrade(ProfessionalProfile.Trade.valueOf(req.trade()));
        pro.setBusinessName(req.businessName());
        pro.setAbout(req.about());
        pro.setShopAddress(req.shopAddress());
        if ((req.shopLat() == null) != (req.shopLon() == null)) {
            throw ApiException.badRequest("bad_point", "Send the shop lat and lon together");
        }
        if (req.shopLat() != null) pro.setShop(req.shopLat(), req.shopLon());
        if (req.serviceRadiusKm() != null) pro.setServiceRadiusKm(req.serviceRadiusKm());
        pro.setYearsExperience(req.yearsExperience());
        pro.setLicenceRef(req.licenceRef());
        pro.setInsuranceRef(req.insuranceRef());
        pro.setLanguages(req.languages());
        pro.setContactPhone(req.contactPhone());

        professionals.save(pro);
        publishProfessional(pro);
        return toProfessional(pro);
    }

    @Transactional(readOnly = true)
    public VerificationDtos.ProfessionalResponse professional(UUID userId) {
        ProfessionalProfile pro = professionals.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Professional profile"));
        return toProfessional(pro);
    }

    // ---- the admin's side ---------------------------------------------------

    @Transactional(readOnly = true)
    public List<VerificationDtos.ReviewItem> queue(String state) {
        List<VerificationRequest> found = state == null || state.isBlank()
                ? requests.findQueue(Limit.of(100))
                : requests.findByState(state, Limit.of(100));
        return found.stream().map(this::toReviewItem).toList();
    }

    @Transactional
    public VerificationDtos.ReviewItem claim(UUID adminId, UUID requestId) {
        VerificationRequest request = load(requestId);
        request.claim(adminId);
        return toReviewItem(request);
    }

    @Transactional
    public VerificationDtos.ReviewItem approve(UUID adminId, UUID requestId, String note) {
        VerificationRequest request = load(requestId);
        request.approve(adminId, note);

        if (VerificationRequest.Kind.IDENTITY.name().equals(request.getKind())) {
            Profile profile = profiles.findById(request.getUserId())
                    .orElseGet(() -> profiles.save(new Profile(request.getUserId())));
            profile.markIdChecked(CHECKED_BY);
            profiles.save(profile);
            notifyMember(request.getUserId(), "id_checked", "Your ID is checked",
                    "You can now apply to offer a service that visits homes.");
        } else {
            ProfessionalProfile pro = professionals.findById(request.getUserId())
                    .orElseThrow(() -> ApiException.conflict("no_professional_profile",
                            "That member has no trade details to approve"));
            pro.activate();
            professionals.save(pro);
            profiles.findById(request.getUserId()).ifPresent(p -> {
                p.setProfessional(true);
                profiles.save(p);
            });
            publishProfessional(pro);
            notifyMember(request.getUserId(), "professional_active", "You are listed as a professional",
                    "You can now publish services that include a home visit.");
        }

        publishDecision(request);
        log.info("verification {} approved for {}", request.getKind(), request.getUserId());
        return toReviewItem(request);
    }

    @Transactional
    public VerificationDtos.ReviewItem reject(UUID adminId, UUID requestId, String note) {
        VerificationRequest request = load(requestId);
        request.reject(adminId, note);
        publishDecision(request);
        notifyMember(request.getUserId(), "verification_rejected", "We could not complete that check",
                note);
        return toReviewItem(request);
    }

    /** Pulls a professional out of circulation without deleting their history. */
    @Transactional
    public VerificationDtos.ProfessionalResponse suspend(UUID adminId, UUID userId, String note) {
        ProfessionalProfile pro = professionals.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Professional profile"));
        pro.suspend();
        professionals.save(pro);
        profiles.findById(userId).ifPresent(p -> {
            p.setProfessional(false);
            profiles.save(p);
        });
        publishProfessional(pro);
        notifyMember(userId, "professional_suspended", "Your professional listing is paused", note);
        log.info("professional {} suspended by {}", userId, adminId);
        return toProfessional(pro);
    }

    // ---- internals ----------------------------------------------------------

    private VerificationRequest load(UUID requestId) {
        return requests.findById(requestId).orElseThrow(() -> ApiException.notFound("Request"));
    }

    private static String latestState(List<VerificationRequest> all, VerificationRequest.Kind kind) {
        return all.stream()
                .filter(v -> v.getKind().equals(kind.name()))
                .max(Comparator.comparing(VerificationRequest::getSubmittedAt))
                .map(VerificationRequest::getState)
                .orElse("NONE");
    }

    private static VerificationDtos.VerificationStatus toStatus(VerificationRequest v) {
        return new VerificationDtos.VerificationStatus(v.getId(), v.getKind(), v.getState(),
                v.getMemberNote(), v.getDecisionNote(), v.getSubmittedAt(), v.getDecidedAt(), v.isOpen());
    }

    private VerificationDtos.ProfessionalResponse toProfessional(ProfessionalProfile pro) {
        UserAccount user = users.findById(pro.getUserId()).orElse(null);
        Profile profile = profiles.findById(pro.getUserId()).orElse(null);
        return new VerificationDtos.ProfessionalResponse(
                pro.getUserId(), user == null ? null : user.getDisplayName(), pro.getTrade(),
                pro.getBusinessName(), pro.getAbout(), pro.getShopAddress(), pro.getShopLat(),
                pro.getShopLon(), pro.getServiceRadiusKm(), pro.getYearsExperience(),
                pro.getLicenceRef(), pro.getInsuranceRef(), pro.getLanguages(), pro.getContactPhone(),
                pro.getState(), profile != null && profile.isVerified(),
                profile == null ? null : profile.getVerifiedAt(), pro.getCreatedAt());
    }

    private VerificationDtos.ReviewItem toReviewItem(VerificationRequest v) {
        UserAccount user = users.findById(v.getUserId()).orElse(null);
        List<String> keys = new ArrayList<>();
        if (v.getEvidenceKeys() != null && !v.getEvidenceKeys().isBlank()) {
            keys.addAll(List.of(v.getEvidenceKeys().split(",")));
        }
        return new VerificationDtos.ReviewItem(
                v.getId(), v.getUserId(),
                user == null ? null : user.getDisplayName(),
                user == null ? null : user.getPhone(),
                v.getKind(), v.getState(), v.getMemberNote(), keys,
                v.getSubmittedAt(), v.getPurgeAfter(),
                professionals.findById(v.getUserId()).map(this::toProfessional).orElse(null));
    }

    private void publishDecision(VerificationRequest v) {
        boolean idChecked = profiles.findById(v.getUserId()).map(Profile::isVerified).orElse(false);
        Instant checkedAt = profiles.findById(v.getUserId()).map(Profile::getVerifiedAt).orElse(null);
        outbox.publish(Topics.USER, v.getUserId(), new RadiusEvents.VerificationDecided(
                v.getUserId(), v.getKind(), v.getState(), idChecked, checkedAt, Instant.now()));
    }

    private void publishProfessional(ProfessionalProfile pro) {
        outbox.publish(Topics.USER, pro.getUserId(), new RadiusEvents.ProfessionalStatusChanged(
                pro.getUserId(), pro.getTrade(), pro.getBusinessName(), pro.getState(),
                pro.getShopLat(), pro.getShopLon(), pro.getServiceRadiusKm(), Instant.now()));
    }

    private void notifyMember(UUID userId, String kind, String title, String body) {
        outbox.publish(Topics.NOTIFICATION, userId, new RadiusEvents.NotificationRequested(
                userId, "push", kind, title, body, "/me/verification", Instant.now()));
    }
}
