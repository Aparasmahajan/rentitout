package io.radius.common.events;

/**
 * Topic names carry a version. When an event shape has to break, publish
 * {@code .v2} alongside {@code .v1} and retire v1 once every consumer moved.
 */
public final class Topics {

    public static final String USER = "radius.user.v1";
    public static final String LISTING = "radius.listing.v1";
    public static final String REQUEST = "radius.request.v1";
    public static final String PAYMENT = "radius.payment.v1";
    public static final String NOTIFICATION = "radius.notification.v1";

    /** Header carrying the logical event type, so a consumer can route without deserialising. */
    public static final String HEADER_EVENT_TYPE = "event-type";
    /** Header carrying the outbox row id, used by consumers for idempotency. */
    public static final String HEADER_EVENT_ID = "event-id";

    private Topics() {}
}
