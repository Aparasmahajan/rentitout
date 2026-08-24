package io.radius.booking.service;

import io.radius.booking.api.Dtos;
import io.radius.booking.client.ListingClient;
import io.radius.booking.domain.BookingRequest;
import io.radius.booking.domain.BookingRequest.Status;
import io.radius.booking.domain.RequestTransition;
import io.radius.booking.repo.MessageRepository;
import io.radius.booking.repo.RequestRepository;
import io.radius.booking.repo.TransitionRepository;
import io.radius.common.events.RadiusEvents;
import io.radius.common.events.Topics;
import io.radius.common.outbox.OutboxService;
import io.radius.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The state machine's only owner. Controllers ask for a transition; this class
 * decides whether the caller may have it, writes the audit row, and publishes
 * the event. A status is never set anywhere else.
 */
@Service
public class RequestService {

    private static final Logger log = LoggerFactory.getLogger(RequestService.class);
    private static final Duration SENT_TTL = Duration.ofHours(48);

    private final RequestRepository requests;
    private final TransitionRepository transitions;
    private final MessageRepository messages;
    private final ListingClient listingClient;
    private final PricingService pricing;
    private final OutboxService outbox;

    public RequestService(RequestRepository requests, TransitionRepository transitions,
                          MessageRepository messages, ListingClient listingClient,
                          PricingService pricing, OutboxService outbox) {
        this.requests = requests;
        this.transitions = transitions;
        this.messages = messages;
        this.listingClient = listingClient;
        this.pricing = pricing;
        this.outbox = outbox;
    }

    // ---- the transaction ---------------------------------------------------

    @Transactional(readOnly = true)
    public Dtos.Breakdown quote(Dtos.QuoteRequest req) {
        return pricing.quote(listingClient.snapshot(req.listingId()), req.units());
    }

    @Transactional
    public Dtos.RequestResponse create(UUID requesterId, Dtos.CreateRequest req) {
        ListingClient.ListingSnapshot listing = listingClient.snapshot(req.listingId());

        if (listing.ownerId().equals(requesterId)) {
            throw ApiException.badRequest("own_listing", "That one is yours already");
        }
        if (!"LIVE".equals(listing.status())) {
            throw ApiException.conflict("listing_not_live", "That listing is not taking requests");
        }

        LocalDate end = pricing.endDate(req.startDate(), listing.unit(), req.units());
        if (requests.countOverlapping(listing.id(), req.startDate(), end) > 0
                || !listingClient.isFree(listing.id(), req.startDate(), end)) {
            throw ApiException.conflict("dates_taken", "Those days are already booked");
        }

        Dtos.Breakdown breakdown = pricing.quote(listing, req.units());
        BookingRequest request = requests.save(new BookingRequest(
                listing.id(), listing.title(), requesterId, listing.ownerId(),
                req.startDate(), end, req.units(), breakdown.unit(), breakdown.rateMinor(),
                breakdown.amountMinor(), breakdown.depositMinor(), breakdown.feeMinor(),
                breakdown.currency(), req.message(), Instant.now().plus(SENT_TTL)));

        audit(request, null, Status.SENT, requesterId, null);
        outbox.publish(Topics.REQUEST, request.getId(), new RadiusEvents.RequestCreated(
                request.getId(), listing.id(), listing.title(), requesterId, listing.ownerId(),
                req.startDate(), req.units(), breakdown.unit(), breakdown.amountMinor(),
                breakdown.depositMinor(), Instant.now()));

        // The owner has 48 hours to answer, so the owner has to be told. Without
        // this the request sits unseen and expires, which reads to the requester
        // as the neighbour ignoring them.
        notify(listing.ownerId(), "request_received", "Someone asked for " + listing.title(),
                "%d %s%s from %s".formatted(req.units(), breakdown.unit().toLowerCase(),
                        req.units() > 1 ? "s" : "", req.startDate()),
                "/requests/" + request.getId());

        log.info("request {} created on listing {}", request.getId(), listing.id());
        return toDto(request, requesterId);
    }

    @Transactional
    public Dtos.RequestResponse accept(UUID requestId, UUID callerId) {
        BookingRequest request = load(requestId, callerId);
        if (!request.getOwnerId().equals(callerId)) {
            throw ApiException.forbidden("Only the owner can accept a request");
        }
        // Re-check now, not when it was sent: someone else may have taken the days.
        if (requests.countOverlapping(request.getListingId(), request.getStartDate(),
                request.getEndDate()) > 0) {
            throw ApiException.conflict("dates_taken", "Those days went to another request");
        }

        Status from = request.status();
        request.transitionTo(Status.ACCEPTED);
        audit(request, from, Status.ACCEPTED, callerId, null);

        // listing-service blocks the calendar off this event; payment-service
        // opens the deposit hold off the same one.
        outbox.publish(Topics.REQUEST, request.getId(), new RadiusEvents.RequestAccepted(
                request.getId(), request.getListingId(), request.getRequesterId(), request.getOwnerId(),
                request.getStartDate(), request.getEndDate(), request.getAmountMinor(),
                request.getDepositMinor(), Instant.now()));
        notify(request.getRequesterId(), "request_accepted", "Accepted",
                request.getListingTitle() + " is yours for " + request.getStartDate(),
                "/requests/" + request.getId());

        return toDto(request, callerId);
    }

    @Transactional
    public Dtos.RequestResponse decline(UUID requestId, UUID callerId, String reason) {
        BookingRequest request = load(requestId, callerId);
        if (!request.getOwnerId().equals(callerId)) {
            throw ApiException.forbidden("Only the owner can decline a request");
        }
        Status from = request.status();
        request.transitionTo(Status.DECLINED);
        audit(request, from, Status.DECLINED, callerId, reason);

        outbox.publish(Topics.REQUEST, request.getId(), new RadiusEvents.RequestDeclined(
                request.getId(), request.getListingId(), request.getRequesterId(), request.getOwnerId(),
                reason, Instant.now()));
        notify(request.getRequesterId(), "request_declined", "Not this time",
                request.getListingTitle() + " was declined", "/requests/" + request.getId());

        return toDto(request, callerId);
    }

    @Transactional
    public Dtos.RequestResponse start(UUID requestId, UUID callerId) {
        BookingRequest request = load(requestId, callerId);
        Status from = request.status();
        request.transitionTo(Status.IN_PROGRESS);
        audit(request, from, Status.IN_PROGRESS, callerId, null);
        return toDto(request, callerId);
    }

    @Transactional
    public Dtos.RequestResponse complete(UUID requestId, UUID callerId) {
        BookingRequest request = load(requestId, callerId);
        Status from = request.status();
        request.transitionTo(Status.COMPLETED);
        audit(request, from, Status.COMPLETED, callerId, null);

        outbox.publish(Topics.REQUEST, request.getId(), new RadiusEvents.RequestCompleted(
                request.getId(), request.getListingId(), request.getRequesterId(), request.getOwnerId(),
                request.getAmountMinor(), request.getDepositMinor(), Instant.now()));
        notify(request.counterpartOf(callerId), "request_completed", "All done",
                "Leave a review for " + request.getListingTitle(), "/requests/" + request.getId());

        return toDto(request, callerId);
    }

    @Transactional
    public Dtos.RequestResponse cancel(UUID requestId, UUID callerId) {
        BookingRequest request = load(requestId, callerId);
        Status from = request.status();
        request.transitionTo(Status.CANCELLED);
        audit(request, from, Status.CANCELLED, callerId, null);

        outbox.publish(Topics.REQUEST, request.getId(), new RadiusEvents.RequestCancelled(
                request.getId(), request.getListingId(), request.getRequesterId(), request.getOwnerId(),
                callerId, Instant.now()));
        notify(request.counterpartOf(callerId), "request_cancelled", "Cancelled",
                request.getListingTitle() + " was cancelled", "/requests/" + request.getId());

        return toDto(request, callerId);
    }

    /** A request nobody answered in 48 hours stops sitting in the owner's list. */
    @Scheduled(fixedDelayString = "${radius.booking.expiry-interval-ms:60000}")
    @Transactional
    public void expireStaleRequests() {
        List<BookingRequest> stale = requests.findExpired(Instant.now(), Limit.of(100));
        for (BookingRequest request : stale) {
            Status from = request.status();
            request.transitionTo(Status.EXPIRED);
            audit(request, from, Status.EXPIRED, null, "no answer in 48 hours");
            notify(request.getRequesterId(), "request_expired", "No answer",
                    request.getListingTitle() + " expired without a reply", "/requests/" + request.getId());
        }
        if (!stale.isEmpty()) log.info("expired {} requests", stale.size());
    }

    // ---- reads -------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Dtos.RequestResponse> list(UUID callerId, String direction) {
        List<BookingRequest> found = "in".equalsIgnoreCase(direction)
                ? requests.findByOwnerIdOrderByCreatedAtDesc(callerId)
                : requests.findByRequesterIdOrderByCreatedAtDesc(callerId);
        return found.stream().map(r -> toDto(r, callerId)).toList();
    }

    @Transactional(readOnly = true)
    public Dtos.RequestResponse get(UUID requestId, UUID callerId) {
        return toDto(load(requestId, callerId), callerId);
    }

    BookingRequest load(UUID requestId, UUID callerId) {
        BookingRequest request = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Request"));
        if (!request.involves(callerId)) {
            // Not "forbidden" — a stranger should not learn that this id exists.
            throw ApiException.notFound("Request");
        }
        return request;
    }

    // ---- internals ---------------------------------------------------------

    private void audit(BookingRequest request, Status from, Status to, UUID actorId, String reason) {
        transitions.save(new RequestTransition(request.getId(), from == null ? null : from.name(),
                to.name(), actorId, reason));
    }

    private void notify(UUID userId, String kind, String title, String body, String deepLink) {
        outbox.publish(Topics.NOTIFICATION, userId, new RadiusEvents.NotificationRequested(
                userId, "push", kind, title, body, deepLink, Instant.now()));
    }

    Dtos.RequestResponse toDto(BookingRequest r, UUID callerId) {
        var breakdown = new Dtos.Breakdown(r.getRateMinor(), r.getUnit(), r.getUnits(), r.getAmountMinor(),
                r.getDepositMinor(), r.getFeeMinor(),
                r.getFeeMinor() == 0 ? "Radius fee: none for now" : "Radius fee",
                r.getTotalMinor(), r.getCurrency(),
                r.getFeeMinor() == 0 ? "Settled between you at handover" : "Paid in the app");

        return new Dtos.RequestResponse(r.getId(), r.getListingId(), r.getListingTitle(),
                r.getRequesterId(), r.getOwnerId(), r.getStartDate(), r.getEndDate(), r.getUnits(),
                r.getUnit(), r.getMessage(), r.status().name(), breakdown, r.getCreatedAt(),
                r.getExpiresAt(), messages.countUnread(r.getId(), callerId),
                r.getOwnerId().equals(callerId),
                transitions.findByRequestIdOrderByAtAsc(r.getId()).stream()
                        .sorted(Comparator.comparing(RequestTransition::getAt))
                        .map(t -> new Dtos.TransitionDto(t.getFromStatus(), t.getToStatus(),
                                t.getActorId(), t.getReason(), t.getAt()))
                        .toList());
    }
}
