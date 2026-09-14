# Radius

A neighbourhood index of people, skills and things. A member can rent a ladder, buy a
printer, hire an electrician, book two hours of video editing, or post what they need —
and the same member can be the one listing. Search runs against the opted-in community
inside a chosen radius, not against the web.

This is the POC implementation of [Radius Build Plan.dc.html](design/Radius%20Build%20Plan.dc.html):
Phases 00–03 built end to end, Phase 05 present as a working skeleton with the fee reading
zero.

**[poc.md](poc.md) is the single build record** — every feature that exists, everything still to
build, and the reasoning behind both. This README covers only how to run it.

```
services/     Java 21 · Spring Boot 3.3 · seven Maven modules
apps/web/     Next.js 15 · App Router · TypeScript · PWA ("Postcard", light + dark)
apps/mobile/  React Native · Expo · expo-router
infra/        Docker Compose, database bootstrap, API seed script
scripts/      The Docker-free local stack
design/       The build plan and the design system these were built from
```

---

## Run it without Docker

This is the path that works on a locked-down Windows machine: no Docker, no admin rights,
nothing installed system-wide. Two PowerShell scripts fetch portable copies of everything
into `%LOCALAPPDATA%\radius-dev` and run them from there.

```powershell
.\scripts\devstack.ps1 setup     # once: JDK 21, Maven, PostgreSQL 16 + PostGIS, Redis, Kafka, MinIO
.\scripts\devstack.ps1 up        # start the four backing services
.\scripts\services.ps1 build     # mvn package
.\scripts\services.ps1 up        # start the seven Spring Boot services
node infra\seed\seed.mjs         # members, listings and one live booking, through the API
```

`setup` downloads about 600 MB once and needs nothing already installed — not even a JDK.
Seven JVMs starting together on a laptop takes two to four minutes; `services.ps1 up` waits
and reports each one as it reports healthy.

Then the web app:

```powershell
cd apps\web; npm install; npm run dev
```

Open http://localhost:3000. **You do not need to sign in to look around** — the feed,
search and the map all read as a guest. Sign in as `+491700000001` when you want to book
something or review a check; there is no SMS provider, so the code comes back in the
response (and is in the user-service log). That number is also the default administrator —
override it with `RADIUS_ADMIN_PHONE`.

Useful while it runs:

```powershell
.\scripts\devstack.ps1 status    # what is listening
.\scripts\services.ps1 status    # per-service health
.\scripts\services.ps1 logs -Only booking
.\scripts\services.ps1 down; .\scripts\devstack.ps1 down
```

`devstack.ps1 reset` wipes the data and keeps the binaries. Deleting
`%LOCALAPPDATA%\radius-dev` removes every trace.

The ports are deliberately off the defaults (Postgres **5433**, Redis **6380**, MinIO
**9010**) so an existing Postgres or Redis on the machine is left alone. Set
`RADIUS_DEV_HOME` to put the stack somewhere else — keep it off OneDrive, because a synced
Postgres data directory corrupts.

### Run it with Docker

If Docker does work for you, the same stack is one command:

```bash
cd infra
docker compose --profile all up -d --build
node ../infra/seed/seed.mjs
```

`--profile infra` brings up only the backing services, which is what you want when running
the JVM side from an IDE.

| What | Without Docker | With Docker |
| --- | --- | --- |
| API gateway | http://localhost:8080 | http://localhost:8080 |
| Web | http://localhost:3000 | http://localhost:3000 |
| Swagger (per service) | `http://localhost:808x/swagger-ui.html` | same |
| Postgres | localhost:5433 · radius / radius | localhost:5432 |
| Redis | localhost:6380 | localhost:6379 |
| Kafka | localhost:9092 | localhost:9094 |
| MinIO console | http://localhost:9011 · radius / radius-secret | http://localhost:9001 |
| Kafka UI | — | http://localhost:8090 |
| Jaeger traces | — | http://localhost:16686 |

**Mobile:**

```bash
cd apps/mobile && npm install && npx expo install --fix && npx expo start
```

The Android emulator reaches the gateway on `10.0.2.2:8080`; the iOS simulator uses
`localhost`. On a real phone, set `EXPO_PUBLIC_API_URL` to your machine's LAN address.

**If Maven cannot resolve Spring Boot**, the machine's global `~/.m2/settings.xml` is
pointing at an internal mirror. Every script here already passes
`services/settings-central.xml`, which uses Maven Central over HTTPS and keeps your local
repository. By hand: `mvn -s settings-central.xml package`.

### Things a locked-down Windows box does that the scripts already work around

All of these were hit while getting this running, and each fix is commented where it lives:

| Symptom | Cause | What the scripts do |
| --- | --- | --- |
| Unpacking takes half an hour | `Expand-Archive` writes file by file, and on-access antivirus scans each one | Use `tar.exe`, which ships with Windows — 24 seconds instead of 28 minutes |
| `devstack up` hangs forever after Postgres starts | `pg_ctl \| Out-Null` — the server inherits the pipe handle, so the pipeline never ends | `Start-Process -Wait` |
| Kafka: `The input line is too long`, `'wmic' is not recognized` | Kafka's `.bat` launchers build the classpath jar by jar and shell out to a tool Microsoft removed | Launch the JVM directly with a wildcard classpath |
| `The connection attempt failed` while Postgres is clearly up | Endpoint security intercepts loopback and aborts the JDBC SSL handshake | `DB_OPTIONS=?sslmode=disable` for the local stack |
| Redis health `DOWN` while Redis works | The Windows Redis port reports `executable:C:\...` in `INFO`, and Spring parses `INFO` with `java.util.Properties`, which chokes on `\U` | `MANAGEMENT_HEALTH_REDIS_ENABLED=false` locally only |
| MinIO will not bind | Something else already holds port 9000 (a corporate tunnel, in this case) | MinIO runs on 9010/9011 |
| Services take minutes to start | Seven JVMs at once, each scanned on load | The health wait is patient rather than declaring failure |

---

## The services

Seven modules. Each owns its data and its tables; nothing reads another service's schema.

| Service | Port | Owns |
| --- | --- | --- |
| `api-gateway` | 8080 | Routing, JWT verification at the edge, Redis rate limiting, CORS |
| `user-service` | 8081 | Accounts, phone OTP, rotating refresh tokens, profiles, the tag vocabulary |
| `listing-service` | 8082 | Listings (all seven kinds), photos, availability, the nearby feed |
| `search-service` | 8083 | A read model of every live listing and member; sentence parsing; the map |
| `booking-service` | 8084 | The request state machine, the price breakdown, chat over STOMP |
| `payment-service` | 8085 | Fee configuration, payments, deposit holds, payouts |
| `notification-service` | 8086 | Fan-in of everything that wants to reach a human |

**Why there is no Eureka.** Services resolve each other by DNS name — the Compose service
name locally, a Kubernetes `Service` in a cluster. A discovery server would be one more
thing to run and one more thing to fail, for a lookup the platform already does.

### Talking to each other

Two mechanisms, chosen per case:

**Events, for anything that can be eventually consistent.** Six topics, versioned:
`radius.user.v1`, `radius.listing.v1`, `radius.request.v1`, `radius.payment.v1`,
`radius.notification.v1`. Producers write the event into their own `outbox_event` table in
the same transaction as the state change; a publisher drains that table to Kafka. No
service ever writes to Kafka and its database in two separate transactions — that is the
gap where "the event fired but the row rolled back" lives.

Delivery is at-least-once, so every consumer is idempotent: by upsert where the projection
allows it (search), by Redis guard where it does not (listing's calendar), and by a unique
constraint in the database where it is money (payment keys on the gateway reference).

**Synchronous calls, only where a stale answer would be wrong.** booking-service asks
listing-service for the rate before it prices a request, and payment-service for the fee.
Both calls sit behind a resilience4j circuit breaker; the fee falls back to zero, the rate
does not fall back at all, because a guess about money is worse than an error.

```
                   ┌────────────┐
   phone / web ───▶│  gateway   │──── JWT, rate limit, routing
                   └─────┬──────┘
        ┌────────────────┼────────────────┬─────────────────┐
        ▼                ▼                ▼                 ▼
    user-svc        listing-svc       booking-svc       payment-svc
        │                │             │      │              │
        └──── outbox ────┴─── Kafka ───┴──────┴──────────────┘
                          │         │
                    search-svc  notification-svc
                   (read model)   (inbox + push)
```

---

## Browsing, checks, and who may enter a home

**Browsing needs no account.** The feed, search, the map, a listing page and a professional's
page all read for a guest. The wall goes up at one place only: asking a neighbour for
something. Signing in returns you to the listing you were on.

Guests are not treated identically to members, for two reasons. They must send coordinates
(the API refuses to guess a location for an anonymous caller), and the points they get back
are **snapped to a 250 m grid** rather than the usual ~100 m — open browsing is also open
scraping, and a competitor should not be able to drain a precise map of your supply.

**Two checks, in order, before anyone comes to your flat.**

| Gate | What it means | What it unlocks |
| --- | --- | --- |
| `IDENTITY` | An admin has seen government ID | Applying as a professional |
| `PROFESSIONAL` | An admin has also seen trade paperwork | Publishing a `homeVisit` listing |

Both are enforced in `ListingService.requireCheckedProfessional` against a local projection
fed by `radius.user.v1` — so listing-service answers "may this person do that" without
calling user-service. A refusal **must** carry a reason; the member sees it and can fix it
and re-apply.

**The badge says "ID checked", never "verified".** The platform states what it looked at.
It takes no position on the quality of anyone's wiring, and the listing page says so in
words. That distinction is the difference between a factual claim and an implied warranty.

**Evidence is not kept.** Only object keys are stored, alongside a `purge_after` date set
when a decision is made (30 days). The images live under a restricted prefix and can be
destroyed on that date without losing the audit trail of who decided what, when.

**A professional's shop point is stored exactly** — unlike a member's home point, which is
fuzzed to ~100 m. It is a business address being published on purpose, and a neighbour
deciding whether to open the door deserves to see where someone trades from.

**Roles.** There were none; there is now `MEMBER` and `ADMIN`, carried in the access token.
Every admin endpoint calls `me.requireAdmin()`, which throws rather than returning a boolean
— a forgotten `if` on a boolean fails open, and this one cannot. Promote yourself by setting
`RADIUS_ADMIN_PHONE`; there is deliberately no "make me an admin" endpoint.

## Decisions worth knowing about

**Money is integer minor units, everywhere.** No float, no `BigDecimal` rounding surprises.
The fee is basis points (`250` = 2.5%) so the arithmetic stays exact, and the client never
computes a total — it renders the breakdown the server sends. That is what lets Phase 05
switch the fee on without touching a single component.

**Distance filtering happens in PostGIS.** `ST_DWithin` against a GiST index, never
`ST_Distance` in a `WHERE` clause and never distance computed in Java. The `geography`
column is `GENERATED ALWAYS AS` from lat/lon, so the ORM writes two doubles and Postgres
keeps the spatial index correct — no spatial mapping in Hibernate to go wrong.

**Home points are rounded to about 100 m before they are stored.** Enough for "2 km away",
not enough to find someone's front door.

**Feed pagination is keyset, not offset.** The cursor is `(distance, id)` from the last row
of the previous page: stable while new listings appear, and page 40 costs what page 1 does.

**Search is CQRS.** search-service owns no source data. Delete its tables, reset the
consumer groups to the beginning, and the index rebuilds itself from the topics.

**Search parsing is rules first.** A synonym dictionary over the tag vocabulary, a distance
regex, a date-word list. Instant, free, debuggable, and it handles the sentences people
actually type. Queries the rules cannot read are logged — that log is the list to grow
synonyms from. The LLM fallback exists behind an interface and is off by default.

**The request state machine lives in one class.** Legal transitions are a map on the
entity; who may ask for one is decided in `RequestService`. Every transition writes an
audit row. Nothing else in the codebase sets a status.

**Photos never touch the JVM.** The client asks for a pre-signed PUT, uploads straight to
the object store, then attaches the key.

**The tokens.** 15-minute access token, 30-day rotating refresh token with a server-side
row. Presenting a revoked refresh token revokes the whole family — that is a leak, not a
retry. Both clients serialise refreshes so five parallel 401s do not rotate five times and
log the member out.

### Where the POC is honestly a POC

- **The web app is client-rendered.** The build plan calls for server components rendering
  the feed. That needs the session in an HTTP-only cookie, not a bearer token in
  `localStorage`. Moving to cookie sessions plus a BFF route handler is the right next step
  and would not change any service.
- **Chat uses the in-memory STOMP broker.** Two replicas of booking-service would not see
  each other's subscribers. The relay swap (Redis or RabbitMQ) is configuration, not code.
- **Notification delivery logs instead of sending.** Both channels are behind one
  interface; FCM and a real SMS provider are a class each. A POC that pretends to send push
  notifications is worse than one that says it did not.
- **Phase 04 (trust) is not built.** Ratings, verification badges and completed counts are
  in the schema and render on cards; ID verification, two-sided reviews and the moderation
  queue are not.
- **The gateway payment is a sandbox.** No real gateway, so the reference is synthesised —
  but the webhook path, the idempotency and the deposit lifecycle are real.

---

## Tests

```powershell
.\scripts\services.ps1 test      # or: cd services; mvn -s settings-central.xml test
```

35 tests across seven classes, all green on a clean build. They cover the parts where a bug
costs someone money or a wasted trip: the request state machine, the fee arithmetic, the query
parser, the geo rounding, and the auth endpoints. Integration tests over Testcontainers are the
obvious next layer.

Two of them earned their keep immediately. The auth slice test caught the API answering an
unauthenticated call with **403 instead of 401** — which would have broken token refresh in
both clients, since a client only refreshes on a 401. The parser test caught `within 30
minutes` being read as **30 metres**, because Java's regex alternation is ordered and `m`
came before `min`.

## What has actually been run

Not "should work" — this is what was exercised against the running stack:

- Seven services healthy behind the gateway; six databases migrated by Flyway.
- The seed script signing six members up over OTP, publishing twelve listings, and opening
  one booking — all through the public API.
- The nearby feed returning twelve listings ordered by PostGIS distance, with owner names
  resolved from the Kafka-fed projection rather than a call to user-service.
- `I need someone who knows video editing and lives within 5 km` parsing to
  `{tags:[video-editing], kinds:[SKILL_FOR_HIRE], radiusKm:5}` and returning both the
  matching listing and three neighbours, from the CQRS read model.
- A booking going `SENT → ACCEPTED` with its audit trail, a chat message delivered, and the
  `RequestAccepted` event travelling outbox → Kafka → listing-service to block
  **2026-08-28 → 2026-08-29** on the ladder's calendar; the same event opening a payment in
  payment-service with the fee reading zero.
- The gateway's OTP rate limiter refusing a burst of sign-ins (the seed now backs off and
  retries, rather than the limit being loosened for a demo).
- Sign-up, profile editing, feed and Ask-mode search driven through the web UI in a browser,
  including the home point coming back rounded from `52.4999` to `52.5` — the ~100 m privacy
  rounding doing its job.
- A guest reading the feed, search and tags with no token, while `POST /api/listings`,
  `/api/listings/mine`, `/api/requests` and the admin queue all still answer 401.
- Both gates refusing in order — `id_check_first`, then `professional_check_first` — and the
  home-visit listing publishing only once an admin approved both, with the owner card then
  carrying ID checked / Professional / AC_SERVICE.
- A refusal with a blank reason rejected as `reason_required`; a member hitting the admin
  queue getting 403; the same listing returning a snapped point to a guest and the exact one
  to a signed-in member.
- The map showing 13 pins inside 5 km on an empty query, and the "Nothing is listed here
  yet" state for a query nobody offers.
- The dark theme switching to `#12110f` and back, with light the default on a fresh visit.

---

## What I would add next

Ordered by what buys the most for the least, given what is already here.

**1. Testcontainers integration tests for the event paths.** The most valuable missing
tests are not more unit tests — they are "accept a request, assert the listing calendar
blocked those days", running against a real Postgres and a real Kafka. Everything in this
design leans on events being delivered and consumed correctly, and nothing currently proves
that end to end.

**2. Redis as more than a cache.** It is already there for rate limits, OTP and idempotency.
Two more uses pay off immediately: a short-TTL cache on the feed query keyed by
`(cell, radius, kind)` — the feed is read constantly and changes rarely — and a distributed
lock around accept, so two owners' devices cannot both accept overlapping requests in the
same second. The database check catches it today; a lock stops the race before it starts.

**3. A dead-letter topic and a consumer-lag alert.** Right now a poison message retries
forever and a stalled consumer is silent. `radius.dlq.v1` plus an alert on lag turns both
into something a person finds out about.

**4. Kafka Streams for the trust numbers.** Rating averages and completed counts are
denormalised onto profiles and nothing updates them yet. A small streams job over
`radius.request.v1` is exactly the right shape: no batch job, no nightly recompute.

**5. Debezium instead of the polling outbox publisher.** The outbox pattern is right; the
500 ms poll is the crude version of it. CDC off the WAL removes the latency and the load.

**6. WebPush and FCM behind the existing `Channel` interface.** The inbox and the SSE stream
already work; this is the last mile to a phone that is not open.

**7. OpenTelemetry is wired but only traced.** Metrics are exposed on `/actuator/prometheus`
with nothing scraping them. Prometheus and Grafana in the infra profile, plus one dashboard
per service, would make the circuit breakers and consumer lag visible instead of theoretical.

**8. Rate limiting by member age.** Phase 04 calls for tighter limits on accounts younger
than seven days. The gateway already resolves the member; the bucket size just needs to
read account age.

**Things I would deliberately not add yet:** Elasticsearch (Postgres full-text plus PostGIS
is doing this job well at neighbourhood scale, and one fewer datastore is worth a lot), a
service mesh (six services and a gateway do not need one), and GraphQL (the clients are
happy with the REST shapes and the OpenAPI documents).

---

## Two decisions the build plan leaves open

Both are still open, and both want answering before Phase 05:

**Launch geography.** Radius is worthless at low density and good at high density. Pick one
or two neighbourhoods and saturate them. Everything in the schema is already scoped by point
and radius, so this is a go-to-market choice, not a technical one.

**Liability on damage.** A held deposit covers a scratched grinder and not a stolen one.
Decide before launch whether a rental above a threshold needs a signed handover checklist
with photos — a small feature in Phase 03, an expensive retrofit later.
