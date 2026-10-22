# Radius (repo: rentitout)

A neighbourhood index of people, skills and things. A member can rent a ladder, buy a printer,
hire an electrician, book two hours of video editing, or post what they need — and the same
member can be the one listing. Search runs against the opted-in community **inside a chosen
radius**, not against the web. Currency is INR (₹), stored as integer paise.

This is a POC on branch `dev` (PRs go to `main`). Phases 00–03 are built end to end, 04 and
05 are partial.

## Read these before you change anything

| File | What it is for |
| --- | --- |
| `poc.md` | **The build record.** What is built (`[x]`), partial (`[~]`), next (`[ ]`), and *why*. Part 3 is the ordered to-do list — "Block B ← start here". |
| `README.md` | How to run it, plus the architecture decisions in short form. |
| `design/Radius Build Plan.dc.html` | The original phased plan the code implements. |
| `.claude/rules/*.md` | Conventions per area. They load automatically when you touch matching paths. |

If a change makes `poc.md` wrong, update `poc.md` in the same change. See
`.claude/rules/build-record.md`.

## Layout

```
services/              Java 21 · Spring Boot 3.3 · Maven multi-module (parent: services/pom.xml)
  common/              Shared auto-config: JWT, outbox, idempotency, ApiException, Geo, Cursor
  api-gateway/   :8080 Routing, JWT at the edge, Redis rate limits, CORS
  user-service/  :8081 Accounts, phone OTP, refresh tokens, profiles, tags, ID/pro verification
  listing-service/:8082 Listings (7 kinds), photos, availability, nearby feed, comments/ratings/reports
  search-service/:8083 CQRS read model fed by Kafka; sentence parser; map
  booking-service/:8084 Request state machine, price breakdown, STOMP chat
  payment-service/:8085 Fee config, payments, deposit holds, payouts (gateway is a sandbox)
  notification-service/:8086 Inbox + SSE; delivery logs instead of sending
apps/web/              Next.js 15 App Router · React 19 · TanStack Query · client-rendered PWA
apps/mobile/           Expo 52 · expo-router · React Native 0.76
infra/                 docker-compose.yml, DB bootstrap SQL, seed/seed.mjs (seeds through the public API)
scripts/               devstack.ps1 + services.ps1: the Docker-free Windows stack
design/                Build plan, design directions, and the "Postcard" design system bundle (_ds/)
```

Java packages follow one shape per service: `io.radius.<svc>.{api, domain, repo, service, messaging, client, config}`.
Controllers live in `api/` with request/response records in `Dtos.java`.

## Commands

The team's toolchain is **Maven 3.9.x on the host, with Java 21 and every backing service from
Docker**. Run these from the repo root in **PowerShell**. Each machine sets
`RADIUS_MAVEN_HOME` to its Maven folder in `.claude/settings.local.json`.

```powershell
# Build / test: host Maven inside a JDK 21 container (eclipse-temurin:21-jdk), ~/.m2 shared
.\scripts\mvn-docker.ps1                         # test (default)
.\scripts\mvn-docker.ps1 -DskipTests package
.\scripts\mvn-docker.ps1 -pl booking-service -am test

# Backing services only: Postgres+PostGIS :5432, Redis :6379, Kafka :9094, MinIO :9000/9001,
# Kafka UI :8090, Jaeger :16686
cd infra; docker compose --profile infra up -d

# Everything, with the seven services built into JDK 21 images; only the gateway :8080 is on the host
cd infra; docker compose --profile all up -d --build
docker compose -f infra/docker-compose.yml ps
docker compose -f infra/docker-compose.yml logs -f booking-service
docker compose -f infra/docker-compose.yml down       # `down -v` WIPES DATA — ask first

# Clients
cd apps\web;    npm run typecheck; npm run lint; npm run dev     # http://localhost:3000
cd apps\mobile; npm run typecheck; npx expo start

node infra\seed\seed.mjs                   # seed members, listings, a booking through the API
```

**Never run bare `mvn` or `java` for the services.** Host JDKs may be older than 21, so a
hook blocks bare `mvn` when `JAVA_HOME` or `PATH` points at an old one. If Docker Desktop
isn't running, say so and don't work around it.

`scripts/devstack.ps1` and `scripts/services.ps1` are the fallback for machines **without**
Docker. They use portable binaries and ports 5433/6380/9092/9010, and their `reset` wipes
data. See README.

Sign-in for local testing: phone `+491700000001`. It is also the default admin. The OTP comes
back in the response, because there is no SMS provider.

## Non-negotiables

These are the decisions the whole design leans on. Breaking one is a bug even when the tests pass.

1. **Money is integer minor units** (`long`, `*_minor` columns, `*Minor` fields). Never use
   `double`, `float` or `BigDecimal` for an amount. Fees are basis points (`250` = 2.5%).
   **Clients never compute a total.** They render the `Breakdown` the server sends.
2. **Each service owns its schema.** Never read another service's tables. Cross-service data
   arrives by event (eventually consistent) or by a sync call (only where stale = wrong:
   booking→listing for the rate, booking→payment for the fee).
3. **Events go through the outbox.** Call `OutboxService.publish(...)` in the same
   `@Transactional` as the state change. Never use `KafkaTemplate` directly. Every consumer
   is idempotent (upsert, Redis guard, or a unique constraint when money is involved). Event
   records live in `common/events/RadiusEvents.java`. Additive changes only; a breaking change
   gets a `.v2` topic.
4. **Distance is PostGIS.** Filter with `ST_DWithin` against the GiST index. `ST_Distance`
   may appear in a `SELECT` or `ORDER BY`, never as a filter, and distance is never computed
   in Java.
5. **Privacy.** Home points are rounded to ~100 m before storage, and guests get points
   snapped to a 250 m grid. A professional's shop point is exact, on purpose.
6. **State machines have one owner.** A `BookingRequest` status changes only via
   `transitionTo` and is driven only from `RequestService`, and each transition writes an
   audit row.
7. **Authorization throws.** Admin endpoints call `me.requireAdmin()`, which throws instead of
   returning a boolean. Home-visit listings need both the `IDENTITY` and `PROFESSIONAL` checks
   (`ListingService.requireCheckedProfessional`). A refusal must carry a reason.
8. **Errors cross the boundary as `ApiException`** with a stable snake_case `code`
   (`dates_taken`, `reason_required`). Clients branch on `code`, never on the message.
9. **Unauthenticated = 401, not 403.** Both clients refresh tokens only on a 401.
10. **Copy says what we checked, not what we vouch for.** Use "ID checked", never "verified".
11. **Applied Flyway migrations are immutable.** Add `V{n+1}__description.sql` instead.

## How to work here

- Match the surrounding code. The codebase uses explanatory Javadoc and block comments that
  say *why* (look at `RequestService`, `V4__comments_ratings_reports.sql`). New code should
  read the same way, and should not narrate *what*.
- Prefer the smallest change that fits the existing machinery. `poc.md` repeatedly calls out
  where a feature is "a flow, not a migration" or "a UI job, not a data-model one". Check it
  first.
- Verify before you claim. Run `.\scripts\mvn-docker.ps1 test` for backend changes and
  `npm run typecheck` in each client you touched. Report failures as they are.
- Web and mobile keep parallel `lib/` modules (`api.ts`, `auth.tsx`, `types.ts`, `format.ts`,
  `useThread.ts`). A change to an API shape usually needs both.

## Claude Code tooling in this repo

- `.claude/commands/`: `/verify`, `/review`, `/next-task`, `/update-record`
- `.claude/skills/`: add-event, add-endpoint, run-local-stack. These load by task.
- `.claude/agents/`: `code-reviewer`, `security-auditor`
- `.claude/hooks/`: guard risky shell commands, protect migrations and secrets, and flag
  breaches of the non-negotiables after edits.
- Personal overrides go in `CLAUDE.local.md` and `.claude/settings.local.json`. Both are
  gitignored.
