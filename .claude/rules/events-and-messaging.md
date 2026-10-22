---
paths:
  - "services/common/src/main/java/io/radius/common/events/**"
  - "services/common/src/main/java/io/radius/common/outbox/**"
  - "services/**/messaging/**"
  - "services/**/client/**"
---

# Events and inter-service calls

## Choosing the mechanism

- **Event**: use when the reader can be eventually consistent. This is the default.
- **Sync call**: use only when a stale answer would be *wrong*, such as a price or a fee.
  Wrap it in a resilience4j circuit breaker and decide the fallback on purpose.

## Publishing

```java
@Transactional
public X doThing(...) {
    // state change via repository...
    outbox.publish(Topics.REQUEST, aggregateId, new RadiusEvents.Something(...));
}
```

- Always publish inside the transaction that changes state. `OutboxPublisher` drains the
  table to Kafka. Never inject `KafkaTemplate` in a service.
- The key is the aggregate id, which keeps per-aggregate ordering.
- Event records all live in `common/events/RadiusEvents.java`, grouped under
  `// ---- <topic> ----` banners.
- Only additive changes are allowed to an existing record. A removal or rename means a `.v2`
  topic published alongside `.v1`. Update `Topics.java`.

## Consuming

- Use `@KafkaListener(topics = Topics.X, groupId = "<service>.<topic-short>")`.
- Read the `Topics.HEADER_EVENT_TYPE` header, `switch` on it, and deserialize with `ObjectMapper`.
- **Delivery is at least once, so every handler must be idempotent:**
  - projections (search): upsert
  - side effects without a natural key (listing calendar): `IdempotencyGuard` (Redis) on `Topics.HEADER_EVENT_ID`
  - money (payment): a unique constraint in the database
- Local projections, such as listing-service's view of user checks, let a service answer
  without a sync call. Prefer extending one over adding a call.

## Topics

`radius.user.v1`, `radius.listing.v1`, `radius.request.v1`, `radius.payment.v1`,
`radius.notification.v1`. To reach a human, publish `NotificationRequested` to the
notification topic. Don't call notification-service.
