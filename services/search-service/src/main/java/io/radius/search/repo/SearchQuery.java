package io.radius.search.repo;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Ranking, in one place.
 *
 * The spec says relevance, then distance, then rating. Taken literally that
 * means ts_rank — a float — decides everything and distance never gets a vote.
 * So relevance is bucketed to two decimals first; inside a bucket the closer
 * neighbour wins, and rating breaks the remaining ties.
 */
@Repository
public class SearchQuery {

    public record Hit(UUID id, UUID ownerId, String ownerName, String ownerPhoto, java.math.BigDecimal ownerRating,
                      String kind, String title, String description, String photoUrl, String areaLabel,
                      Long priceMinor, String unit, Long buyPriceMinor, String currency,
                      double lat, double lon, double distanceMetres, double relevance) {}

    public record MemberHit(UUID userId, String displayName, String photoUrl, String areaLabel, String bio,
                            java.math.BigDecimal rating, double lat, double lon, double distanceMetres,
                            double relevance, List<String> tags) {}

    public record Pin(UUID id, double lat, double lon, Long priceMinor, String kind, String title) {}

    public record Cluster(double lat, double lon, int count) {}

    private final JdbcClient jdbc;

    public SearchQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Hit> listings(String tsQuery, List<String> tags, List<String> kinds, String sort,
                              double lat, double lon, double radiusMetres, int limit, int offset) {
        String order = switch (sort == null ? "relevance" : sort) {
            case "distance" -> "h.distance_m ASC, h.relevance DESC";
            case "price" -> "COALESCE(h.price_minor, h.buy_price_minor, 0) ASC, h.distance_m ASC";
            case "rating" -> "COALESCE(h.owner_rating, 0) DESC, h.distance_m ASC";
            default -> "round(h.relevance::numeric, 2) DESC, h.distance_m ASC, COALESCE(h.owner_rating, 0) DESC";
        };

        // The ranking expression has to live in an outer select: Postgres will
        // not resolve an output alias inside an ORDER BY function call.
        String sql = """
                SELECT h.* FROM (
                  SELECT d.id, d.owner_id, d.owner_name, d.owner_photo, d.owner_rating, d.kind, d.title,
                         d.description, d.photo_url, d.area_label, d.price_minor, d.unit, d.buy_price_minor,
                         d.currency, d.lat, d.lon,
                         ST_Distance(d.point, :origin::geography) AS distance_m,
                         CASE WHEN :tsQuery = '' THEN 0
                              ELSE ts_rank(d.search_vector, to_tsquery('simple', :tsQuery)) END AS relevance
                  FROM listing_doc d
                  WHERE d.status = 'LIVE'
                    AND ST_DWithin(d.point, :origin::geography, :radiusMetres)
                    AND (:tsQuery = '' OR d.search_vector @@ to_tsquery('simple', :tsQuery))
                    AND (:tagCount = 0 OR d.tags && string_to_array(:tags, ','))
                    AND (:kindCount = 0 OR d.kind = ANY (string_to_array(:kinds, ',')))
                ) h
                ORDER BY %s
                LIMIT :limit OFFSET :offset
                """.formatted(order);

        return jdbc.sql(sql)
                .param("origin", point(lat, lon))
                .param("radiusMetres", radiusMetres)
                .param("tsQuery", tsQuery == null ? "" : tsQuery)
                .param("tags", String.join(",", tags))
                .param("tagCount", tags.size())
                .param("kinds", String.join(",", kinds))
                .param("kindCount", kinds.size())
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, n) -> new Hit(
                        rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class),
                        rs.getString("owner_name"), rs.getString("owner_photo"),
                        rs.getBigDecimal("owner_rating"), rs.getString("kind"), rs.getString("title"),
                        rs.getString("description"), rs.getString("photo_url"), rs.getString("area_label"),
                        (Long) rs.getObject("price_minor"), rs.getString("unit"),
                        (Long) rs.getObject("buy_price_minor"), rs.getString("currency"),
                        rs.getDouble("lat"), rs.getDouble("lon"), rs.getDouble("distance_m"),
                        rs.getDouble("relevance")))
                .list();
    }

    /** "Someone who knows video editing and lives within 5 km" is a member query, not a listing one. */
    public List<MemberHit> members(String tsQuery, List<String> tags, double lat, double lon,
                                   double radiusMetres, int limit) {
        String sql = """
                SELECT h.* FROM (
                  SELECT m.user_id, m.display_name, m.photo_url, m.area_label, m.bio, m.rating_avg,
                         m.lat, m.lon, m.tags,
                         ST_Distance(m.point, :origin::geography) AS distance_m,
                         CASE WHEN :tsQuery = '' THEN 0
                              ELSE ts_rank(m.search_vector, to_tsquery('simple', :tsQuery)) END AS relevance
                  FROM member_doc m
                  WHERE m.open_to_requests
                    AND m.point IS NOT NULL
                    AND ST_DWithin(m.point, :origin::geography, :radiusMetres)
                    AND ((:tsQuery <> '' AND m.search_vector @@ to_tsquery('simple', :tsQuery))
                         OR (:tagCount > 0 AND m.tags && string_to_array(:tags, ',')))
                ) h
                ORDER BY round(h.relevance::numeric, 2) DESC, h.distance_m ASC
                LIMIT :limit
                """;

        return jdbc.sql(sql)
                .param("origin", point(lat, lon))
                .param("radiusMetres", radiusMetres)
                .param("tsQuery", tsQuery == null ? "" : tsQuery)
                .param("tags", String.join(",", tags))
                .param("tagCount", tags.size())
                .param("limit", limit)
                .query((rs, n) -> {
                    java.sql.Array raw = rs.getArray("tags");
                    List<String> tagList = raw == null ? List.of() : List.of((String[]) raw.getArray());
                    return new MemberHit(rs.getObject("user_id", UUID.class), rs.getString("display_name"),
                            rs.getString("photo_url"), rs.getString("area_label"), rs.getString("bio"),
                            rs.getBigDecimal("rating_avg"), rs.getDouble("lat"), rs.getDouble("lon"),
                            rs.getDouble("distance_m"), rs.getDouble("relevance"), tagList);
                })
                .list();
    }

    public int countInBox(double south, double west, double north, double east) {
        return jdbc.sql("""
                        SELECT count(*) FROM listing_doc
                        WHERE status = 'LIVE'
                          AND ST_Intersects(point, ST_MakeEnvelope(:west, :south, :east, :north, 4326)::geography)
                        """)
                .param("south", south).param("west", west).param("north", north).param("east", east)
                .query(Integer.class).single();
    }

    public List<Pin> pinsInBox(double south, double west, double north, double east, int limit) {
        return jdbc.sql("""
                        SELECT id, lat, lon, price_minor, kind, title FROM listing_doc
                        WHERE status = 'LIVE'
                          AND ST_Intersects(point, ST_MakeEnvelope(:west, :south, :east, :north, 4326)::geography)
                        LIMIT :limit
                        """)
                .param("south", south).param("west", west).param("north", north).param("east", east)
                .param("limit", limit)
                .query((rs, n) -> new Pin(rs.getObject("id", UUID.class), rs.getDouble("lat"),
                        rs.getDouble("lon"), (Long) rs.getObject("price_minor"), rs.getString("kind"),
                        rs.getString("title")))
                .list();
    }

    /**
     * Above the pin threshold the map returns counts on a grid instead of
     * thousands of markers. The grid size is derived from the viewport so the
     * clusters stay roughly the same size on screen at any zoom.
     */
    public List<Cluster> clustersInBox(double south, double west, double north, double east, int gridCells) {
        double cell = Math.max((north - south) / gridCells, 0.0005);
        return jdbc.sql("""
                        SELECT avg(lat) AS lat, avg(lon) AS lon, count(*) AS n
                        FROM listing_doc
                        WHERE status = 'LIVE'
                          AND ST_Intersects(point, ST_MakeEnvelope(:west, :south, :east, :north, 4326)::geography)
                        GROUP BY floor(lat / :cell), floor(lon / :cell)
                        """)
                .param("south", south).param("west", west).param("north", north).param("east", east)
                .param("cell", cell)
                .query((rs, n) -> new Cluster(rs.getDouble("lat"), rs.getDouble("lon"), rs.getInt("n")))
                .list();
    }

    private static String point(double lat, double lon) {
        return "SRID=4326;POINT(%s %s)".formatted(lon, lat);
    }
}
