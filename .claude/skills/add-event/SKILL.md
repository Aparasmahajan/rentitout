---
name: add-event
description: Use when adding or changing a cross-service Kafka event in Radius — a new RadiusEvents record, a new producer via the outbox, a new @KafkaListener consumer, or a projection fed by events (e.g. "notify the owner when X", "keep search in sync with Y", "listing-service needs to know about Z").
---

# Adding a cross-service event

Work through these in order. Each step names the file that already shows the pattern — open it and copy its shape.

## 1. Decide it should be an event
Event if the consumer can be a few hundred ms stale. A sync call (behind resilience4j, in `client/`) only if stale would be *wrong* — like `booking-service/.../client/FeeClient.java`.

## 2. Define the record
`services/common/src/main/java/io/radius/common/events/RadiusEvents.java`, under the right `// ---- <topic>.v1 ----` banner.
- A `record` with ids as `UUID`, money as `long`/`Long` `*Minor`, an `Instant at` last.
- Carry what consumers need so they never call back. Changing an existing record: **add fields only**. Breaking change → new `.v2` topic in `Topics.java`, published alongside v1.

## 3. Publish via the outbox
In the producing `service/` class, inside the existing `@Transactional` method that changes state:
```java
outbox.publish(Topics.LISTING, aggregateId, new RadiusEvents.YourEvent(...));
```
Pattern: `booking-service/.../service/RequestService.java` (`create`, `accept`). Never `KafkaTemplate`.

## 4. Consume idempotently
In the consuming service's `messaging/` class (create one if it has none, e.g. `payment-service/.../messaging/RequestEventConsumer.java`):
- `@KafkaListener(topics = Topics.X, groupId = "<service>.<topic>")`, switch on `Topics.HEADER_EVENT_TYPE`.
- Pick idempotency by consequence:
  - projection → upsert (`search-service/.../repo/ProjectionWriter.java`)
  - side effect, no natural key → `IdempotencyGuard` on `HEADER_EVENT_ID` (`listing-service/.../messaging/EventConsumer.java`)
  - money → unique constraint, handle the duplicate (`payment-service`)
- If the consumer needs a local table, add it with the next `V` migration in **that** service.

## 5. Reaching a human
Don't call notification-service. Publish `NotificationRequested` to `Topics.NOTIFICATION` (see `ChatService.java`).

## 6. Verify
- Unit-test the handler logic (state change, idempotent second delivery).
- `.\scripts\mvn-docker.ps1 test`.
- If the stack is up (`docker compose --profile all`): rebuild the touched services (`docker compose -f infra/docker-compose.yml up -d --build <svc>`), trigger the producer through the gateway, then check the consumer's effect, its `docker compose logs <svc>`, and the topic in Kafka UI (:8090).
- Note the event in `poc.md` if it is part of a feature.
