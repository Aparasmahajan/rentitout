package io.radius.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.radius.common.events.RadiusEvents;
import io.radius.common.events.Topics;
import io.radius.common.outbox.OutboxService;
import io.radius.common.web.ApiException;
import io.radius.search.api.Dtos;
import io.radius.search.domain.SavedSearch;
import io.radius.search.repo.SavedSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SavedSearchService {

    private static final Logger log = LoggerFactory.getLogger(SavedSearchService.class);
    private static final int MAX_PER_MEMBER = 10;

    private final SavedSearchRepository repo;
    private final SearchService search;
    private final OutboxService outbox;
    private final ObjectMapper mapper;

    public SavedSearchService(SavedSearchRepository repo, SearchService search, OutboxService outbox,
                              ObjectMapper mapper) {
        this.repo = repo;
        this.search = search;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @Transactional
    public Dtos.SavedSearchDto save(UUID userId, Dtos.SaveSearchRequest req) {
        if (repo.findByUserIdOrderByCreatedAtDesc(userId).size() >= MAX_PER_MEMBER) {
            throw ApiException.conflict("too_many_saved_searches",
                    "You can keep " + MAX_PER_MEMBER + " saved searches");
        }
        String parsedJson;
        try {
            parsedJson = mapper.writeValueAsString(search.parse(req.query()));
        } catch (Exception e) {
            parsedJson = null;
        }
        SavedSearch saved = repo.save(new SavedSearch(userId,
                req.label() == null || req.label().isBlank() ? req.query() : req.label(),
                req.query(), parsedJson,
                req.radiusKm() == null ? 5 : req.radiusKm(),
                req.digestFrequency() == null ? "daily" : req.digestFrequency()));
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<Dtos.SavedSearchDto> mine(UUID userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId).stream().map(SavedSearchService::toDto).toList();
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        repo.findById(id)
                .filter(s -> s.getUserId().equals(userId))
                .ifPresentOrElse(repo::delete, () -> { throw ApiException.notFound("Saved search"); });
    }

    /**
     * The daily digest. It re-runs each saved search and, when there is
     * something new inside the radius, asks notification-service to tell the
     * member. This service never sends anything itself.
     */
    @Scheduled(cron = "${radius.search.digest-cron:0 0 8 * * *}")
    @Transactional
    public void runDailyDigest() {
        List<SavedSearch> due = repo.findByDigestFrequency("daily");
        log.info("running daily digest for {} saved searches", due.size());

        for (SavedSearch saved : due) {
            try {
                var results = search.search(saved.getUserId(), new Dtos.SearchRequest(
                        saved.getRawQuery(), null, null, null, saved.getRadiusKm(), null,
                        "relevance", null, null, 0));
                if (results.listings().isEmpty()) continue;

                outbox.publish(Topics.NOTIFICATION, saved.getUserId(),
                        new RadiusEvents.NotificationRequested(saved.getUserId(), "push", "digest",
                                results.listings().size() + " near you match \"" + saved.getLabel() + "\"",
                                results.listings().get(0).title(),
                                "/search?saved=" + saved.getId(), Instant.now()));
                saved.markRun();
            } catch (RuntimeException e) {
                // One bad saved search must not stop the digest for everyone else.
                log.warn("digest failed for saved search {}", saved.getId(), e);
            }
        }
    }

    private static Dtos.SavedSearchDto toDto(SavedSearch s) {
        return new Dtos.SavedSearchDto(s.getId(), s.getLabel(), s.getRawQuery(), s.getRadiusKm(),
                s.getDigestFrequency(), s.getCreatedAt());
    }
}
