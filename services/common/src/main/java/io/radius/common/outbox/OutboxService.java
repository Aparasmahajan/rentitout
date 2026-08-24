package io.radius.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Call this inside the transaction that changed state. Nothing here touches
 * Kafka — that is the publisher's job, and it runs after the commit.
 *
 * Registered by {@code RadiusOutboxAutoConfiguration} rather than by a stereotype
 * annotation: this class lives in the shared library, outside every service's
 * component scan.
 */
public class OutboxService {

    private final OutboxRepository repo;
    private final ObjectMapper mapper;

    public OutboxService(OutboxRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public void publish(String topic, Object key, Object event) {
        try {
            repo.save(new OutboxEvent(topic, String.valueOf(key),
                    event.getClass().getSimpleName(), mapper.writeValueAsString(event)));
        } catch (JsonProcessingException e) {
            // An event we cannot serialise is a programming error, not a runtime condition.
            throw new IllegalStateException("cannot serialise event " + event.getClass(), e);
        }
    }
}
