---
paths:
  - "services/**/*.java"
  - "services/**/application.yml"
  - "services/**/pom.xml"
---

# Java services

## Shape of a service

`io.radius.<svc>.` + one of these packages:

| Package | Holds | Notes |
| --- | --- | --- |
| `api` | `@RestController`s, `Dtos.java` (records) | Thin. Validate with `@Valid`, delegate to a service, return a DTO, never an entity. Tag with springdoc `@Tag`/`@Operation`. |
| `domain` | JPA entities | Invariants live here (`BookingRequest.transitionTo`). No public setters for state that has rules. |
| `repo` | Spring Data repos, native-query classes (`FeedQuery`, `SearchQuery`) | Spatial SQL lives here, as native SQL. |
| `service` | `@Service`, `@Transactional` boundaries | Authorization decisions and outbox publishing happen here. |
| `messaging` | `@KafkaListener` consumers | Switch on the `event-type` header. Ignore unknown types at `debug`. |
| `client` | Sync calls to other services | Behind resilience4j. Fall back only where a guess is safe: fee → 0, rate → no fallback. |
| `config` | `PersistenceConfig`, etc. | Cross-cutting config belongs in `common` as auto-config. |

- Constructor injection with `private final` fields. No `@Autowired` on fields, and no Lombok.
- Group a class's sections with banner comments: `// ---- the transaction ------`.
- Every class gets a Javadoc paragraph saying **why it exists or why it is shaped this way**.
- Shared code goes in `common/` and gets wired by auto-config
  (`META-INF/spring/...AutoConfiguration.imports`). It must not depend on any service.

## Errors

- Throw `ApiException.badRequest|conflict|forbidden|notFound(code, message)`.
  `GlobalExceptionHandler` maps it.
- `code` is stable snake_case and clients branch on it. Treat a rename as a breaking API
  change.
- `message` is human copy shown to a member, so write it in a friendly tone: "That one is
  yours already".

## Auth

- Take `AuthUser me` as a controller parameter. Mark it `@Nullable AuthUser me` for endpoints
  a guest may read.
- Guest-readable paths are listed per service under `radius.security.public-get-paths` in
  `application.yml` (`SecurityPathProperties`). Those paths are GET-only, so a POST or PATCH
  on the same path still needs a token. Use `public-paths` (all methods) only for health and
  docs.
- Admin: call `me.requireAdmin()` first thing. Never use `if (me.isAdmin())` to gate.
- A missing or expired token is a **401**, not a 403.

## Money, geo, time

- Store amounts as `long` minor units in `*_minor` columns. Fees are basis points. Do the
  arithmetic in `PricingService` / `FeeConfig`, and no other path may compute a total.
- Use `Geo` in `common/support` for rounding (~100 m for homes, 250 m snap for guests). Don't
  re-implement it.
- Timestamps are `Instant`/`timestamptz`. Booking dates are `LocalDate`.
- Feed pagination is a keyset on `(distance, id)` via `common/support/Cursor`. Don't use an
  offset.
