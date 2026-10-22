# 10 · Testing

> **Audience:** developers. Inventory read from `services/*/src/test`, 14 October 2026.
> **Rules:** `.claude/rules/testing.md`.

## 1. Strategy

Tests are spent where a bug **costs someone money or a wasted trip**, in this priority order:

1. **State machines:** booking requests, reports, verification.
2. **Money arithmetic:** fee basis points, caps, minimums, totals.
3. **Parsing:** the sentence parser that decides what people find.
4. **Geo and privacy:** rounding that keeps front doors private.
5. **Auth boundaries:** 401 versus 403, which both clients depend on to refresh tokens.

Domain logic is tested **directly** (plain JUnit on entities and services), without Spring.
`@WebMvcTest` is used only to pin HTTP status codes and auth behaviour.

## 2. Inventory

**8 test classes, 49 `@Test` methods**, all unit or slice tests. Run with
`.\scripts\mvn-docker.ps1 test`.

| Class | Tests | Covers |
| --- | --- | --- |
| `booking-service/.../domain/BookingRequestStateMachineTest` | 7 | New → SENT; happy path; ACCEPTED → COMPLETED directly; DECLINED final; no skipping SENT → COMPLETED; COMPLETED cannot be cancelled; total = rate × units + deposit + fee |
| `common/.../security/AuthUserTest` | 4 | No role = member; `requireAdmin` throws for a member and passes for an admin; unknown role is not admin |
| `common/.../support/GeoTest` | 3 | ~100 m rounding; rounding is stable; Kreuzberg → Friedrichshain distance |
| `listing-service/.../domain/ModerationDomainTest` | 14 | Report claim → decide, no second decision; comment tombstone; double delete; edit marks edited; verified-booking rule; self-removal versus moderated removal and restore; ban expiry and extension |
| `payment-service/.../FeeConfigTest` | 5 | Default zero; basis points; cap wins; minimum wins; rounding never invents a paisa |
| `search-service/.../parse/QueryParserTest` | 6 | The spec sentence; **metres vs minutes**; day words; kind from the verb; no matching inside words; an unreadable sentence parses to empty |
| `user-service/.../api/AuthControllerTest` (`@WebMvcTest`) | 4 | Sends an OTP; rejects a bad number; verify returns tokens; **logout without a token is 401** |
| `user-service/.../domain/VerificationRequestTest` | 6 | New is open; approve records the decider and the purge date; reject needs a reason; no second decision; claim → in review; withdraw purges immediately |

Two tests earned their keep on day one (from `poc.md`): `AuthControllerTest` caught a **403
where a 401 was needed**, which would have broken token refresh in both clients.
`QueryParserTest` caught `within 30 minutes` parsing as **30 metres** (regex alternation order).

> **Count drift:** `poc.md` says 47 tests and the root `README.md` says 35. The source has
> **49**. Update `poc.md` only after a real run (`/update-record`).

## 3. Conventions

- JUnit 5 + AssertJ. Test classes are package-private.
- Method names are snake_case sentences: `accepted_request_can_complete_without_starting()`.
- Each class opens with a Javadoc saying **why** these cases matter.
- No mocks for domain tests; construct the entity and call the method.
- `spring-boot-starter-test` is in every module; `spring-security-test` in user, listing, search
  and booking; H2 only in user-service (for the slice test).

## 4. What is not tested (the honest gaps)

| Gap | Risk | Suggested first test |
| --- | --- | --- |
| **No integration tests.** Testcontainers 1.20.3 is declared but unused | The whole design leans on events being delivered and applied idempotently, and **nothing proves that end to end** | Accept a request → assert `availability_block` exists in listing DB and `payment` exists in payment DB (Postgres + Kafka containers) |
| No repository / SQL tests | `FeedQuery` and `SearchQuery` (PostGIS, keyset, FTS) are untested | Testcontainers `postgis/postgis:16-3.4`: radius filter, keyset stability, guest snapping |
| No consumer tests | Idempotency regressions (see [13](13-known-issues.md#events-and-data)) | Deliver the same record twice → one row; handler throws once → event still applied on retry |
| No outbox publisher test | Poison row blocks the outbox forever | Row that always fails does not block later rows (after a fix) |
| No controller tests outside auth | Auth level drift between gateway, service and docs | `@WebMvcTest` per controller: guest GET 200, member-only 401 without a token, admin 403 for a member |
| No frontend tests | `lib/api.ts` refresh serialisation, money formatting | Vitest: five parallel 401s → one refresh; `money(10000000)` → `₹1,00,000.00` |
| No contract tests web ↔ API | `types.ts` is hand-maintained | Generate types from `/v3/api-docs`, or snapshot-test DTOs |
| No load tests | Build plan target: search **< 300 ms p95** | k6 against `POST /api/search` with seeded density |

The build plan's standing rule is *"every endpoint gets a slice test"*. The codebase is far from
that. Treat it as the target for new endpoints.

## 5. Running

```powershell
.\scripts\mvn-docker.ps1                                  # all modules, test
.\scripts\mvn-docker.ps1 -pl listing-service -am test     # one module + common
.\scripts\mvn-docker.ps1 -pl search-service -am test -Dtest=QueryParserTest
cd apps\web;    npm run typecheck
cd apps\mobile; npm run typecheck
```

Surefire reports land in `services/<module>/target/surefire-reports/`.

## 6. Manual verification against the stack

Unit tests do not cover cross-service behaviour, so the team **exercises** changes against the
running stack before ticking anything in `poc.md`:

1. `docker compose --profile all up -d --build` and `node infra\seed\seed.mjs`.
2. Drive the path through the gateway (web UI or curl. See [06 §12](06-api-reference.md#12-example-session-curl)).
3. Watch events in Kafka UI (:8090), traces in Jaeger (:16686) and service logs.
4. Record what was **observed** in `poc.md` → "What has actually been run". Never "should work".
