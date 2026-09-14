package io.radius.common.outbox;

import io.radius.common.events.Topics;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Drains the outbox on a tick. At-least-once by design — consumers are
 * idempotent, keyed on {@code event-id}.
 */
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH = 200;

    private final OutboxRepository repo;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPublisher(OutboxRepository repo, KafkaTemplate<String, String> kafka) {
        this.repo = repo;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${radius.outbox.interval-ms:500}")
    @Transactional
    public void drain() {
        List<OutboxEvent> batch = repo.findUnpublished(Limit.of(BATCH));
        for (OutboxEvent e : batch) {
            try {
                var record = new ProducerRecord<>(e.getTopic(), e.getKey(), e.getPayload());
                record.headers().add(new RecordHeader(Topics.HEADER_EVENT_TYPE,
                        e.getType().getBytes(StandardCharsets.UTF_8)));
                record.headers().add(new RecordHeader(Topics.HEADER_EVENT_ID,
                        e.getId().toString().getBytes(StandardCharsets.UTF_8)));
                kafka.send(record).get();
                e.markPublished();
            } catch (Exception ex) {
                e.markAttempt();
                log.warn("outbox publish failed for {} (attempt {})", e.getId(), e.getAttempts(), ex);
                // Leave it unpublished; the next tick retries. Ordering per key is
                // preserved because we stop the batch at the first failure.
                break;
            }
        }
    }

    /** Published rows are kept a week for debugging, then dropped. */
    @Scheduled(cron = "${radius.outbox.cleanup-cron:0 15 3 * * *}")
    @Transactional
    public void cleanup() {
        long removed = repo.deleteByPublishedAtIsNotNullAndCreatedAtBefore(
                Instant.now().minus(7, ChronoUnit.DAYS));
        if (removed > 0) log.info("outbox cleanup removed {} rows", removed);
    }
}
