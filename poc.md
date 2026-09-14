# Radius — the complete POC and build record

**One document.** What Radius is, everything that is built, everything still to build, and the
reasoning behind both. This is the file to keep — `README.md` covers only how to run it.

Radius is a neighbourhood index of people, skills and things. A member can rent a ladder, buy a
printer, hire an electrician, book two hours of video editing, or post what they need — and the
same member can be the one listing. Search runs against the opted-in community inside a chosen
radius, not against the web.

Implements [Radius Build Plan.dc.html](design/Radius%20Build%20Plan.dc.html).

| | |
| --- | --- |
| Branch | `dev` |
| Phases complete | 00, 01, 02, 03 |
| Phases partial | 04 (verification + moderation, ~⅔), 05 (ledger works, gateway is a sandbox) |
| Services | 7 Spring Boot modules + `common`, all healthy behind the gateway |
| Clients | Next.js web (complete), Expo mobile (no map screen, no comments or ratings) |
| Tests | 8 classes / 47 tests, green on a clean build. **No integration tests.** |
| Currency | INR (₹), `en-IN` grouping |
| Last verified | 27 August 2026 — clean build, 47 tests green, `V4` applied, all services UP, and Block A exercised end to end over the public API (34 checks) |

Legend used throughout: `[x]` built · `[~]` partial · `[ ]` not started

---

# Part 1 — What is built

## Architecture

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

| Service | Port | Owns |
| --- | --- | --- |
| `api-gateway` | 8080 | Routing, JWT verification at the edge, Redis rate limiting, CORS |
| `user-service` | 8081 | Accounts, phone OTP, rotating refresh tokens, profiles, tag vocabulary |
| `listing-service` | 8082 | Listings (all seven kinds), photos, availability, the nearby feed |
| `search-service` | 8083 | Read model of every live listing and member; sentence parsing; the map |
| `booking-service` | 8084 | Request state machine, price breakdown, chat over STOMP |
| `payment-service` | 8085 | Fee configuration, payments, deposit holds, payouts |
| `notification-service` | 8086 | Fan-in of everything that wants to reach a human |

```
services/     Java 21 · Spring Boot 3.3 · 7 service modules + common + parent
apps/web/     Next.js 15 · App Router · TypeScript · PWA ("Postcard", light + dark)
apps/mobile/  React Native · Expo · expo-router
infra/        Docker Compose, database bootstrap, API seed script
scripts/      The Docker-free local stack
design/       The build plan and the design system these were built from
```

Seven services, each owning its data and its tables. Nothing reads another service's schema.
No Eureka — services resolve by DNS name, because a discovery server is one more thing to run
and one more thing to fail, for a lookup the platform already does.

## Phase 00 — Foundations `[x]`

- [x] `user`, `profile`, `tag`, `profile_tag` tables, Flyway-migrated. No `ddl-auto` anywhere.
- [x] Phone OTP sign-up; 15-minute JWT access token; 30-day rotating refresh token with a
      server-side row. Presenting a revoked refresh token revokes the whole family — that is a
      leak, not a retry.
- [x] `GET /me`, `PATCH /me/profile`, tag add/remove across four relations
      (has · teaches · needs · enjoys)
- [x] Home point as `geography(Point,4326)` `GENERATED ALWAYS AS` from lat/lon, rounded to
      ~100 m before saving — `common/support/Geo.java`
- [x] Seed script signing six members up over the public API, with OTP back-off when the
      gateway's rate limiter refuses a burst
- [x] Slice tests on auth. `AuthControllerTest` caught the API answering an unauthenticated
      call **403 instead of 401**, which would have broken refresh in both clients.

## Phase 01 — Listings and the nearby feed `[x]`

- [x] One `listing` table covering all seven kinds: `RENT_ITEM`, `SELL_ITEM`, `TRADE_SERVICE`,
      `SKILL_FOR_HIRE`, `TEACHING`, `SPACE_OR_VEHICLE`, `OPEN_NEED`
- [x] `listing_photo`, `listing_tag`, `availability_rule`, `availability_block`
- [x] `search_vector` maintained by trigger from title, description and tag labels, GIN-indexed;
      GiST index on `listing.point` and `profile.home_point`
- [x] Create, update, get, my-listings, pause/resume
- [x] Photos by pre-signed PUT straight to the object store, then attach the key — no image
      bytes ever enter the JVM
- [x] `GET /feed` — `ST_DWithin` against the GiST index, keyset pagination on `(distance, id)`,
      distance ascending
- [x] Web: three-step create flow, dashboard with live/paused switch, feed with category chips,
      listing and profile detail pages

## Phase 02 — Search, both ways `[x]`

- [x] `POST /api/search` takes either a sentence in `q` or structured filters, and echoes the
      extraction back as `{ terms, radiusKm, day, kinds }` for removable chips
- [x] Rules-first parsing: synonym dictionary over the tag vocabulary, a `within N km` regex,
      a date-word list
- [x] `GET /api/search/map?bbox` — pins below 200 results, server-side grid clustering above
- [x] Saved searches with a daily digest at 08:00, publishing a notification event rather than
      sending anything itself
- [x] CQRS — search-service owns no source data. Delete its tables, reset the consumer groups,
      and the read model rebuilds from the topics.
- [x] LLM fallback behind an interface, **off by default**; unparsed queries are logged as the
      list to grow synonyms from
- [x] `QueryParserTest` caught `within 30 minutes` parsing as 30 **metres** — Java's regex
      alternation is ordered and `m` came before `min`

## Phase 03 — Requests, booking and chat `[x]`

- [x] `request` and `message` tables
- [x] `SENT → ACCEPTED → IN_PROGRESS → COMPLETED`, with `DECLINED`, `CANCELLED`, `EXPIRED` as
      exits. Legal transitions are a map on the entity; who may ask for one is decided in
      `RequestService`. Every transition writes an audit row. **Nothing else in the codebase
      sets a status.**
- [x] `SENT` expires after 48 hours on a scheduled sweep
- [x] Accepting writes an `availability_block` over the booked days, via
      `RequestAccepted` → outbox → Kafka → listing-service
- [x] Chat over STOMP at `/ws/threads/{requestId}`, REST history, unread counts
- [x] Server computes the breakdown — rate × units, deposit, fee line. The client renders what
      it is sent and never does the arithmetic.

## Phase 04 — Trust `[~]` (roughly one third)

Built, as a **manual admin-reviewed check** rather than the third-party provider the plan calls
for:

- [x] Two gates in order: `IDENTITY` (an admin has seen government ID) unlocks applying as a
      professional; `PROFESSIONAL` (trade paperwork too) unlocks publishing a `homeVisit`
      listing. Enforced in `ListingService.requireCheckedProfessional` against a local
      projection fed by `radius.user.v1` — listing-service never calls user-service to ask.
- [x] Admin queue with claim / approve / reject, where **a refusal must carry a reason**
- [x] Roles `MEMBER` and `ADMIN` in the access token. Every admin endpoint calls
      `me.requireAdmin()`, which **throws rather than returning a boolean** — a forgotten `if`
      on a boolean fails open, and this one cannot. There is deliberately no
      "make me an admin" endpoint.
- [x] Evidence is not kept: only object keys, with a `purge_after` date set on decision (30 days)
- [x] The badge reads **"ID checked", never "verified"** — the platform states what it looked
      at and takes no position on the quality of anyone's wiring
- [x] A professional's shop point is stored **exactly**, unlike a member's fuzzed home point —
      it is a business address published on purpose

Built since, as Block A — comments, ratings, reports, comment bans and new-account rate limits.
See *Block A, built* below. **Still not built:** two-sided post-transaction reviews (the ratings
here are the open Amazon-style kind), and the profile-level trust numbers.

## Block A — moderation plumbing `[x]`

All of it lives in listing-service, because every one of these things hangs off a listing and
splitting them across services would mean a distributed transaction to delete one.
Migration `V4__comments_ratings_reports.sql`.

- [x] **Comments**, threaded one level. A reply to a reply attaches to its parent rather than
      being refused — the member's intent is obvious. **Soft delete**: the row stays, the body
      stops being served, so the evidence survives the removal. Author edits and deletes their
      own; the listing owner may also delete on their own listing, because they carry the
      reputational cost of what sits under it.
- [x] **Ratings**, one per member per listing on a unique index — changing your mind edits the
      row. Anyone may rate; a rating from someone who completed a booking is badged
      **"verified booking"** and sorts first, which is Amazon's middle path: useful on day one
      without being worthless. Star histogram, not just an average. `rating_avg` and
      `rating_count` are **recomputed** onto `listing` from the rows on every write, never
      incremented — an increment gets one delete wrong and is quietly wrong forever.
- [x] **Who removed a rating decides whether it can come back.** Running it caught the
      difference: the unique index means a soft-deleted row still occupies the member's one
      slot, so "remove mine" was quietly permanent — they could never rate that listing again.
      A self-removal now restores the row with the new content; a *moderated* removal still
      refuses, because otherwise deleting your own rating would just be a step in re-posting it.
- [x] The verified badge reads a local `completed_booking` projection of `RequestCompleted`, so
      listing-service never calls booking-service to ask, and the badge survives it being down.
- [x] **Reports** — one table for all three target kinds, `(target_type, target_id)`.
      `OPEN → REVIEWING → ACTIONED | DISMISSED`, legal transitions on the entity exactly as the
      booking machine does it, and **nothing outside `moveTo` sets a state**. A unique index on
      `(reporter, target_type, target_id)` makes reporting the same thing twice one report.
      Dismissing **requires a reason**. Removing content closes every open report about it in
      the same transaction.
- [x] **Comment bans that expire** — `comment_ban.banned_until` is a timestamp, not a boolean,
      so an admin sets a duration and the ban lifts itself. Checked at post time, and the member
      is told the date.
- [x] **New-account rate limits** — `PostingLimiter`, per member in Redis: 5 comments and
      3 ratings an hour under seven days old, 30 and 15 after. The gateway's limiter is per IP
      and cannot tell a new account from an old one; this one can, because listing-service now
      mirrors `registered_at` from `UserRegistered`.
      **It fails closed**: a member with no projection row yet is treated as new, not
      established. Running it caught the opposite reading — for the second or two before the
      registration event lands, a brand-new account looked established and got the loose bucket,
      which is a hole a spammer walks straight through. A row that exists with a null date is a
      different thing: that member predates the column, and is established.
- [x] **Admin surface** under `/api/moderation/**`, not `/api/admin/**` — that prefix is already
      routed to user-service for the verification queue, and gateway routes match in order.
      Every method opens with `me.requireAdmin()`.
- [x] **Web**: comment thread and ratings panel on `/l/[id]`, a quiet report control on the
      listing and on every comment and rating, and `/admin` grown from one queue into three
      tabs — Checks · Reports · Restrictions — with the reported content rendered inline.
- [x] 14 unit tests over the report machine, comment soft-delete, the verified-booking rule,
      self-removal versus moderation, and ban expiry — green, and the whole suite with them.

**Deliberately not done in Block A:** `profile.rating_avg` and `profile.completed_count` in
user-service. They are member-level numbers aggregated across everything a member has done, and
the backlog already calls for a Kafka Streams job over `radius.request.v1` to own them. Writing
them a second way here would mean building the writer that job then has to replace — so the
cards that read those two columns are still stale by construction. The listing-level numbers
this block adds are real.

## Phase 05 — Money `[~]` (ledger real, gateway simulated)

- [x] `fee_config`, `payment`, `deposit_hold`, `payout` tables
- [x] `FeeConfig` with percentage, cap and per-category override, read at request time and
      **stored on the payment row**, so changing the fee never re-prices an old request.
      It reads zero today. No code path assumes a non-zero fee.
- [x] A payment opens when a request is accepted, driven by `RequestAccepted`; the unique index
      on `request_id` makes a redelivered event harmless
- [x] `POST /api/webhooks/gateway`, idempotent on `gateway_ref`, treated as the source of truth
      over local optimistic state
- [x] Deposit authorised on acceptance, released on completion, held back if a claim is open
- [x] `closePeriod` rolls settled payments into one payout per owner per month, idempotent on
      the `(owner, period)` unique index
- [x] `GET /api/payments/mine`, `GET /api/payments/payouts`, `GET /api/fees/quote`

**Not built:** `POST /payments/intent`, a real gateway, the claim flow, a payout schedule,
per-request statements, the dashboard payouts view.

## Cross-cutting `[x]`

- [x] **Transactional outbox.** Producers write the event into their own `outbox_event` table in
      the same transaction as the state change; a publisher drains it to Kafka every 500 ms and
      prunes published rows after a week. No service writes to Kafka and its database in two
      separate transactions — that is the gap where "the event fired but the row rolled back"
      lives.
- [x] **Idempotent consumers**, each by the right mechanism: upsert where the projection allows
      it (search), a Redis guard where it does not (listing's calendar), a unique constraint
      where it is money (payment, on the gateway reference).
- [x] **Synchronous calls only where a stale answer would be wrong** — booking asks listing for
      the rate and payment for the fee, both behind a resilience4j circuit breaker. The fee
      falls back to zero; **the rate does not fall back at all**, because a guess about money is
      worse than an error.
- [x] **Guest browsing.** Feed, search, map, listing and professional pages read with no token.
      Guests must send coordinates, and get points snapped to a **250 m grid** instead of the
      usual ~100 m — open browsing is also open scraping.
- [x] **Gateway.** JWT at the edge, Redis-backed rate limiting shared across replicas, OTP on
      its own much tighter bucket, CORS.
- [x] **Notifications.** Inbox, unread counts, SSE stream, `device_token` table, both delivery
      channels behind one `Channel` interface.
- [x] **Web** covers every route in the plan's sketch plus `/admin`, `/pro/[id]`,
      `/me/verification`, `/me/professional`. Installable PWA, light and dark.
- [x] **Docker-free Windows stack** — two PowerShell scripts fetch portable JDK 21, Maven,
      PostgreSQL 16 + PostGIS, Redis, Kafka and MinIO into `%LOCALAPPDATA%\radius-dev`, no admin
      rights, nothing installed system-wide.

## v2 changes, requested after the app was built (26 August 2026)

- [x] **Currency → INR.** Was euro (`€`, `en-IE`). Now `₹` with `en-IN`, which matters because
      Indian digit grouping differs — one lakh is `₹1,00,000`, and `en-IE` renders it
      `₹100,000`. Changed in web + mobile `format.ts`, the `(€)` labels in both create flows,
      four Java defaults (`Listing`, `BookingRequest`, `Payment`, `Payout`), two literals in
      `PaymentService` and `RequestEventConsumer`, and **four Flyway migrations**
      (`listing V3`, `booking V2`, `payment V2`, `search V2`) that flip the column default and
      rewrite existing rows — including the search projection, which would otherwise have kept
      rendering € on the map until listings republished.
      **No amounts rescaled:** paise and cents are both 1/100, so stored integers stay correct.
      *Verified: the live API returns `currency=INR` for all 13 listings.*
- [x] **Map zoom.** `RadiusMap` is deliberately tile-free — no provider, no key, no per-view
      cost — and the search radius was previously the only scale control. Now `+` / `−` / fit
      buttons, non-passive scroll wheel (so the page does not scroll underneath), and
      two-finger pinch. The coupling asked for is preserved: **a small radius opens zoomed in,
      a large radius opens pulled back**, and zoom resets when the radius changes because a new
      radius is a new question. Distance rings scale with zoom so they stay truthful markers,
      and the legend changed from `13 within 5 km` to `7 in view · 6 outside · 800 m across`,
      because the old text became a lie the moment you zoomed.

## What has actually been run

Not "should work" — exercised against the running stack:

- Seven services healthy behind the gateway; six databases migrated by Flyway
- The seed script signing six members up over OTP, publishing twelve listings and opening one
  booking, all through the public API
- The nearby feed returning twelve listings ordered by PostGIS distance, with owner names from
  the Kafka-fed projection rather than a call to user-service
- `I need someone who knows video editing and lives within 5 km` parsing to
  `{tags:[video-editing], kinds:[SKILL_FOR_HIRE], radiusKm:5}` and returning both the matching
  listing and three neighbours, from the CQRS read model
- A booking going `SENT → ACCEPTED` with its audit trail, a chat message delivered, and the
  `RequestAccepted` event travelling outbox → Kafka → listing-service to block
  **2026-08-28 → 2026-08-29** on the ladder's calendar — the same event opening a payment with
  the fee reading zero
- The gateway's OTP rate limiter refusing a burst of sign-ins (the seed backs off and retries,
  rather than the limit being loosened for a demo)
- The home point coming back rounded from `52.4999` to `52.5` — the ~100 m privacy rounding
  doing its job
- A guest reading feed, search and tags with no token, while `POST /api/listings`,
  `/api/listings/mine`, `/api/requests` and the admin queue all answer 401
- Both gates refusing in order — `id_check_first`, then `professional_check_first` — and the
  home-visit listing publishing only once an admin approved both
- A refusal with a blank reason rejected as `reason_required`; a member hitting the admin queue
  getting 403; the same listing returning a snapped point to a guest and the exact one to a
  member
- The map showing 13 pins inside 5 km, and the "Nothing is listed here yet" empty state
- The dark theme switching to `#12110f` and back, light default on a fresh visit

Block A, exercised over the gateway on 27 August 2026 — 34 checks, all passing:

- A guest reading a comment thread with no token while `POST` on the same path answers 401, and
  being handed `canEdit: false, canDelete: false` rather than deciding that in the browser
- A reply to a reply attaching to its parent instead of nesting a third level
- An author editing their own comment; a different member refused with 403 on the same one
- Rating your own listing refused; rating twice editing the one row; the summary, the histogram
  and the denormalised `listing.rating_avg` all agreeing at `5.0 / 1`
- A reason off the fixed list refused; the same person reporting the same comment twice getting
  one report back, not two; a member reading the moderation queue getting 403
- The queue rendering the reported comment inline; a blank dismissal refused as
  `reason_required`; removing the comment closing its open report as `ACTIONED` in the same
  transaction; the closed report refusing a second decision with 409
- The removed comment surviving as a tombstone — row present, body `null`
- A three-day ban refusing a comment with the expiry date in the message, and the member
  posting again the moment it was lifted
- A minutes-old account refused on its sixth comment of the hour, while an established one is
  not — the fail-closed reading of an absent projection row
- A booking taken `SENT → ACCEPTED → IN_PROGRESS → COMPLETED`, the `RequestCompleted` event
  reaching the `completed_booking` projection within half a second, and the rating written after
  it coming back **badged verified** — the whole point of the badge, end to end
- A member removing their own rating (the average going back to `null`, not zero) and then
  rating that listing again — the restore path, which is the thing running it caught

---

# Part 2 — Design decisions worth knowing

**Money is integer minor units, everywhere.** No float, no `BigDecimal` rounding surprises. The
fee is basis points (`250` = 2.5%) so the arithmetic stays exact, and the client never computes
a total — it renders the breakdown the server sends. That is what lets Phase 05 switch the fee
on without touching a single component.

**Distance filtering happens in PostGIS.** `ST_DWithin` against a GiST index, never
`ST_Distance` in a `WHERE` clause and never distance computed in Java. The `geography` column is
`GENERATED ALWAYS AS` from lat/lon, so the ORM writes two doubles and Postgres keeps the spatial
index correct — no spatial mapping in Hibernate to go wrong.

**Home points are rounded to ~100 m before storage.** Enough for "2 km away", not enough to find
someone's front door. Guests get 250 m.

**Feed pagination is keyset, not offset.** The cursor is `(distance, id)` from the last row:
stable while new listings appear, and page 40 costs what page 1 does.

**Search is CQRS.** search-service owns no source data.

**Search parsing is rules first.** Instant, free, debuggable, and it handles the sentences people
actually type.

**The request state machine lives in one class.** Every transition writes an audit row.

**Photos never touch the JVM.** Pre-signed PUT, then attach the key.

**The tokens.** 15-minute access, 30-day rotating refresh with a server-side row. Both clients
serialise refreshes so five parallel 401s do not rotate five times and log the member out.

## Where the POC is honestly a POC

- **The web app is client-rendered.** The plan calls for server components rendering the feed.
  That needs the session in an HTTP-only cookie, not a bearer token in `localStorage`.
- **Chat uses the in-memory STOMP broker.** Two replicas of booking-service would not see each
  other's subscribers. The relay swap is configuration, not code.
- **Notification delivery logs instead of sending.** A POC that pretends to send push
  notifications is worse than one that says it did not.
- **`profile.rating_avg` and `profile.completed_count` render on member cards from columns
  nothing writes.** Stale by construction since Phase 01, and still stale — Block A writes the
  *listing* rating columns, not these two. The Kafka Streams job in the backlog is their owner.
- **`npm run build` fails on `/sign-in`.** Pre-existing and unrelated to Block A: the page calls
  `useSearchParams()` without a Suspense boundary, which only bites when Next prerenders it.
  `npm run dev` — the way this POC is run — is unaffected, so it went unnoticed. A two-line fix
  whenever the web app needs a production build.
- **`BookingSheet` asks for `className="scrim"`, and the stylesheet only defines `.scrim-bg`.**
  The booking modal has no backdrop. One word.
- **The gateway payment is a sandbox.** The reference is synthesised — but the webhook path,
  the idempotency and the deposit lifecycle around it are real.
- **Nothing proves the event paths end to end.** 35 unit tests cover the state machine, the fee
  arithmetic, the query parser, the geo rounding and the auth endpoints. The Testcontainers
  layer that would assert "accept a request → the calendar blocked those days" does not exist.
- **`poc/` is five empty directories.** Leftover scaffolding, zero files, untracked by git
  (git cannot represent empty directories). Delete with `rmdir /s /q poc` — it currently
  refuses, held by an open handle, most likely the VS Code Java language server.

---

# Part 3 — What to develop next

Ordered. Blocks A–E are the changes requested after the app was built; the backlog after them
predates that.

## Block A — moderation plumbing `[x]`

Built and exercised — see *Block A* in Part 1 for what landed and why. What is left of it:

- [ ] `profile.rating_avg` / `completed_count` — parked on purpose, see the note in Part 1
- [ ] The seed stops at `ACCEPTED`, so a fresh database has no verified rating to look at. Worth
      a seed step that completes one booking and rates it, or the badge is invisible in a demo.
- [ ] Mobile has none of it: the thread and the ratings panel are web-only so far

## Block B — the admin panel  ← start here

`/admin` now has three tabs. These are what it still lacks to be a console.

- [x] **Reports** — the queue across listings, comments and ratings, reported content shown
      inline so the decision does not need a second tab
- [x] **Comments** — remove a reported comment, restrict its author, both from the report
- [x] **Verifications** — the existing queue, unchanged
- [ ] **Listings** — every listing, searchable, filter by kind / status / owner. Unlisting one
      already exists (`POST /api/moderation/listings/{id}/unlist`); the browse-and-search
      surface does not.
- [ ] **Members** — search, see checks and bans, restrict commenting, suspend. Restricting works
      from a report today, but there is no way to look a member up and act on them directly.
- [ ] Every endpoint calls `me.requireAdmin()`. Keep that pattern.

## Block C — accounts

- [ ] **Email + password login** alongside phone OTP, both landing on the same account.
      `user_account.email` and `password_hash` **already exist from Phase 00 and are unused** —
      this is a flow, not a migration.
- [ ] BCrypt or Argon2; password reset over email
- [ ] Rotating-refresh-token machinery unchanged behind both entry points
- [ ] **Username** — unique, immutable once set, shown on comments and ratings instead of the
      display name
- [ ] Web + mobile sign-in screens gain a second mode

## Block D — listing lifecycle and sale parity

- [ ] **`DRAFT`** added to `Listing.Status` (`LIVE · PAUSED · UNLISTED` today). Invisible to
      everyone but the owner, promoted to `LIVE` on publish.
- [ ] Dashboard shows draft / active / inactive as three explicit states
- [ ] **Sale parity.** `SELL_ITEM` and `buy_price_minor` already work end to end — model, feed,
      search projection, listing page. Missing is UI: the sale path is second-class in the
      create flow and there is no **Buy** call to action matching **Request to rent**.
      A UI job, not a data-model one.

## Block E — from OLX

- [ ] **Make Offer, beside Chat.** The highest-value item here. Do **not** build a negotiation
      table — add `proposed_amount_minor` and a `COUNTERED` state to the existing request
      machine, and the audit trail, chat thread and breakdown all work unchanged.
- [ ] **Favourites.** Saved *listings*, not just saved searches. The notification service and
      SSE stream for a price-drop alert already exist.
- [ ] **Pre-booking enquiry thread.** Chat is gated behind a booking request today, so there is
      no way to ask "does the ladder fold to 1.8 m?" without asking to rent it.
- [ ] **Share link.** How supply actually spreads in a neighbourhood — a forward to a building
      group.
- [ ] Similar listings under the listing page (the read model already ranks by distance and
      tags — one existing query)
- [ ] Sold / Rented badge instead of deleting — keeps the neighbourhood looking active
- [ ] "Member since" and response rate on the profile

## Backlog predating v2

- [ ] **Readability pass** — type scale, WCAG AA contrast in both themes, 44 px tap targets,
      and the `0.66rem` uppercase labels in the map and cards brought up to a readable size
- [ ] **Testcontainers integration tests for the event paths.** The largest gap between what the
      code claims and what is proven. Not more unit tests — "accept a request, assert the
      listing calendar blocked those days" against a real Postgres and a real Kafka.
- [ ] **Kafka Streams for the trust numbers** — overlaps A2; do it once, there
- [ ] Dead-letter topic `radius.dlq.v1` and a consumer-lag alert. Today a poison message retries
      forever and a stalled consumer is silent.
- [ ] Redis feed cache keyed by `(cell, radius, kind)`, and a distributed lock around accept so
      two devices cannot both accept overlapping requests in the same second
- [ ] Finish Phase 05 — `POST /payments/intent`, deposit claim flow, scheduled payout close,
      per-request statements, dashboard payouts view
- [ ] Debezium instead of the 500 ms polling outbox publisher
- [ ] WebPush / FCM behind the existing `Channel` interface
- [ ] Cookie sessions + server components on the web
- [ ] Mobile map screen against the same `GET /api/search/map`
- [ ] Delete the empty `poc/` tree

**Deliberately not building:** Elasticsearch (Postgres full-text plus PostGIS does this job well
at neighbourhood scale, and one fewer datastore is worth a lot), a service mesh (six services and
a gateway do not need one), GraphQL (the clients are happy with the REST shapes).

---

# Part 4 — What OLX does that is worth copying

Checked against OLX India's live product and help centre. Their category mix is not ours, but
the ad-detail page is the most-tested second-hand-marketplace screen in the country.

**Worth adding**

1. **Make Offer, next to Chat.** OLX puts *Chat* and *Make Offer* side by side at the bottom of
   every ad. Best fit for Radius by far, because the machinery already exists — a request
   carries a server-computed `amount_minor` and a breakdown. An offer is that same request with
   a buyer-proposed amount and an owner counter. Haggling is the norm in this market; a fixed
   price with no counter-offer button loses the deal to WhatsApp.
2. **Favourites / wishlist.** Account-linked saved ads.
3. **Call and Chat as two distinct CTAs.** OLX exposes both. Radius has chat only, gated behind
   a booking request — a real gap.
4. **Share an ad.** For a neighbourhood product, a WhatsApp forward to a building group is how
   supply actually spreads.

**Worth taking with modification**

5. **Similar ads under the listing.** Cheap for us — one existing query.
6. **Sold / Rented badge.** OLX marks an ad sold rather than deleting it. Keeping completed
   listings visible-but-closed is what makes a neighbourhood look active instead of empty.
7. **Seller profile with "member since" and response rate.** Small denormalisation, and it is
   what makes a stranger with a drill feel like a neighbour with a drill.

**Deliberately not copying**

8. **Paid promotion / featured listings.** At neighbourhood density this corrupts a feed whose
   entire promise is *closest first* — **the ranking is the product**.
9. **Their safety-tips interstitial.** Right instinct, wrong execution: a generic banner nobody
   reads. The Radius equivalent that matters is the handover checklist with photos.

**One thing to do better than OLX.** OLX pushes everything to off-platform contact and then
disclaims responsibility for it. Radius holds a deposit and computes a breakdown, so keeping the
conversation and the money on-platform is both safer for the member and the reason the fee is
defensible when it switches on.

**Sources:** [buying process](https://help.olx.in/hc/en-us/articles/36384134614045-Buying-process-on-OLX) ·
[contacting a seller](https://help.olx.in/hc/en-us/articles/10845259300253-How-do-I-initiate-a-chat-with-a-seller) ·
[favourites](https://help.olx.in/hc/en-us/articles/36406693294493-I-am-unable-to-add-Ads-under-Favourite) ·
[sharing an ad](https://help.olx.in/hc/en-us/articles/10876803335325-How-do-I-share-an-Ad-with-my-friends) ·
[Elite Seller package](https://help.olx.in/hc/en-us/articles/30981283186205-FAQs-Elite-Seller-Package) ·
[OLX properties](https://www.olx.in/properties_c3)

---

# Part 5 — Recommendations

Nearly free once the blocks above are being built:

- **An offer is a request.** Do not build a separate negotiation table. `proposed_amount_minor`
  plus a `COUNTERED` state on the existing machine, and everything else works unchanged.
- ~~Rate limit comments and ratings from new accounts~~ · ~~one report table, not three~~ ·
  ~~a ban that expires~~ · ~~soft-delete moderated content~~ — all four shipped with Block A.
- **Write the trust numbers from a stream, not a trigger.** A Kafka Streams job over
  `radius.request.v1` is the right shape.
- **Handover checklist with photos.** Still the highest-value unbuilt feature, and much cheaper
  to add while the photo pipeline and the report/evidence flow are open.

## Two decisions still open

**Launch geography.** Radius is worthless at low density and good at high density. Pick one or
two neighbourhoods and saturate them. Everything in the schema is already scoped by point and
radius, so this is a go-to-market choice, not a technical one.

**Liability on damage.** A held deposit covers a scratched grinder and not a stolen one. Decide
before launch whether a rental above a threshold needs a signed handover checklist with photos —
a small feature in Phase 03, an expensive retrofit later.

---

# Part 6 — Running it

**Without Docker** — the path that works on a locked-down Windows machine, no admin rights:

```powershell
.\scripts\devstack.ps1 setup     # once: JDK 21, Maven, PostgreSQL 16 + PostGIS, Redis, Kafka, MinIO
.\scripts\devstack.ps1 up
.\scripts\services.ps1 build
.\scripts\services.ps1 up
node infra\seed\seed.mjs
cd apps\web; npm install; npm run dev
```

**With Docker:**

```bash
cd infra && docker compose --profile all up -d --build && node ../infra/seed/seed.mjs
```

| What | URL |
| --- | --- |
| Web app | <http://localhost:3000> |
| API gateway | <http://localhost:8080> |
| Swagger, per service | `http://localhost:808x/swagger-ui.html` |
| Health, per service | `http://localhost:808x/actuator/health` |
| Postgres | `localhost:5433` · radius / radius |
| Redis | `localhost:6380` |
| Kafka | `localhost:9092` |
| MinIO API / console | `localhost:9010` / <http://localhost:9011> |

**You do not need to sign in to look around** — feed, search and map all read as a guest. Sign in
as `+491700000001` to book something or review a check; there is no SMS provider, so the OTP
comes back in the response. That number is also the default administrator — override with
`RADIUS_ADMIN_PHONE`.

Ports are deliberately off the defaults so an existing Postgres or Redis is left alone. Keep
`RADIUS_DEV_HOME` off OneDrive — a synced Postgres data directory corrupts.

**Rebuilding:** stop the services first. Each runs from its own jar, so a running stack makes
`mvn clean` fail on a locked file — and an incremental build will silently recompile nothing and
still report SUCCESS.

**Rebuilding one service** is `-pl common,<service>`, never `-pl <service>` alone: `radius-common`
is only ever built in the reactor and is not installed to the local repository, so the single-module
build fails to resolve it. Add `-s services\settings-central.xml` — `scripts\services.ps1` does
this for you — on any machine whose global Maven settings point at an internal mirror.
