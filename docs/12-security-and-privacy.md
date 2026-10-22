# 12 · Security and privacy

> **Audience:** tech lead, security reviewers, anyone touching auth, admin, geo, uploads or money.
> **Tooling:** the `security-auditor` agent (`.claude/agents/security-auditor.md`) runs from
> `/review` when a diff touches these areas.
> *"A security bug here can send a stranger to someone's door."*

## 1. Threat model in brief

| Asset | Threat | Main controls |
| --- | --- | --- |
| A member's **home location** | Stalking, burglary | ~100 m rounding at write time; 250 m snapping for guests; no exact address field for members |
| **Who comes to your home** | An unvetted person offering a home visit | Two ordered human checks (ID, then professional) enforced server-side before a home-visit listing |
| **Accounts** | Takeover via OTP brute force or a stolen refresh token | OTP send and attempt limits; gateway OTP bucket; rotating refresh with family revocation on reuse |
| **Supply data** | Bulk scraping of the neighbourhood | Guests get coarse points; gateway rate limits per IP/user |
| **Money** | Double charge, replayed webhook | Unique constraints on `request_id`, `gateway_ref`, `(owner, period)` |
| **Content** | Spam, harassment, scams | Reports, moderation queue, comment bans, stricter limits for new accounts |
| **ID evidence** | Leak of government ID images | Keys only, `purge_after` 30 days, restricted object prefix |
| **Admin powers** | Privilege escalation | Role in signed token; `requireAdmin()` throws; no "make admin" endpoint |

## 2. Authentication

| Item | Implementation |
| --- | --- |
| Credential | Phone + 6-digit OTP (5 min). Email and password columns exist but are unused |
| OTP abuse | Gateway bucket `otp:<ip>` 1/s, burst 5 · 5 sends/hour/phone (`otp_rate_limited`) · 5 attempts/code (`otp_locked`) |
| Tokens | HS-signed JWT (jjwt; key ≥ 32 bytes; HS384 with the dev key). Issuer `radius` required |
| Access token | 15 min; claims `sub`, `typ=access`, `name`, `role`, `jti` |
| Refresh token | 30 days; `typ=refresh`; `jti` = server-side `refresh_token` row. Rotated on every use |
| Reuse detection | Presenting a revoked refresh token revokes **all** of that user's tokens (`refresh_replayed`) |
| Logout | Revokes all refresh tokens |
| Where checked | Gateway `AuthenticationFilter` (fast path) **and** every service (`JwtAuthFilter`). Services never trust gateway headers |
| Unauthenticated | **401** with `WWW-Authenticate: Bearer`. Never 403. Clients refresh only on 401 |
| Client storage | Web: `localStorage` (XSS-exposed; moving to HTTP-only cookies + BFF is planned). Mobile: Keychain/Keystore via SecureStore |

## 3. Authorization

- **Roles:** `MEMBER`, `ADMIN` (DB `CHECK`). Admin comes only from `RADIUS_ADMIN_PHONE` at
  start-up. **No API grants it.**
- **`me.requireAdmin()` throws** (403). A forgotten `if` on a boolean fails open; a throw cannot.
  Every method in `AdminController` and `AdminModerationController` opens with it.
- **Ownership:** listing edit, pause and photos → owner. Comment edit → author. Comment delete →
  author, listing owner or admin. Rating delete → author or admin. Booking accept/decline → owner.
  Start, complete and cancel → either party.
- **Existence hiding:** a booking you are not party to returns **404**, not 403.
- **Home-visit gate:** `ListingService.requireCheckedProfessional`, from a local projection fed by
  `radius.user.v1`. The refusal codes are ordered: `id_check_first`, then `professional_check_first`.
- **Refusals carry reasons:** rejecting a check or dismissing a report without a note →
  `reason_required`.

## 4. Location privacy

| Point | Stored | Shown to members | Shown to guests |
| --- | --- | --- | --- |
| Member home (`profile.lat/lon`) | **Rounded to ~100 m** (`Geo.roundLat/roundLon`) | Rounded point; distances | Not shown directly |
| Listing (`listing.lat/lon`) | ⚠ **As sent by the client** (clients send the rounded home point) | As stored | **Snapped to 250 m** (`Geo.coarsen`) in feed and search |
| Professional shop (`shop_lat/lon`) | **Exact, on purpose** (a business address) | Exact | Exact |
| Dev seed members | 4-decimal, bypasses rounding | — | — |

Rules:

- Distance **filters** are PostGIS `ST_DWithin`. Display distances come from SQL, except the
  listing detail page, which uses `Geo.distanceMetres` in Java (a known deviation).
- Guests **must** send coordinates; the API refuses to guess (`no_location`).
- Phone numbers are not in public DTOs. `ProfessionalResponse.contactPhone` is the business
  number the professional chose to publish.

## 5. Uploads and evidence

- Photos and evidence use **pre-signed PUT** URLs (10 min, `image/jpeg|png|webp`, ≤ 10 MB) to the
  `radius-photos` bucket. Bytes never pass through a JVM.
- Verification stores only `evidence_keys`, with `purge_after` set on decision (+30 days) or on
  withdrawal (today).
- ⚠ Gaps: evidence uses the same presign endpoint, so it lands under `listings/<ownerId>/…`
  in a bucket with **anonymous download** (set by the Compose `minio-setup` job). Nothing deletes
  objects at `purge_after` yet. `attachPhoto` does not check that the key belongs to the caller.
  See [13](13-known-issues.md#security-and-api).

## 6. Rate limiting and abuse

| Layer | Limit |
| --- | --- |
| Gateway, all routes | 20 req/s, burst 60, per user (or per IP for guests) |
| Gateway, `/api/auth/**` | 1 req/s, burst 5, per IP |
| OTP | 5 sends/h/phone; 5 attempts/code |
| Comments | 5/h if the account is < 7 days old (or unknown: fails closed), else 30/h |
| Ratings | 3/h new, 15/h established |
| Listings | 50 live per member; 8 photos each |
| Saved searches | 10 per member |
| Reports | One per reporter per target |

## 7. Money integrity

- Integer paise everywhere. The hook `check-invariants.mjs` flags `double`, `float` and
  `BigDecimal` money variables.
- The price comes from listing-service **live**, with no fallback; it is snapshotted on the request.
- The fee is stored on the payment row, so a fee change never re-prices history.
- Idempotency by unique constraints: `payment.request_id`, `deposit_hold.request_id`,
  `gateway_event.gateway_ref`, `payout(owner_id, period)`.
- ⚠ The webhook has no signature verification yet, and `Payment.moveTo` allows any state change.

## 8. Transport and edge

- CORS: `http://localhost:*`, `http://127.0.0.1:*`, `exp://*` with credentials. **Must be
  narrowed for production.**
- The gateway strips client-supplied `X-Radius-User-*` headers.
- `/internal/**` endpoints are `permitAll` in the service and **not routed** by the gateway. They
  rely on network isolation. Keep service ports unpublished.
- Actuator: `health`, `info`, `prometheus` (and `gateway`/`circuitbreakers`) are exposed. Restrict
  them in production.

## 9. Open security findings

Found while writing these docs and verified in the source. Each is tracked with a severity in
[13 · Known issues](13-known-issues.md#security-and-api):

1. STOMP **SUBSCRIBE is not authorised**: any signed-in user who knows a request id can listen to
   that thread.
2. Gateway does not list `/ws/**` or `/api/webhooks/**` as public. Browser WebSocket upgrades
   carry no bearer header, and providers cannot send one.
3. Evidence objects are publicly downloadable and never purged.
4. Webhook unsigned; payment state unguarded.
5. `resume` can revive a moderator-unlisted listing.
6. Web tokens in `localStorage` (planned move to cookies).
7. `/actuator/gateway` reachable without auth.
8. One shared DB superuser for all services.

## 10. Reporting a vulnerability

There is no public disclosure process yet. Report privately to the maintainers. Do not open a
public issue for anything that exposes location, identity evidence or another member's data.
