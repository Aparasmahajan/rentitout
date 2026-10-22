# 06 · API reference

> **Audience:** developers (backend and client). Read from every controller, `Dtos.java` and the
> gateway config, 14 October 2026.
> **Live, exact schemas:** each service serves OpenAPI at `http://localhost:808x/v3/api-docs` and
> Swagger UI at `/swagger-ui.html` (8081–8086). When this page and OpenAPI disagree, **OpenAPI
> wins**. Fix this page.

## 1. Conventions

| Topic | Rule |
| --- | --- |
| Base URL | Always the **gateway**: `http://localhost:8080` (`NEXT_PUBLIC_API_URL`, `EXPO_PUBLIC_API_URL`). Never call a service port from a client |
| Paths | The gateway does **no prefix stripping**. `/api/...` on the gateway is `/api/...` on the service |
| Auth header | `Authorization: Bearer <accessToken>` |
| Content type | JSON in, JSON out. `204 No Content` for some deletes |
| Money | Integer **minor units** (paise) in fields ending `Minor`; `currency` is `"INR"` |
| Dates | `LocalDate` as `YYYY-MM-DD`; instants as ISO-8601 UTC |
| IDs | UUID strings |
| Errors | See [§3](#3-errors). **Branch on `code`, never on `message`** |

### Auth levels used below

| Level | Meaning |
| --- | --- |
| **public** | No token needed and none is used |
| **guest** | No token needed. If a token is sent, the response is personalised (distance, `mine`, `canEdit`). Guests must send coordinates where location matters and get 250 m-coarse points |
| **member** | Valid access token required, else **401** |
| **owner / party / author** | Member **and** a relationship to the resource, else 403 (or 404 for bookings, to hide existence) |
| **admin** | Member with role `ADMIN`; the handler calls `me.requireAdmin()` first, else **403 `forbidden`** |
| **internal** | Service-to-service only; **not routed** by the gateway |

## 2. Gateway

### Routes (first match wins)

| Route | Paths | Service |
| --- | --- | --- |
| `auth` | `/api/auth/**` | user-service :8081 (own tight rate limit) |
| `users` | `/api/me/**`, `/api/users/**`, `/api/tags/**`, `/api/professionals/**`, `/api/admin/**` | user-service :8081 |
| `listings` | `/api/listings/**`, `/api/feed/**`, `/api/photos/**`, `/api/comments/**`, `/api/ratings/**`, `/api/reports/**`, `/api/moderation/**` | listing-service :8082 |
| `search` | `/api/search/**`, `/api/saved-searches/**` | search-service :8083 |
| `booking` | `/api/requests/**`, `/api/threads/**` | booking-service :8084 |
| `booking-ws` | `/ws/**` | booking-service :8084 (WebSocket) |
| `payments` | `/api/payments/**`, `/api/fees/**`, `/api/webhooks/**` | payment-service :8085 |
| `notifications` | `/api/notifications/**` | notification-service :8086 |

> Content moderation lives under **`/api/moderation`**, not `/api/admin`, because `/api/admin/**`
> is already routed to user-service.

### Edge behaviour

1. Strips any client-sent `X-Radius-User-Id` / `X-Radius-User-Name` (spoofing guard), then sets
   them from a valid token. No service reads them; each service re-verifies the JWT.
2. **Public prefixes** (any method): `/api/auth/`, `/api/search`, `/api/tags`, `/api/users/`,
   `/api/professionals/`, `/actuator/health`, `/actuator/prometheus`, `/v3/api-docs`, `/swagger-ui`.
3. **Public on GET only:** `/api/feed`, `/api/listings`, `/api/reports/reasons`.
4. Anything else without a valid token → **401** `{"code":"unauthenticated","message":"Sign in to continue"}`.
5. **Rate limits** (Redis token bucket):

   | Bucket | Key | Rate | Burst | Applies to |
   | --- | --- | --- | --- | --- |
   | default | `user:<id>` if signed in, else `ip:<addr>` | 20/s | 60 | every route |
   | auth | `otp:<ip>` | 1/s | 5 | `/api/auth/**` (otp, verify, refresh, logout) |

   Exceeding a bucket → **429** with `X-RateLimit-*` headers and an empty body.
6. CORS: origins `http://localhost:*`, `http://127.0.0.1:*`, `exp://*`; credentials allowed.
7. Timeouts: connect 2 s, response 10 s.

⚠ Edge vs service mismatches are listed in [13 · Known issues](13-known-issues.md#security-and-api)
(notably `/ws/**` and `/api/webhooks/**` are not in the gateway's public list).

## 3. Errors

Every service returns:

```json
{
  "code": "dates_taken",
  "message": "Those days are already booked",
  "fields": {},
  "path": "/api/requests",
  "at": "2026-10-14T08:00:00Z"
}
```

- `validation_failed` (400) fills `fields` with `{fieldName: firstMessage}`.
- `unauthenticated` (401) also sets `WWW-Authenticate: Bearer realm="radius"`.
- Any unmapped exception → **500 `internal_error`**. ⚠ That currently includes malformed JSON,
  a missing required query parameter, and a non-UUID path variable (see [13](13-known-issues.md)).

### All error codes

| Code | HTTP | Meaning / where |
| --- | --- | --- |
| `unauthenticated` | 401 | No or invalid access token |
| `forbidden` | 403 | Not admin, not owner, not author, or account not active |
| `not_found` | 404 | Resource missing, or a booking you are not a party to |
| `validation_failed` | 400 | Bean-validation failure; see `fields` |
| `internal_error` | 500 | Anything unmapped |
| `bad_cursor` | 400 | Feed cursor cannot be decoded |
| `otp_rate_limited` | 429 | > 5 OTP sends per hour for this phone |
| `otp_locked` | 400 | > 5 wrong attempts on this code |
| `otp_expired` / `otp_invalid` | 400 | Code expired (5 min) / wrong |
| `invalid_refresh` | 401 | Refresh token unreadable, wrong type, or unknown |
| `refresh_replayed` | 401 | A revoked refresh token was reused: **all sessions revoked** |
| `bad_point` | 400 | lat and lon not sent together |
| `too_many_tags` | 409 | > 30 profile tags |
| `already_open` | 409 | A check of this kind is already open |
| `id_check_first` | 409 | Needs an approved ID check first (professional application, home-visit listing) |
| `professional_check_first` | 409 | Home-visit listing needs an active professional profile |
| `no_professional_profile` | 400 (submit) / 409 (approve) | Trade details missing |
| `reason_required` | 400 | Rejection or dismissal without a reason |
| `already_decided` | 409 | Verification already decided |
| `too_many_listings` | 409 | 50 live listings |
| `too_many_photos` | 409 | 8 photos |
| `price_required` | 400 | A non-`OPEN_NEED` listing without a price |
| `no_location` | 400 | Guest without lat/lon, or member without a home point |
| `posting_rate_limited` | 429 | Comment or rating hourly limit (stricter for accounts < 7 days) |
| `parent_mismatch` | 400 | Reply parent is on another listing |
| `comment_deleted` | 409 | Editing a removed comment |
| `comment_banned` | 403 | Commenting while banned (message includes the end date) |
| `rating_removed` | 409 | Re-rating after a moderator removed your rating |
| `bad_reason` / `target_required` / `bad_target_type` | 400 | Report payload problems |
| `bad_state` | 400 | Unknown report state filter |
| `illegal_transition` | 409 | Booking or report state machine refused the move |
| `bad_bbox` | 400 | Map bbox missing or malformed |
| `too_many_saved_searches` | 409 | > 10 saved searches |
| `own_listing` | 400 | Requesting your own listing |
| `listing_not_live` | 409 | Requesting a paused or unlisted listing |
| `listing_unlisted` | 409 | Pausing or resuming a listing that is `UNLISTED` (taken down by a moderator, or deleted by its owner) |
| `dates_taken` | 409 | Clash with a blocked range or another accepted booking |
| `not_bookable` | 400 | Listing has no rate (e.g. a pure sale or an open need) |

## 4. user-service

### Auth (`/api/auth`), public, tight rate limit

| Method | Path | Body → Response | Notes |
| --- | --- | --- | --- |
| POST | `/api/auth/otp` | `{phone}` → `{phone, expiresInSeconds: 300, devCode?}` | `phone` matches `\+?[0-9]{8,15}`. `devCode` only when `radius.otp.expose-code=true` (dev profile) |
| POST | `/api/auth/verify` | `{phone, code (6 digits), displayName? (≤60)}` → `TokenResponse` | Creates the account if new (emits `UserRegistered`) |
| POST | `/api/auth/refresh` | `{refreshToken}` → `TokenResponse` | Rotates. Reuse of a revoked token → `refresh_replayed` and every session revoked |
| POST | `/api/auth/logout` | — → 204 | **member** (the service needs the token). Revokes all refresh tokens |

`TokenResponse = {accessToken, refreshToken, expiresIn (900 s), me: MeResponse}`

### Profile

| Method | Path | Auth | Body → Response |
| --- | --- | --- | --- |
| GET | `/api/me` | member | → `MeResponse` |
| PATCH | `/api/me/profile` | member | `{displayName?, photoUrl?, areaLabel?, lat?, lon?, searchRadiusKm? (1–50), openToRequests?, bio? (≤500)}` → `MeResponse`. Emits `ProfileUpdated` |
| POST | `/api/me/tags` | member | `{slug, relation: has\|teaches\|needs\|enjoys}` → `TagRef[]` |
| DELETE | `/api/me/tags/{slug}?relation=` | member | → `TagRef[]` (`relation` is required) |
| GET | `/api/users/{id}` | guest | → `PublicProfileResponse` (`distanceKm` only when signed in) |
| GET | `/api/tags?kind=` | public | → `TagRef[]` (`kind` ∈ `skill, teach, need, hobby, item`) |

```ts
MeResponse = { id, displayName, phone, photoUrl, areaLabel, lat, lon, searchRadiusKm,
  openToRequests, bio, ratingAvg, completedCount, idChecked, idCheckedAt, professional,
  role: 'MEMBER'|'ADMIN', tags: TagRef[] }
TagRef = { id, slug, label, kind, relation }
```

### Verification and professionals

| Method | Path | Auth | Body → Response |
| --- | --- | --- | --- |
| GET | `/api/me/verification` | member | → `MyVerification {idChecked, identityState, professionalState, canGoProfessional, isProfessional, history[]}` |
| POST | `/api/me/verification` | member | `{kind: IDENTITY\|PROFESSIONAL, evidenceKeys[≤8], note?}` → `VerificationStatus` |
| DELETE | `/api/me/verification/{id}` | member (own) | → 204 (withdraw) |
| PUT | `/api/me/professional` | member, ID checked | `{trade, businessName?, about?, shopAddress?, shopLat?, shopLon?, serviceRadiusKm? (1–100), yearsExperience?, licenceRef?, insuranceRef?, languages?, contactPhone?}` → `ProfessionalResponse` |
| GET | `/api/me/professional` | member | → `ProfessionalResponse` |
| GET | `/api/professionals/{id}` | public | → `ProfessionalResponse` (public shopfront) |

Evidence files are uploaded first with `POST /api/photos/presign` (listing-service). Only the
returned object keys are sent here.

### Admin (`/api/admin`), admin only

| Method | Path | Body → Response |
| --- | --- | --- |
| GET | `/api/admin/verifications?state=` | → `ReviewItem[]` (oldest first, ≤ 100) |
| POST | `/api/admin/verifications/{id}/claim` | → `ReviewItem` (→ IN_REVIEW) |
| POST | `/api/admin/verifications/{id}/approve` | `{note?}` → `ReviewItem` |
| POST | `/api/admin/verifications/{id}/reject` | `{note}` **required** → `ReviewItem` |
| POST | `/api/admin/professionals/{userId}/suspend` | `{note?}` → `ProfessionalResponse` |

## 5. listing-service

### Listings and feed

| Method | Path | Auth | Body / query → Response |
| --- | --- | --- | --- |
| POST | `/api/listings` | member | `CreateListingRequest` → **201** `ListingResponse`. Emits `ListingPublished` |
| PATCH | `/api/listings/{id}` | owner | any subset of the create fields → `ListingResponse`. Emits `ListingUpdated` |
| GET | `/api/listings/mine` | member | → `ListingCard[]` (all statuses) |
| GET | `/api/listings/{id}` | guest | → `ListingResponse` (a non-live listing is visible to its owner only) |
| POST | `/api/listings/{id}/pause` / `resume` | owner | → `ListingResponse` |
| DELETE | `/api/listings/{id}` | owner | → `ListingResponse` (status `UNLISTED`) |
| GET | `/api/listings/{id}/availability` | guest | → `{listingId, weekly: Rule[], blocked: [{from, to, reason}]}` |
| GET | `/api/feed?lat&lon&radiusKm&kind&cursor&limit` | guest | → `FeedPage {items: ListingCard[], nextCursor, radiusKm}` |
| POST | `/api/photos/presign` | member | `{contentType: image/jpeg\|png\|webp, sizeBytes ≤ 10 MB}` → `{uploadUrl, objectKey, publicUrl, expiresInSeconds: 600}` |
| POST | `/api/listings/{id}/photos` | owner | `{objectKey}` → `PhotoDto[]` |
| DELETE | `/api/listings/{id}/photos/{photoId}` | owner | → 204 |

```ts
CreateListingRequest = {
  kind: 'RENT_ITEM'|'SELL_ITEM'|'TRADE_SERVICE'|'SKILL_FOR_HIRE'|'TEACHING'|'SPACE_OR_VEHICLE'|'OPEN_NEED',
  title (≤120), description? (≤4000),
  priceMinor?, unit?: 'HOUR'|'DAY'|'WEEK'|'SESSION'|'ITEM', depositMinor?, buyPriceMinor?,
  lat, lon, tags? (≤10 slugs), availability?: {weekday 1–7, from 'HH:mm', to 'HH:mm'}[],
  homeVisit?: boolean }
ListingResponse = { id, kind, title, description, priceMinor, unit, depositMinor, buyPriceMinor,
  currency, lat, lon, status, tags, photos, availability, owner: OwnerDto, distanceKm, mine,
  homeVisit, ratingAvg, ratingCount }
OwnerDto = { id, displayName, photoUrl, areaLabel, idChecked, professional, trade }
```

**Feed paging:** pass `nextCursor` back as `cursor`. `null` means the end. Default page 20,
max 50. Radius defaults to the member's radius, or 5 km for a guest.

**Photo upload sequence:** presign → `PUT uploadUrl` with the same `Content-Type` → attach
`objectKey`. Image bytes never pass through a service.

### Comments, ratings, reports

| Method | Path | Auth | Body → Response |
| --- | --- | --- | --- |
| GET | `/api/listings/{id}/comments` | guest | → `CommentView[]` (nested one level; `canEdit`/`canDelete` decided by the server) |
| POST | `/api/listings/{id}/comments` | member | `{body ≤2000, parentId?}` → **201** `CommentView` |
| PATCH | `/api/comments/{id}` | author | `{body}` → `CommentView` |
| DELETE | `/api/comments/{id}` | author, listing owner, or admin | → 204 (soft delete) |
| GET | `/api/listings/{id}/ratings` | guest | → `RatingSummary {average, count, histogram, mine, ratings[]}` |
| POST | `/api/listings/{id}/ratings` | member, not owner | `{stars 1–5, title? ≤120, body? ≤2000}` → `RatingView` (create or replace) |
| DELETE | `/api/ratings/{id}` | author or admin | → 204 |
| GET | `/api/reports/reasons` | public | → `["SPAM","SCAM","OFFENSIVE","WRONG_CATEGORY","PROHIBITED_ITEM","HARASSMENT","MISLEADING","OTHER"]` |
| POST | `/api/reports` | member | `{targetType: LISTING\|COMMENT\|RATING, targetId, reason, detail?}` → **201** `ReportView` |

### Moderation (`/api/moderation`), admin only

| Method | Path | Body → Response |
| --- | --- | --- |
| GET | `/api/moderation/reports?state=` | → `ReportView[]` (with `targetSummary` and `targetAuthorId`) |
| POST | `/api/moderation/reports/{id}/claim` | → `ReportView` |
| POST | `/api/moderation/reports/{id}/action` | `{note}` → `ReportView` (does not remove content by itself) |
| POST | `/api/moderation/reports/{id}/dismiss` | `{note}` **required** → `ReportView` |
| DELETE | `/api/moderation/comments/{id}?note=` | → 204; closes open reports |
| DELETE | `/api/moderation/ratings/{id}?note=` | → 204; closes open reports |
| POST | `/api/moderation/listings/{id}/unlist` | `{note?}` → 204; closes open reports |
| GET | `/api/moderation/bans` | → `BanView[]` |
| POST | `/api/moderation/members/{userId}/comment-ban` | `{days 1–3650, reason}` → `BanView` |
| DELETE | `/api/moderation/members/{userId}/comment-ban` | → 204 |

### Internal (not routed)

| Method | Path | Used by |
| --- | --- | --- |
| GET | `/internal/listings/{id}` → `ListingSnapshot {id, ownerId, kind, title, priceMinor, unit, depositMinor, buyPriceMinor, currency, status}` | booking `ListingClient` |
| GET | `/internal/listings/{id}/free?from&to` → `boolean` | booking `ListingClient` |

## 6. search-service

| Method | Path | Auth | Body / query → Response |
| --- | --- | --- | --- |
| POST | `/api/search` | guest | `SearchRequest` → `SearchResponse` |
| GET | `/api/search/map?bbox=south,west,north,east` | public | → `MapResponse {pins[], clusters[], total, clustered}` (clusters when > 200) |
| POST | `/api/saved-searches` | member | `{label?, query, radiusKm?, digestFrequency?: daily\|weekly\|off}` → `SavedSearchDto` |
| GET | `/api/saved-searches` | member | → `SavedSearchDto[]` |
| DELETE | `/api/saved-searches/{id}` | member | → 204 |

```ts
SearchRequest = { q? (≤200), keyword? (≤80), kinds? (≤5), tags? (≤10), radiusKm? (1–50),
  availableOn?: 'YYYY-MM-DD', sort?: 'relevance'|'distance'|'price'|'rating',
  lat?, lon?, page? (0–50) }
SearchResponse = { parsed: {terms, tags, kinds, radiusKm, day, source: 'filters'|'rules'},
  listings: ListingHit[], members: MemberHit[] /* page 0 only, ≤6 */, radiusKm, hasMore }
```

Example:

```http
POST /api/search
{"q": "I need someone who knows video editing and lives within 5 km", "lat": 52.4996, "lon": 13.418}

200 {"parsed": {"terms": [], "tags": ["video-editing"], "kinds": ["SKILL_FOR_HIRE"],
     "radiusKm": 5, "day": null, "source": "rules"}, "listings": [...], "members": [...], ...}
```

## 7. booking-service

| Method | Path | Auth | Body → Response |
| --- | --- | --- | --- |
| POST | `/api/requests/quote` | member | `{listingId, startDate, units 1–365}` → `Breakdown` |
| POST | `/api/requests` | member | `{listingId, startDate (future), units, message? ≤500}` → **201** `RequestResponse` |
| GET | `/api/requests?direction=in\|out` | member | → `RequestResponse[]` (`in` = I am the owner; default `out`) |
| GET | `/api/requests/{id}` | party | → `RequestResponse` (with `history`) |
| POST | `/api/requests/{id}/accept` | owner | → `RequestResponse` (re-checks `dates_taken`) |
| POST | `/api/requests/{id}/decline` | owner | `{reason?}` → `RequestResponse` |
| POST | `/api/requests/{id}/start` | party | → `RequestResponse` (→ IN_PROGRESS, "handed over") |
| POST | `/api/requests/{id}/complete` | party | → `RequestResponse` |
| POST | `/api/requests/{id}/cancel` | party | → `RequestResponse` |
| GET | `/api/threads/{requestId}` | party | → `MessageDto[]` |
| POST | `/api/threads/{requestId}` | party | `{body ≤2000}` → `MessageDto` (then pushed over STOMP) |
| POST | `/api/threads/{requestId}/read` | party | → `{marked}` |

```ts
Breakdown = { rateMinor, unit, units, amountMinor, depositMinor, feeMinor, feeLabel,
  totalMinor, currency, settlementNote }
RequestResponse = { id, listingId, listingTitle, requesterId, ownerId, startDate, endDate, units,
  unit, message, status, breakdown: Breakdown, createdAt, expiresAt, unreadCount, iAmOwner,
  history: {from, to, actorId, reason, at}[] }
```

**Clients render `breakdown` as sent. They never add, multiply or apply a fee.**

### WebSocket (STOMP)

| Item | Value |
| --- | --- |
| URL | `ws://<gateway>/ws` (native WebSocket, no SockJS) |
| CONNECT header | `Authorization: Bearer <accessToken>` (in the STOMP frame, not the URL, so it stays out of access logs) |
| Subscribe | `/topic/threads/{requestId}` → `MessageDto` frames |
| Send | Not used. Post messages over REST |

⚠ SUBSCRIBE is not authorised per thread, and the gateway requires a bearer header on the
upgrade itself. See [13](13-known-issues.md#security-and-api).

## 8. payment-service

| Method | Path | Auth | Response |
| --- | --- | --- | --- |
| GET | `/api/fees/quote?kind=&amountMinor=` | member | `{feeMinor, label}` (`"Radius fee: none for now"` while zero) |
| GET | `/api/payments/mine` | member | `PaymentDto[]` (where I am payee) |
| GET | `/api/payments/payouts` | member | `PayoutDto[]` |
| POST | `/api/webhooks/gateway` | provider callback | `{gatewayRef, type}` → **202**. Types: `payment.authorised`, `payment.settled`, `payment.failed`, `payment.refunded`. Idempotent on `gatewayRef`. ⚠ No signature check |
| GET | `/internal/fees/quote` | internal | `{feeMinor, label}` (booking `FeeClient`) |

## 9. notification-service (member)

| Method | Path | Response |
| --- | --- | --- |
| GET | `/api/notifications?limit=30` | `{id, kind, title, body, deepLink, state, createdAt, readAt}[]` (limit 1–100) |
| GET | `/api/notifications/unread` | `{count}` |
| POST | `/api/notifications/read` | `{marked}` (marks all read) |
| GET | `/api/notifications/stream` | **SSE**, event name `notification`, data `{id, kind, title, body, deepLink}`. 30 min timeout. Needs the bearer header, so a browser's native `EventSource` cannot be used as-is |

## 10. Pagination summary

| Endpoint | Style |
| --- | --- |
| `GET /api/feed` | **Keyset** cursor (base64url of `distance\|id`), stable while new listings appear |
| `POST /api/search` | Offset: `page` × 20, `hasMore` |
| `GET /api/notifications` | `limit` only |
| Admin queues | Verifications ≤ 100; reports unbounded |
| Everything else | Unpaginated lists |

## 11. Idempotency

There is **no `Idempotency-Key` HTTP header**. Retries are made safe by data shape instead:
one rating per member per listing (upsert), one report per reporter per target, one open
verification per kind, and unique keys on every money table. Event consumers are idempotent.
See [07](07-events-and-messaging.md#4-consumers).

## 12. Example session (curl)

```bash
API=http://localhost:8080
# 1. sign in (dev profile returns devCode)
curl -s -XPOST $API/api/auth/otp -H 'content-type: application/json' -d '{"phone":"+491700000001"}'
TOKEN=$(curl -s -XPOST $API/api/auth/verify -H 'content-type: application/json' \
  -d '{"phone":"+491700000001","code":"<devCode>"}' | jq -r .accessToken)

# 2. browse as a guest
curl -s "$API/api/feed?lat=52.4996&lon=13.418&radiusKm=5" | jq '.items[].title'

# 3. quote and request a listing
curl -s -XPOST $API/api/requests/quote -H "authorization: Bearer $TOKEN" \
  -H 'content-type: application/json' -d '{"listingId":"<id>","startDate":"2026-10-20","units":2}'
```
