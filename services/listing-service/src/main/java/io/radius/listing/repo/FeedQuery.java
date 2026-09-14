package io.radius.listing.repo;

import io.radius.common.support.Cursor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The nearby feed. Distance filtering happens in PostGIS against the GiST index
 * via ST_DWithin — computing distance in Java and filtering afterwards would
 * read the whole table.
 *
 * Pagination is keyset on (distance, id): stable while new listings appear, and
 * it does not get slower on page 40 the way OFFSET does.
 */
@Repository
public class FeedQuery {

    public record FeedRow(UUID id, UUID ownerId, String kind, String title, String description,
                          Long priceMinor, String unit, Long depositMinor, Long buyPriceMinor,
                          String currency, double lat, double lon, double distanceMetres,
                          String ownerName, String ownerPhoto, String areaLabel,
                          boolean ownerIdChecked, boolean ownerProfessional, String ownerTrade,
                          boolean homeVisit, java.math.BigDecimal ratingAvg, int ratingCount) {}

    private static final String BASE = """
            SELECT x.* FROM (
              SELECT l.id, l.owner_id, l.kind, l.title, l.description, l.price_minor, l.unit,
                     l.deposit_minor, l.buy_price_minor, l.currency, l.lat, l.lon, l.home_visit,
                     l.rating_avg, l.rating_count,
                     ST_Distance(l.point, :origin::geography) AS distance_m,
                     m.display_name AS owner_name, m.photo_url AS owner_photo, m.area_label,
                     COALESCE(m.id_checked, false) AS owner_id_checked,
                     COALESCE(m.professional, false) AS owner_professional,
                     m.trade AS owner_trade
              FROM listing l
              LEFT JOIN member_location m ON m.user_id = l.owner_id
              WHERE l.status = 'LIVE'
                AND ST_DWithin(l.point, :origin::geography, :radiusMetres)
                %s
            ) x
            %s
            ORDER BY x.distance_m ASC, x.id ASC
            LIMIT :limit
            """;

    private final JdbcClient jdbc;

    public FeedQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<FeedRow> nearby(double lat, double lon, double radiusMetres, String kind,
                                UUID excludeOwner, Cursor cursor, int limit) {
        List<String> inner = new ArrayList<>();
        if (kind != null) inner.add("AND l.kind = :kind");
        if (excludeOwner != null) inner.add("AND l.owner_id <> :excludeOwner");

        String outer = cursor == null ? "" : "WHERE (x.distance_m, x.id) > (:cursorDistance, :cursorId)";
        String sql = BASE.formatted(String.join("\n    ", inner), outer);

        var spec = jdbc.sql(sql)
                .param("origin", "SRID=4326;POINT(%s %s)".formatted(lon, lat))
                .param("radiusMetres", radiusMetres)
                .param("limit", limit);

        if (kind != null) spec = spec.param("kind", kind);
        if (excludeOwner != null) spec = spec.param("excludeOwner", excludeOwner);
        if (cursor != null) {
            spec = spec.param("cursorDistance", cursor.sortValue())
                    .param("cursorId", UUID.fromString(cursor.id()));
        }

        return spec.query((rs, rowNum) -> new FeedRow(
                rs.getObject("id", UUID.class),
                rs.getObject("owner_id", UUID.class),
                rs.getString("kind"),
                rs.getString("title"),
                rs.getString("description"),
                (Long) rs.getObject("price_minor"),
                rs.getString("unit"),
                (Long) rs.getObject("deposit_minor"),
                (Long) rs.getObject("buy_price_minor"),
                rs.getString("currency"),
                rs.getDouble("lat"),
                rs.getDouble("lon"),
                rs.getDouble("distance_m"),
                rs.getString("owner_name"),
                rs.getString("owner_photo"),
                rs.getString("area_label"),
                rs.getBoolean("owner_id_checked"),
                rs.getBoolean("owner_professional"),
                rs.getString("owner_trade"),
                rs.getBoolean("home_visit"),
                rs.getBigDecimal("rating_avg"),
                rs.getInt("rating_count"))).list();
    }
}
