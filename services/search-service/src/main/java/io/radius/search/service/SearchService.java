package io.radius.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.radius.common.support.Geo;
import io.radius.common.web.ApiException;
import io.radius.search.api.Dtos;
import io.radius.search.parse.LlmFallback;
import io.radius.search.parse.QueryParser;
import io.radius.search.repo.SearchQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final int PAGE_SIZE = 20;
    /** What a guest gets when they have not chosen a distance. */
    private static final int DEFAULT_RADIUS_KM = 5;
    /** Guests see points snapped to this grid, members see the usual ~100 m. */
    private static final double GUEST_PRECISION_M = 250;
    private static final int PIN_THRESHOLD = 200;
    private static final Duration PARSE_CACHE_TTL = Duration.ofHours(6);

    private final QueryParser parser;
    private final LlmFallback llm;
    private final SearchQuery queries;
    private final JdbcClient jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public SearchService(QueryParser parser, LlmFallback llm, SearchQuery queries, JdbcClient jdbc,
                         StringRedisTemplate redis, ObjectMapper mapper) {
        this.parser = parser;
        this.llm = llm;
        this.queries = queries;
        this.jdbc = jdbc;
        this.redis = redis;
        this.mapper = mapper;
    }

    public Dtos.SearchResponse search(UUID viewerId, Dtos.SearchRequest req) {
        QueryParser.Parsed parsed = req.q() == null || req.q().isBlank()
                ? new QueryParser.Parsed(List.of(), List.of(), List.of(), null, null, false)
                : parse(req.q());
        String source = req.q() == null || req.q().isBlank() ? "filters" : "rules";

        // Structured filters win over anything the sentence implied — the member
        // set them by hand, usually to correct the parse.
        List<String> tags = merge(parsed.tags(), req.tags());
        List<String> kinds = merge(parsed.kinds(), req.kinds());
        List<String> terms = new ArrayList<>(parsed.terms());
        if (req.keyword() != null && !req.keyword().isBlank()) {
            terms.addAll(List.of(req.keyword().toLowerCase(Locale.ROOT).split("\\s+")));
        }

        double[] origin = origin(viewerId, req.lat(), req.lon());
        int radiusKm = req.radiusKm() != null ? req.radiusKm()
                : parsed.radiusKm() != null ? parsed.radiusKm()
                : radiusOf(viewerId);

        String tsQuery = toTsQuery(terms, tags);
        int page = req.page() == null ? 0 : req.page();

        List<SearchQuery.Hit> hits = queries.listings(tsQuery, tags, kinds, req.sort(),
                origin[0], origin[1], radiusKm * 1000d, PAGE_SIZE + 1, page * PAGE_SIZE);
        boolean hasMore = hits.size() > PAGE_SIZE;
        if (hasMore) hits = hits.subList(0, PAGE_SIZE);

        // Members only matter on the first page of a "who can do this" query.
        List<SearchQuery.MemberHit> members = page == 0 && (!tags.isEmpty() || !tsQuery.isEmpty())
                ? queries.members(tsQuery, tags, origin[0], origin[1], radiusKm * 1000d, 6)
                : List.of();

        boolean guest = viewerId == null;
        return new Dtos.SearchResponse(
                new Dtos.ParsedQuery(terms, tags, kinds, radiusKm, parsed.day(), source),
                hits.stream().map(h -> toDto(h, guest)).toList(),
                members.stream().map(SearchService::toDto).toList(),
                radiusKm, hasMore);
    }

    /**
     * Rules first. The LLM is asked only when the rules found nothing at all,
     * and the answer is cached by normalised query so we pay once per phrase.
     */
    public QueryParser.Parsed parse(String sentence) {
        QueryParser.Parsed parsed = parser.parse(sentence);
        if (!parsed.isEmpty()) return parsed;

        String cacheKey = "search:parse:" + sentence.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return mapper.readValue(cached, QueryParser.Parsed.class);
            } catch (Exception e) {
                log.warn("could not read cached parse for {}", cacheKey, e);
            }
        }

        Optional<QueryParser.Parsed> fromLlm = llm.parse(sentence);
        fromLlm.ifPresent(p -> {
            try {
                redis.opsForValue().set(cacheKey, mapper.writeValueAsString(p), PARSE_CACHE_TTL);
            } catch (Exception e) {
                log.warn("could not cache parse for {}", cacheKey, e);
            }
        });
        return fromLlm.orElse(parsed);
    }

    public Dtos.MapResponse map(double south, double west, double north, double east) {
        if (north <= south || east <= west) {
            throw ApiException.badRequest("bad_bbox", "Send bbox as south,west,north,east");
        }
        int total = queries.countInBox(south, west, north, east);
        if (total > PIN_THRESHOLD) {
            return new Dtos.MapResponse(List.of(),
                    queries.clustersInBox(south, west, north, east, 12).stream()
                            .map(c -> new Dtos.MapCluster(c.lat(), c.lon(), c.count())).toList(),
                    total, true);
        }
        return new Dtos.MapResponse(
                queries.pinsInBox(south, west, north, east, PIN_THRESHOLD).stream()
                        .map(p -> new Dtos.MapPin(p.id(), p.lat(), p.lon(), p.priceMinor(), p.kind(), p.title()))
                        .toList(),
                List.of(), total, false);
    }

    // ---- internals ---------------------------------------------------------

    /** {@code to_tsquery} input, sanitised: only word characters survive. */
    static String toTsQuery(List<String> terms, List<String> tags) {
        Set<String> parts = new LinkedHashSet<>();
        terms.forEach(t -> add(parts, t));
        tags.forEach(t -> add(parts, t.replace('-', ' ')));
        return String.join(" | ", parts);
    }

    private static void add(Set<String> parts, String raw) {
        for (String word : raw.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (word.length() >= 3) parts.add(word);
        }
    }

    private static List<String> merge(List<String> fromParse, List<String> fromFilters) {
        Set<String> all = new LinkedHashSet<>(fromParse);
        if (fromFilters != null) all.addAll(fromFilters);
        return List.copyOf(all);
    }

    private double[] origin(UUID viewerId, Double lat, Double lon) {
        if (lat != null && lon != null) return new double[]{lat, lon};
        if (viewerId == null) {
            // A guest has no stored home point, so the client has to say where to look.
            throw ApiException.badRequest("no_location",
                    "Send lat and lon, or sign in to use your saved area");
        }
        return jdbc.sql("SELECT lat, lon FROM member_doc WHERE user_id = :id AND lat IS NOT NULL")
                .param("id", viewerId)
                .query((rs, n) -> new double[]{rs.getDouble("lat"), rs.getDouble("lon")})
                .optional()
                .orElseThrow(() -> ApiException.badRequest("no_location",
                        "Set your area in your profile, or send lat and lon"));
    }

    /** The member's own preferred radius, mirrored from radius.user.v1. */
    private int radiusOf(UUID viewerId) {
        if (viewerId == null) return DEFAULT_RADIUS_KM;
        return jdbc.sql("SELECT radius_km FROM member_doc WHERE user_id = :id")
                .param("id", viewerId)
                .query(Integer.class)
                .optional()
                .orElse(5);
    }

    private static Dtos.ListingHit toDto(SearchQuery.Hit h, boolean guest) {
        double[] point = guest ? Geo.coarsen(h.lat(), h.lon(), GUEST_PRECISION_M)
                               : new double[]{h.lat(), h.lon()};
        return new Dtos.ListingHit(h.id(), h.kind(), h.title(), h.description(), h.priceMinor(), h.unit(),
                h.buyPriceMinor(), h.currency(), h.photoUrl(), point[0], point[1],
                Math.round(h.distanceMetres() / 100d) / 10d, h.ownerId(), h.ownerName(), h.ownerPhoto(),
                h.ownerRating(), h.areaLabel(), h.relevance());
    }

    private static Dtos.MemberHit toDto(SearchQuery.MemberHit m) {
        return new Dtos.MemberHit(m.userId(), m.displayName(), m.photoUrl(), m.areaLabel(), m.bio(),
                m.rating(), Math.round(m.distanceMetres() / 100d) / 10d, m.tags());
    }
}
