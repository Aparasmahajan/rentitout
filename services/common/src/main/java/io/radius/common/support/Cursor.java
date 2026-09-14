package io.radius.common.support;

import io.radius.common.web.ApiException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Keyset pagination. The cursor is opaque to clients but is just
 * {@code <sortValue>|<id>} — the last row of the previous page.
 */
public record Cursor(double sortValue, String id) {

    public String encode() {
        String raw = sortValue + "|" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int split = raw.lastIndexOf('|');
            return new Cursor(Double.parseDouble(raw.substring(0, split)), raw.substring(split + 1));
        } catch (RuntimeException e) {
            throw ApiException.badRequest("bad_cursor", "That page cursor is not valid");
        }
    }
}
