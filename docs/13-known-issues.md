# 13 · Known issues and tech debt

> **Audience:** tech lead and contributors picking work.
> Compiled on **14 October 2026** while writing these docs, by reading the source on branch
> `addn`. Items marked **✔ verified** were confirmed by reading the exact lines. Items marked
> **? to confirm** follow from the code but have not been exercised against a running stack.
> This register is input to `poc.md` Part 3, not a replacement for it. When you fix one, delete
> it here and note it in `poc.md`. *Fixed*, at the end, lists what has already been removed.

Severity: **High** = security, privacy or data correctness. **Medium** = broken feature or
misleading behaviour. **Low** = polish, consistency or debt.

## Security and API

| # | Sev | Issue | Where | Status |
| --- | --- | --- | --- | --- |
| S2 | **High** | **ID evidence is publicly downloadable and never purged.** Evidence is uploaded with the listing photo presign (`listings/<ownerId>/…`) into `radius-photos`, which Compose marks `mc anonymous set download`. `purge_after` is recorded, but no job deletes objects. The root README's "restricted prefix" claim is not true today | `apps/web/app/me/verification/page.tsx:36`, `PhotoService.java:36`, `infra/docker-compose.yml:126` | ✔ verified |
| S4 | **High** | **Payment webhook is unauthenticated and unreachable.** No signature check (by design for the sandbox), and `/api/webhooks/**` is not public at the gateway, so a real provider would get 401 | `PaymentController`, `AuthenticationFilter` | ✔ verified (gateway list) |
| S6 | Medium | `Payment.moveTo` accepts any target state (no transition map, unlike booking and report) | `payment-service/.../domain/Payment.java` | reported |
| S7 | Medium | `attachPhoto` does not check that the object key belongs to the caller (any key in the bucket can be attached) | `ListingService.attachPhoto` | reported |
| S8 | Medium | Unmapped framework exceptions become **500 `internal_error`**: malformed JSON, a missing required `@RequestParam`, a non-UUID path variable, and an open circuit breaker. They should be 400, 404 or 503 with stable codes. The gateway does the same when a service refuses the connection (still starting): **500**, not 503 (seen 15 October 2026) | `common/.../GlobalExceptionHandler.java` (catch-all `Exception`) | ✔ verified (handler list) |
| S15 | Low | A refused STOMP frame comes back as an `ERROR` whose message is Spring's internal text (*"Failed to send message to ExecutorSubscribableChannel[clientInboundChannel]"*), not a stable code like `not_your_thread`. Clients can only tell that it failed, not why | `booking-service/.../config/WebSocketConfig.java` |
| S9 | Low | Gateway 401 body has 2 fields (`code`, `message`) while services send 5. Clients only read `code`, so this is harmless but inconsistent | `AuthenticationFilter` | reported |
| S10 | Low | `/actuator/gateway` and `/actuator/info` on the gateway are reachable without a token | gateway management config | reported |
| S11 | Low | `/api/auth/logout` is public at the edge but needs a token at the service (works, but the lists disagree) | gateway vs user-service config | reported |
| S12 | Low | One shared Postgres superuser for all six databases | `infra/docker-compose.yml` | ✔ verified |
| S13 | Low | Web tokens in `localStorage` (XSS-exposed). Planned move to HTTP-only cookies + BFF | `apps/web/lib/api.ts` | known (poc.md) |
| S14 | Low | `no_professional_profile` is 400 on submit and 409 on approve: the same code with two statuses | `VerificationService.java:78,196` | reported |

## Events and data

| # | Sev | Issue | Where | Status |
| --- | --- | --- | --- | --- |
| E3 | Medium | **One poison outbox row blocks that service's outbox forever.** No maximum attempts and no dead-letter; the batch stops at the first failure, retried every 500 ms | `common/.../outbox/OutboxPublisher.java` | reported |
| E4 | Medium | **No consumer dead-letter topic.** After 10 immediate retries a record is logged and skipped | all consumers (default `DefaultErrorHandler`) | known (poc.md backlog) |
| E5 | Medium | **Enabling the LLM parser crashes search-service at start-up.** `radius.search.llm.enabled=true` removes the only `LlmFallback` bean and nothing replaces it | `search/parse/LlmFallback.java` | reported |
| E6 | Medium | Listing coordinates are stored exactly as sent. Rounding relies on clients sending the already-rounded home point | `ListingService.create/update` | ✔ verified |
| E7 | Medium | Listing detail computes distance in Java (`Geo.distanceMetres`), against non-negotiable 4 | `ListingService.java:214` | ✔ verified |
| E8 | Low | Columns nothing writes: `profile.rating_avg/completed_count` (parked for Kafka Streams), `member_doc.bio/rating_avg`, `listing_doc.owner_rating`. Listing events carry no `currency` | user, search | ✔ known / reported |
| E9 | Low | Topics auto-created with 1 partition; no explicit topic config | Compose | reported |
| E10 | Low | `@Scheduled` jobs (expiry, digest) have no distributed lock | booking, search | reported |
| E11 | Low | Many enum columns have no DB `CHECK` (listing kind/status, booking status, payment state, …) | migrations | reported |
| E12 | Low | `deposit_hold` has no currency column | payment V1 | reported |
| E13 | Low | resilience4j `timelimiter` configured but unused; no HTTP client timeouts on booking's sync calls | `booking-service/application.yml` | reported |

## Web

Found by code read and not yet exercised.

| # | Sev | Issue | Where |
| --- | --- | --- | --- |
| W9 | Low | STOMP client keeps its first access token; after 15 min, reconnects use an expired token | `lib/useThread.ts` |
| W10 | Low | Map: rings become ellipses on the 16:10 desktop frame; "X across" reports the radius, not the span; server clustering (`/api/search/map`) unused | `components/RadiusMap.tsx` |
| W11 | Low | PWA manifest and icon use the retired blue palette (`#5980a6`); no PNG icons and no service worker | `public/manifest.webmanifest` |
| W12 | Low | Theme applied in `useEffect`, so dark users see a light flash; fonts loaded through a render-blocking `@import` | `lib/theme.tsx`, `globals.css` |
| W13 | Low | Admin evidence URL hard-codes `:8080 → :9010` (the Docker-free MinIO port); wrong under Docker (`:9000`) | `app/admin/page.tsx` |
| W14 | Low | Search radius max 25 km while feed and map allow 50; `TRADE_LABEL` duplicated in three files with different `OTHER` labels | various |
| W15 | Low | Unused API wrappers: notifications, saved searches, listing update/unlist, suspend, report reasons | `lib/api.ts` |

## Mobile

M12 is `expo-doctor`'s only failing check (15 October 2026). The rest were found by code read.

| # | Sev | Issue | Where |
| --- | --- | --- | --- |
| M12 | Medium | `app.json` fails schema validation: `android.usesCleartextTraffic` is not a valid key, so it is ignored. A release APK will then **refuse plain-`http` API calls**. Use `expo-build-properties` for dev, and HTTPS for real builds | `app.json` |
| M13 | Medium | **No Android release path.** No `eas.json`, no icon or splash images, no `versionCode`, and the API URL defaults to the emulator (`10.0.2.2`). No Android SDK on this machine | `apps/mobile` |
| M7 | Low | Booking: free-text date, no clash check, quote errors hidden | `l/[id].tsx` |
| M8 | Low | `app.json` uses `userInterfaceStyle: automatic` and the old palette; brand fonts not loaded | `app.json` |
| M9 | — | Not built (by plan): map, comments, ratings, reports, verification, professional, admin | `poc.md` backlog |

## Developer experience

| # | Sev | Issue | Fix |
| --- | --- | --- | --- |
| D7 | Medium | **Recreating one service breaks its route until the gateway restarts.** The gateway keeps the old container IP (`Connection refused: listing-service/172.18.0.7` after the container moved to `.14`), so `up -d --build <svc>` answers 500 on that service's routes. Seen 15 October 2026 | Workaround: `docker compose restart api-gateway`. Fix: shorter DNS TTL in the gateway's HTTP client, or a resolver that re-resolves on connect failure |
| D8 | Medium | **Zscaler holds ports 9000 and 9010.** ZSATunnel listens on both, so neither the Compose MinIO (9000) nor the Docker-free one (9010) can bind on a Zscaler laptop | Compose: set `MINIO_PORT`/`MINIO_CONSOLE_PORT` (e.g. 9100/9101). The Docker-free scripts still hard-code 9010 |
| D2 | Low | Service ports 8081–8086 are not published, so Swagger UI per service needs an IDE run or a local port mapping | Optional `ports:` in a `docker-compose.override.yml` |
| D3 | Low | App containers have no healthcheck; `depends_on` covers only infra | Add `wget -qO- localhost:808x/actuator/health/readiness` |
| D4 | Low | Images on `latest` (`kafka-ui`, `minio`, `mc`) | Pin versions |
| D5 | Low | `poc.md` still lists "delete the empty `poc/` tree" as open, but the directory is already gone | Tick it in `poc.md` |

## Documentation drift

| # | Where | Says | Code says |
| --- | --- | --- | --- |
| X3 | `CLAUDE.md` | `design/_ds/` is "the Postcard design system bundle" | It is the **"Industry"** system used by the canvas docs; Postcard lives in `globals.css` / `theme.ts` |
| X4 | `CLAUDE.md` | Branch `dev` | Current work branch is `addn` (PRs still go to `main`) |
| X5 | root `README.md` | Evidence "under a restricted prefix" | Same public bucket and prefix as listing photos (S2) |
| X7 | Build plan | Seed twelve members | `seed.mjs` creates 6 via the API; `R__dev_members.sql` creates 12 in the dev profile |
| X8 | `OutboxEvent` Javadoc | `V*__outbox.sql` migrations | Outbox table is in each `V1__init.sql` |

## Product and content

| # | Issue |
| --- | --- |
| P1 | **Location mismatch.** The design prototype is set in **Kothrud, Pune** (₹), while the seed data, guest fallback point (`useViewerPoint`) and tests are **Kreuzberg, Berlin** with `+49` numbers, priced in ₹. Pick the launch neighbourhood (open decision 1 in [01](01-product-vision.md#two-business-decisions-still-open)) and update the seed, the fallback point and the phone pattern together |
| P2 | Seeded prices are small in rupees (e.g. ladder `500` paise = ₹5/day). They were euro-cent amounts before the INR switch, which deliberately did not rescale |
| P3 | "Verified booking" badge on ratings: confirm it is acceptable under the "never say verified" copy rule (it describes a booking, not a person) |

## Fixed

Removed from the tables above on 14–15 October 2026. Backend: `mvn-docker.ps1 test`, 69 tests
in 13 classes, 0 failures (49 before, 20 new). Web: `next build`, `typecheck` and `lint` pass.
Mobile: `npm ci`, `tsc`, `expo-doctor` (17/18, M12 left) and `expo export --platform android`
pass. **On 15 October 2026 the Docker stack was run and these were exercised through the gateway
(13/13 checks):** guest feed with and without a point (M2/M4), default admin and a refused
non-admin (D1), accept blocks dates → cancel frees them → another member books them (E2), chat
reaches a party live and an outsider gets nothing (S1), `/ws` through the gateway and a
tokenless socket refused (S3), pause/resume and `listing_unlisted` after a takedown (S5), plus a
photo presign → upload → attach → public download on the new MinIO. E1 is covered by unit tests
only. Not exercised: the web and mobile UIs in a browser or on a device.

| # | Fix |
| --- | --- |
| S1 | Every STOMP SUBSCRIBE to `/topic/threads/{id}` checks that the caller is one of the request's two parties |
| S3 | `/ws` (exact, or `/ws/…`) is public at the gateway; the token is checked at STOMP CONNECT and on each SUBSCRIBE |
| S16 | **Found in review, 16 October 2026: any socket could SEND forged chat into any thread**, even one that never sent CONNECT; the simple broker relayed client SEND to `/topic/threads/{id}` straight to both parties. The interceptor is now an allow-list (CONNECT/STOMP, SUBSCRIBE, UNSUBSCRIBE, DISCONNECT); chat is posted only via `POST /api/threads/{id}`. Replayed against the stack: before, 2 forged messages delivered; after, 0, while real chat still arrives |
| — | Sign-in `next` rejected `//host` but not `/host` (URL parsing reads `` as `/`). Web and mobile now share one pattern that refuses any backslash or whitespace |
| S5 | `pause`/`resume` refuse an `UNLISTED` listing with **409 `listing_unlisted`**. An owner's own delete is final too, by decision ([ADR-024](14-decision-log.md)) |
| E1 | The idempotency key is written only after the handler succeeds, so Kafka's retry reprocesses a failed event |
| E2 | listing-service consumes `RequestCancelled` and removes the request's `availability_block` |
| W1–W8 | `/sign-in` form inside `<Suspense>`; `scrim-bg`; Requests tab on phones; old tokens and missing classes replaced; ESLint 8 + `eslint-config-next`; search chip removal uses fresh state; a failed refresh clears `me`; the dashboard shows a "Completed" count instead of a client-side ₹ total |
| M1, M2 | Guests get the feed; Me, New and Requests show a sign-in prompt; "Request this" asks a guest to sign in; sign-in honours `next` (in-app paths only) |
| M3 | "ID checked" badge |
| M4 | Feed and search send the viewer point; search has a radius chip |
| M5 | New listing is blocked without a home point instead of falling back to Kreuzberg |
| M6 | `import * as SecureStore` |
| M7 (part) | Requester **Cancel**; action buttons disabled while a transition is in flight |
| M10, M11 | `expo-asset` added; `react-native@0.76.9`, `react-native-screens@~4.4.0`; lockfile regenerated |
| D1 | Compose passes `RADIUS_ADMIN_PHONE` (default `+491700000001`) to user-service |
| D6 | `mvn-docker.ps1` pulls the image explicitly, and no longer splits the default goal `test` into single letters |
| X1, X2, X6 | Counts and the admin claim corrected in `poc.md` and README |
| D9 | MinIO images could no longer be pulled (`minio/*` gone from Docker Hub, 401 on quay.io); Compose now uses `pgsty/minio` and `pgsty/mc`, pinned ([ADR-023](14-decision-log.md)) |
