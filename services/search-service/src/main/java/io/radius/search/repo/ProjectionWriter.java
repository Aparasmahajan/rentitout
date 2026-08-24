package io.radius.search.repo;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * The only writer of the read model. Every statement is an upsert, so replaying
 * a topic from offset zero rebuilds the index without duplicating anything.
 */
@Repository
public class ProjectionWriter {

    private final JdbcClient jdbc;

    public ProjectionWriter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void upsertListing(UUID id, UUID ownerId, String kind, String title, String description,
                              List<String> tags, Long priceMinor, String unit, Long depositMinor,
                              Long buyPriceMinor, String photoUrl, double lat, double lon, String status) {
        jdbc.sql("""
                        INSERT INTO listing_doc (id, owner_id, kind, title, description, tags, price_minor,
                                                 unit, deposit_minor, buy_price_minor, photo_url, lat, lon,
                                                 status, owner_name, owner_photo, owner_rating, area_label)
                        SELECT :id, :ownerId, :kind, :title, :description,
                               CASE WHEN :tags = '' THEN '{}'::text[] ELSE string_to_array(:tags, ',') END, :priceMinor, :unit, :depositMinor,
                               :buyPriceMinor, :photoUrl, :lat, :lon, :status,
                               m.display_name, m.photo_url, m.rating_avg, m.area_label
                        FROM (SELECT 1) AS one
                        LEFT JOIN member_doc m ON m.user_id = :ownerId
                        ON CONFLICT (id) DO UPDATE SET
                            kind = EXCLUDED.kind,
                            title = EXCLUDED.title,
                            description = EXCLUDED.description,
                            tags = EXCLUDED.tags,
                            price_minor = EXCLUDED.price_minor,
                            unit = EXCLUDED.unit,
                            deposit_minor = EXCLUDED.deposit_minor,
                            buy_price_minor = EXCLUDED.buy_price_minor,
                            photo_url = COALESCE(EXCLUDED.photo_url, listing_doc.photo_url),
                            lat = EXCLUDED.lat,
                            lon = EXCLUDED.lon,
                            status = EXCLUDED.status,
                            owner_name = COALESCE(EXCLUDED.owner_name, listing_doc.owner_name),
                            owner_photo = COALESCE(EXCLUDED.owner_photo, listing_doc.owner_photo),
                            owner_rating = COALESCE(EXCLUDED.owner_rating, listing_doc.owner_rating),
                            area_label = COALESCE(EXCLUDED.area_label, listing_doc.area_label),
                            updated_at = now()
                        """)
                .param("id", id).param("ownerId", ownerId).param("kind", kind).param("title", title)
                .param("description", description).param("tags", String.join(",", tags))
                .param("priceMinor", priceMinor).param("unit", unit).param("depositMinor", depositMinor)
                .param("buyPriceMinor", buyPriceMinor).param("photoUrl", photoUrl)
                .param("lat", lat).param("lon", lon).param("status", status)
                .update();
    }

    public void setListingStatus(UUID id, String status) {
        jdbc.sql("UPDATE listing_doc SET status = :status, updated_at = now() WHERE id = :id")
                .param("status", status).param("id", id).update();
    }

    public void upsertMember(UUID userId, String displayName, String photoUrl, String areaLabel,
                             List<String> tags, Double lat, Double lon, int radiusKm, boolean openToRequests) {
        jdbc.sql("""
                        INSERT INTO member_doc (user_id, display_name, photo_url, area_label, tags, lat, lon,
                                                radius_km, open_to_requests)
                        VALUES (:userId, :displayName, :photoUrl, :areaLabel,
                                CASE WHEN :tags = '' THEN '{}'::text[] ELSE string_to_array(:tags, ',') END, :lat, :lon, :radiusKm, :open)
                        ON CONFLICT (user_id) DO UPDATE SET
                            display_name = EXCLUDED.display_name,
                            photo_url = EXCLUDED.photo_url,
                            area_label = EXCLUDED.area_label,
                            tags = EXCLUDED.tags,
                            lat = EXCLUDED.lat,
                            lon = EXCLUDED.lon,
                            radius_km = EXCLUDED.radius_km,
                            open_to_requests = EXCLUDED.open_to_requests,
                            updated_at = now()
                        """)
                .param("userId", userId).param("displayName", displayName).param("photoUrl", photoUrl)
                .param("areaLabel", areaLabel).param("tags", String.join(",", tags))
                .param("lat", lat).param("lon", lon).param("radiusKm", radiusKm).param("open", openToRequests)
                .update();

        // Cards carry the owner's name and rating; refresh the denormalised copy.
        jdbc.sql("""
                        UPDATE listing_doc SET owner_name = :displayName, owner_photo = :photoUrl,
                                               area_label = :areaLabel, updated_at = now()
                        WHERE owner_id = :userId
                        """)
                .param("displayName", displayName).param("photoUrl", photoUrl)
                .param("areaLabel", areaLabel).param("userId", userId)
                .update();
    }
}
