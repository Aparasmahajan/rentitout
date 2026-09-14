package io.radius.search.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.radius.common.events.RadiusEvents;
import io.radius.common.events.Topics;
import io.radius.search.repo.ProjectionWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the search index from events. There is no synchronous path into this
 * service's database from anywhere else — delete the tables, reset the consumer
 * group to the beginning, and the index rebuilds itself.
 *
 * Every write is an upsert, so no idempotency guard is needed here: replaying
 * the same event twice produces the same row.
 */
@Component
public class ProjectionConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProjectionConsumer.class);

    private final ProjectionWriter writer;
    private final ObjectMapper mapper;

    public ProjectionConsumer(ProjectionWriter writer, ObjectMapper mapper) {
        this.writer = writer;
        this.mapper = mapper;
    }

    @KafkaListener(topics = Topics.LISTING, groupId = "search-service.listing")
    public void onListing(@Payload String payload,
                          @Header(name = Topics.HEADER_EVENT_TYPE, required = false) String type)
            throws Exception {
        switch (type == null ? "" : type) {
            case "ListingPublished" -> {
                var e = mapper.readValue(payload, RadiusEvents.ListingPublished.class);
                writer.upsertListing(e.listingId(), e.ownerId(), e.kind(), e.title(), e.description(),
                        nullSafe(e.tags()), e.priceMinor(), e.unit(), e.depositMinor(), e.buyPriceMinor(),
                        e.photoUrl(), e.lat(), e.lon(), "LIVE");
                log.debug("indexed new listing {}", e.listingId());
            }
            case "ListingUpdated" -> {
                var e = mapper.readValue(payload, RadiusEvents.ListingUpdated.class);
                writer.upsertListing(e.listingId(), e.ownerId(), e.kind(), e.title(), e.description(),
                        nullSafe(e.tags()), e.priceMinor(), e.unit(), e.depositMinor(), e.buyPriceMinor(),
                        e.photoUrl(), e.lat(), e.lon(), e.status());
            }
            case "ListingUnlisted" -> {
                var e = mapper.readValue(payload, RadiusEvents.ListingUnlisted.class);
                writer.setListingStatus(e.listingId(), "UNLISTED");
            }
            default -> log.debug("ignoring listing event of type {}", type);
        }
    }

    @KafkaListener(topics = Topics.USER, groupId = "search-service.user")
    public void onUser(@Payload String payload,
                       @Header(name = Topics.HEADER_EVENT_TYPE, required = false) String type)
            throws Exception {
        if (!"ProfileUpdated".equals(type)) return;
        var e = mapper.readValue(payload, RadiusEvents.ProfileUpdated.class);
        writer.upsertMember(e.userId(), e.displayName(), e.photoUrl(), e.areaLabel(),
                nullSafe(e.tags()), e.lat(), e.lon(), e.searchRadiusKm(), e.openToRequests());
        log.debug("indexed member {}", e.userId());
    }

    private static List<String> nullSafe(List<String> in) {
        return in == null ? List.of() : in;
    }
}
