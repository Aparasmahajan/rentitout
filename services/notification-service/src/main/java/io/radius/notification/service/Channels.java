package io.radius.notification.service;

import io.radius.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Delivery adapters behind one interface, so wiring a real provider is a class,
 * not a refactor. Both defaults log — a POC that silently pretends to send push
 * notifications is worse than one that says it did not.
 */
public final class Channels {

    @Component
    @ConditionalOnProperty(prefix = "radius.notify.push", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public static class LoggingPush implements NotificationService.Channel {

        private static final Logger log = LoggerFactory.getLogger(LoggingPush.class);

        @Override
        public String name() { return "push"; }

        @Override
        public boolean supports(String hint) { return hint == null || "push".equals(hint); }

        @Override
        public void deliver(Notification n) {
            // Replace with FCM / APNs against device_token. The contract stays this one.
            log.info("[push] to {} — {} · {}", n.getUserId(), n.getTitle(), n.getBody());
        }
    }

    /** SMS is the fallback when push is not registered; it costs money, so it is off by default. */
    @Component
    @ConditionalOnProperty(prefix = "radius.notify.sms", name = "enabled", havingValue = "true")
    public static class LoggingSms implements NotificationService.Channel {

        private static final Logger log = LoggerFactory.getLogger(LoggingSms.class);

        @Override
        public String name() { return "sms"; }

        @Override
        public boolean supports(String hint) { return "sms".equals(hint); }

        @Override
        public void deliver(Notification n) {
            log.info("[sms] to {} — {}", n.getUserId(), n.getTitle());
        }
    }

    private Channels() {}
}
