package io.radius.listing.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.radius.common.events.RadiusEvents;
import io.radius.common.events.Topics;
import io.radius.common.support.IdempotencyGuard;
import io.radius.listing.service.ListingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Two subscriptions:
 *   radius.user.v1     keeps the member_location projection current
 *   radius.request.v1  blocks the calendar when a request is accepted
 *
 * Both are idempotent — Kafka redelivers, and a double-booked calendar is a
 * support ticket.
 */
@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    private final ListingService listings;
    private final ObjectMapper mapper;
    private final IdempotencyGuard guard;

    public EventConsumer(ListingService listings, ObjectMapper mapper, IdempotencyGuard guard) {
        this.listings = listings;
        this.mapper = mapper;
        this.guard = guard;
    }

    @KafkaListener(topics = Topics.USER, groupId = "listing-service.user")
    public void onUserEvent(@Payload String payload,
                            @Header(name = Topics.HEADER_EVENT_TYPE, required = false) String type,
                            @Header(name = Topics.HEADER_EVENT_ID, required = false) String eventId) throws Exception {
        if (eventId != null && !guard.firstTime("listing.user", eventId)) return;

        switch (type == null ? "" : type) {
            case "ProfileUpdated" -> {
                var e = mapper.readValue(payload, RadiusEvents.ProfileUpdated.class);
                listings.upsertMember(e.userId(), e.displayName(), e.photoUrl(), e.areaLabel(),
                        e.lat(), e.lon(), e.searchRadiusKm());
                log.debug("member_location updated for {}", e.userId());
            }
            // Both of these decide whether this member may publish a home visit.
            case "VerificationDecided" -> {
                var e = mapper.readValue(payload, RadiusEvents.VerificationDecided.class);
                listings.updateMemberIdCheck(e.userId(), e.idChecked());
                log.info("id check for {} is now {}", e.userId(), e.idChecked());
            }
            case "ProfessionalStatusChanged" -> {
                var e = mapper.readValue(payload, RadiusEvents.ProfessionalStatusChanged.class);
                listings.updateMemberProfessional(e.userId(), "ACTIVE".equals(e.state()), e.trade());
                log.info("professional status for {} is now {}", e.userId(), e.state());
            }
            default -> log.debug("ignoring user event of type {}", type);
        }
    }

    @KafkaListener(topics = Topics.REQUEST, groupId = "listing-service.request")
    public void onRequestEvent(@Payload String payload,
                               @Header(name = Topics.HEADER_EVENT_TYPE, required = false) String type,
                               @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) throws Exception {
        if (!"RequestAccepted".equals(type)) return;

        var e = mapper.readValue(payload, RadiusEvents.RequestAccepted.class);
        LocalDate to = e.endDate() == null ? e.startDate() : e.endDate();
        // Keyed on the request id inside blockDates, so a redelivery is a no-op.
        listings.blockDates(e.listingId(), e.startDate(), to, e.requestId());
        log.info("blocked {} to {} on listing {} for request {}", e.startDate(), to, e.listingId(), key);
    }
}
