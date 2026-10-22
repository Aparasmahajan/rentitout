# 14 · Decision log (ADRs)

> **Audience:** tech lead and anyone about to undo a decision.
> These records reconstruct decisions already taken in the build plan, `poc.md` Part 2 and the
> code comments, in a lightweight ADR form. **New decisions get a new entry at the bottom**, and
> their *why* also goes into `poc.md` Part 2 (see `.claude/rules/build-record.md`).
>
> Status values: **Accepted** · **Accepted (POC)**, meaning right for now with a known exit ·
> **Proposed** · **Superseded**.

---

### ADR-001 · Microservices with a database per service

- **Status:** Accepted
- **Context:** Distinct domains (identity, supply, search, transactions, money, notifications)
  with different scaling and change rates. Money and identity need hard boundaries.
- **Decision:** Seven Spring Boot services, each owning one PostgreSQL database. No service reads
  another's tables.
- **Consequences:** Cross-service data arrives by event (eventually consistent) or by a small
  number of sync calls. Joins across domains become local projections. More moving parts than a
  monolith; mitigated by a shared `common` auto-config and one package shape.

### ADR-002 · Transactional outbox for every event

- **Status:** Accepted (POC: polling publisher)
- **Context:** Writing to the DB and to Kafka in two transactions leaves a gap where *"the event
  fired but the row rolled back"*, or the reverse.
- **Decision:** Producers insert into their own `outbox_event` in the business transaction. A
  scheduled publisher drains it to Kafka every 500 ms with `SKIP LOCKED`.
- **Consequences:** At-least-once delivery, so every consumer must be idempotent. Up to ~500 ms
  of added latency. **Exit:** Debezium CDC off the WAL (backlog).

### ADR-003 · Idempotency by the cheapest correct mechanism

- **Status:** Accepted
- **Decision:** An upsert where the projection allows it (search). A unique constraint wherever
  money or a one-off side effect is involved (payment, calendar block, notification). A Redis
  guard only where neither fits.
- **Consequences:** Most idempotency survives Redis loss. The Redis guard must be written
  *after* success (see issue E1).

### ADR-004 · Sync calls only where stale = wrong

- **Status:** Accepted
- **Decision:** booking → listing for the rate (**no fallback**: *"a guess about money is worse
  than an error"*) and booking → payment for the fee (falls back to zero), behind resilience4j.
- **Consequences:** Booking depends on listing-service being up to create a request. Everything
  else degrades gracefully.

### ADR-005 · No service-discovery server

- **Status:** Accepted
- **Decision:** Resolve services by DNS name (Compose service name locally, a Kubernetes
  `Service` in a cluster). No Eureka or Consul.
- **Consequences:** One fewer thing to run and fail, for a lookup the platform already does.

### ADR-006 · Money as integer minor units; fee in basis points; server-computed breakdown

- **Status:** Accepted
- **Decision:** `long *_minor` everywhere; fee = basis points with cap and minimum; only
  `PricingService` / `FeeConfig` compute; clients render the `Breakdown` they receive; the fee is
  stored on the payment row.
- **Consequences:** No rounding surprises. The fee can be switched on with no client change, and
  old requests are never re-priced. The currency moved EUR → INR (26 August 2026) without
  rescaling, since both use 1/100.

### ADR-007 · Distance in PostGIS with generated geography columns

- **Status:** Accepted
- **Decision:** `lat`/`lon` doubles mapped in JPA; `geography(Point,4326)` `GENERATED ALWAYS AS …
  STORED` with a GiST index; filter only with `ST_DWithin`; no spatial types in Hibernate.
- **Consequences:** The index is always right and nothing spatial can go wrong in the ORM.
  `ST_Distance` is allowed only in `SELECT` / `ORDER BY`.

### ADR-008 · Location privacy: 100 m for homes, 250 m for guests, exact for shops

- **Status:** Accepted
- **Decision:** Round home points before storage; snap points for unauthenticated callers;
  require guests to send coordinates; store a professional's shop point exactly.
- **Consequences:** "2 km away" stays accurate without revealing a front door. Open browsing
  does not hand a scraper a precise map. Businesses are findable on purpose.

### ADR-009 · Search as a CQRS read model with rules-first parsing

- **Status:** Accepted
- **Decision:** search-service owns no source data; it projects listings and members from
  Kafka. Sentence parsing is a synonym dictionary + regex + date words. The LLM fallback stays
  behind an interface, off by default.
- **Consequences:** The index is disposable and rebuildable. Parsing is instant, free and
  debuggable, and unparsed queries are logged as the list to grow synonyms from. Elasticsearch
  is **declined**: Postgres FTS + PostGIS is enough at neighbourhood scale.

### ADR-010 · Keyset pagination for the feed

- **Status:** Accepted
- **Decision:** Cursor = `(distance, id)` of the last row.
- **Consequences:** Stable while new listings appear; page 40 costs what page 1 does. Search
  still uses offset paging (bounded to 50 pages).

### ADR-011 · One owner per state machine

- **Status:** Accepted
- **Decision:** Legal transitions are a map on the entity (`BookingRequest.ALLOWED`,
  `Report.LEGAL`). Only one service class drives them. Every transition writes an audit row.
- **Consequences:** Illegal moves are impossible from anywhere else and are answered with
  `illegal_transition`. A hook flags booking status writes outside the entity. (Payment does not
  follow this yet; see issue S6.)

### ADR-012 · Authorization throws; refusals carry reasons; no "make admin" endpoint

- **Status:** Accepted
- **Decision:** `me.requireAdmin()` throws 403. Rejections and dismissals require a reason.
  Admin is granted only by `RADIUS_ADMIN_PHONE`.
- **Consequences:** A forgotten check fails closed. Members can understand and fix a refusal.
  There is no API path to escalation.

### ADR-013 · Manual, two-step trust checks; "ID checked", not "verified"

- **Status:** Accepted (POC: manual review instead of a provider)
- **Decision:** `IDENTITY`, then `PROFESSIONAL`, both reviewed by an admin. Evidence is kept as
  object keys with a 30-day purge date. Copy states what was checked.
- **Consequences:** No vendor cost or integration yet. A factual claim rather than an implied
  warranty. **Exit:** a `VerificationProvider` behind the same flow (Phase 04).

### ADR-014 · Guests can browse; sign-in gates actions, not pages

- **Status:** Accepted
- **Decision:** Feed, search, map, listing and profile pages are readable without a token.
  Sign-in is asked for at the moment of asking a neighbour for something, and returns to the page.
- **Consequences:** Real supply is visible before commitment. Requires guest coarsening
  (ADR-008) and per-path public lists at the gateway and in each service.

### ADR-015 · Short access token, rotating refresh with reuse detection; 401 not 403

- **Status:** Accepted
- **Decision:** 15-minute access, 30-day rotating refresh with a server-side row; reuse of a
  revoked refresh token revokes the family. Unauthenticated is always 401. Clients serialise
  refreshes.
- **Consequences:** A leaked refresh token is detected on its first reuse. Five parallel 401s do
  not log the member out.

### ADR-016 · Photos never touch the JVM

- **Status:** Accepted
- **Decision:** Pre-signed PUT to the object store, then attach the key.
- **Consequences:** No upload bandwidth or memory in services. Ownership of keys must be checked
  on attach (issue S7), and sensitive uploads need their own prefix and policy (issue S2).

### ADR-017 · Client-rendered web app with bearer tokens

- **Status:** Accepted (POC)
- **Context:** The build plan asks for server components rendering the feed.
- **Decision:** Ship client-rendered pages with tokens in `localStorage` first.
- **Consequences:** Simple and fast to build; XSS exposure of tokens; no SSR for SEO. **Exit:**
  HTTP-only cookie session + BFF route handlers, with no service change.

### ADR-018 · In-memory STOMP broker and per-instance SSE

- **Status:** Accepted (POC)
- **Decision:** Spring's simple broker for chat; `SseEmitter` map for notifications.
- **Consequences:** Correct only with one replica. **Exit:** broker relay (Redis/RabbitMQ) and
  pub/sub fan-out. These are configuration changes, not code rewrites.

### ADR-019 · Simulate external providers rather than pretend

- **Status:** Accepted (POC)
- **Decision:** OTP returned in dev, push and SMS log instead of sending, payment gateway is a
  sandbox. All behind interfaces.
- **Consequences:** *"A POC that pretends to send push notifications is worse than one that says
  it did not."* The webhook path, idempotency and deposit lifecycle are still real.

### ADR-020 · Tile-free map

- **Status:** Accepted (POC)
- **Decision:** A hand-built projection around the viewer instead of a tile provider.
- **Consequences:** No key, no cost, no rate limit; no streets either. **Exit:** MapLibre behind
  the same props and endpoint.

### ADR-021 · No paid promotion

- **Status:** Accepted (product)
- **Decision:** Ranking is distance and relevance only.
- **Consequences:** *"The ranking is the product."* Revenue must come from the transaction fee
  (Phase 05), not placement.

### ADR-022 · Deliberately not adopted

- **Status:** Accepted
- **Decision:** No Elasticsearch, no service mesh, no GraphQL, no Lombok.
- **Consequences:** Fewer datastores and moving parts. The clients are fine with the REST shapes
  and OpenAPI. Revisit only with a measured need.

### ADR-023 · MinIO from Pigsty's community rebuild

- **Status:** Accepted (POC)
- **Date:** 15 October 2026
- **Context:** `minio/minio` and `minio/mc` can no longer be pulled. Docker Hub answers
  "repository does not exist" and quay.io answers 401, so `docker compose up` failed on any
  machine without a cached copy.
- **Decision:** Use `pgsty/minio` and `pgsty/mc`, pinned to release tags. They are Pigsty's
  rebuilds of the same AGPL server and client, so the entrypoint, `mc` healthcheck and bucket
  setup are unchanged.
- **Alternatives:** `bitnamilegacy/minio` (frozen, no updates); SeaweedFS or Garage (real S3
  servers, but different setup and no `mc anonymous set download` equivalent to drop in).
- **Consequences:** No code change; presign, upload and public download verified on 15 October
  2026. The exit is any S3-compatible store, since listing-service only speaks S3.

### ADR-024 · Deleting a listing is final

- **Status:** Accepted
- **Date:** 15 October 2026
- **Context:** S5's guard refuses `pause`/`resume` on an `UNLISTED` listing so a member cannot
  undo a moderator's takedown. An owner's own delete also sets `UNLISTED`, so it became final too.
- **Decision:** Keep it. Deleting is deleting; an owner who wants a listing back lists it again.
- **Alternatives:** An `unlisted_by` column (owner or moderator) in a new migration, so owners
  could restore their own. Rejected for now: a restore flow nobody asked for, and a second way
  for a takedown to come back if the column is ever wrong.
- **Consequences:** `listing_unlisted` (409) covers both cases. If owners ask for "undo delete",
  add the column then; nothing in the data model blocks it.

---

## Open decisions (need an owner)

| # | Question | Notes |
| --- | --- | --- |
| OD-1 | **Launch geography.** Which one or two neighbourhoods first? | Pune (prototype) vs Berlin (seed). Changes the seed, the fallback point and the phone format. See [01](01-product-vision.md#two-business-decisions-still-open) |
| OD-2 | **Liability on damage.** A handover checklist with photos above a value threshold? | Cheap now, expensive later |
| OD-3 | Offers: a `COUNTERED` state and `proposed_amount_minor` on the existing request machine, not a separate negotiation table | Recommended in `poc.md` Part 5 |
| OD-4 | Trust numbers (`profile.rating_avg`, `completed_count`) via a Kafka Streams job over `radius.request.v1` | Recommended; parked in Block A |

## Template for a new decision

```markdown
### ADR-0NN · <short title>

- **Status:** Proposed | Accepted | Accepted (POC) | Superseded by ADR-0MM
- **Date:** <14 October 2026>
- **Context:** what forces the decision, including constraints and numbers
- **Decision:** what we will do, in one or two sentences
- **Alternatives:** what we rejected and why
- **Consequences:** what gets easier, what gets harder, and the exit if this is a POC choice
```
