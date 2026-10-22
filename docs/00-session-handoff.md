# 00 · Session handoff

> **Why this file exists:** written at the user's request on **14 October 2026** so the state of
> this work survives a reset of the chat session. It records what was done, what was actually
> verified, what is still open, and what was decided. It is a snapshot, not a living document —
> once the work below is picked up, `poc.md` and `docs/13-known-issues.md` take over.
>
> **Branch:** `addn` (based on `dev`; PRs go to `main`). **Nothing was committed** during this
> session.

---

## 1. What was produced

A complete documentation set in **`docs/`** (16 files, ~3,200 lines). Start at
[`docs/README.md`](README.md), which is the index with reading paths per audience.

| File | Covers |
| --- | --- |
| `README.md` | Index + reading paths (product / new dev / maintainer) |
| `01-product-vision.md` | Aim, personas, 7 listing kinds, principles, roadmap, business model, open decisions, glossary |
| `02-architecture.md` | Context + container diagrams, 7 services, event/sync communication, key flows, cross-cutting concerns, deployment, scaling limits |
| `03-tech-stack.md` | Every framework + exact version and why; upgrade notes |
| `04-domain-model.md` | Business rules, limits, state machines (booking, verification, report), pricing formula, the 11 non-negotiables restated |
| `05-data-model.md` | 6 databases, ER diagrams, indexes, projections, conventions, how to write a migration |
| `06-api-reference.md` | Gateway routes, auth levels, every endpoint, ~40 error codes, DTO shapes, curl walkthrough |
| `07-events-and-messaging.md` | Topics, event catalogue, consumers + idempotency, outbox mechanics, chat/SSE, scheduled jobs |
| `08-frontend.md` | Web routes/components/design tokens, the tile-free map, mobile app, web↔mobile parity table |
| `09-development-guide.md` | First run, daily workflow, conventions, recipes, definition of done, Claude Code tooling |
| `10-testing.md` | The 49 tests, what they cover, what is missing |
| `11-configuration-and-operations.md` | Ports, env vars, observability, runbook, troubleshooting, pre-deployment checklist |
| `12-security-and-privacy.md` | Threat model, auth, authorization, location privacy, rate limits, open findings |
| `13-known-issues.md` | **The defect register.** Ranked, with ✔ verified markers |
| `14-decision-log.md` | 22 ADRs with trade-offs, open decisions, template |
| `00-session-handoff.md` | This file |

All internal links and anchors were checked programmatically and resolve (0 problems).

**No source code was changed.** Only `docs/` was added. `poc.md`, `README.md` and `CLAUDE.md`
were deliberately left untouched (see §5).

---

## 2. What was actually verified vs. read

This distinction matters — treat anything not marked verified as a code read, not a proven fact.

### Ran and confirmed (14 October 2026)

| Check | Command | Result |
| --- | --- | --- |
| Web typecheck | `npx tsc --noEmit` | ✅ passes |
| **Web production build** | `npx next build` | ❌ **fails**: compiles + typechecks, then prerendering `/sign-in` throws `useSearchParams() should be wrapped in a suspense boundary` |
| Mobile typecheck | `npx tsc --noEmit` | ✅ passes (does **not** catch the runtime crash, M6) |
| **Mobile clean install** | `npm ci` | ⚠ exit 0, but nests `expo-asset`, `expo-font`, `expo-file-system`, `expo-keep-awake` under `node_modules/expo/node_modules` |
| **Mobile Android bundle** | `npx expo export --platform android` | ❌ **fails**: `The required package 'expo-asset' cannot be found` |
| Expo health | `npx expo-doctor` | ❌ 3/18 checks fail: invalid `android.usesCleartextTraffic` in `app.json`; `react-native@0.76.3` (expect 0.76.9); `react-native-screens@4.1.0` (expect ~4.4.0) |
| Doc links | custom script | ✅ 0 broken links/anchors |
| Backend build + tests | `.\scripts\mvn-docker.ps1` | ⏳ **started at the end of this session — result unknown.** Docker was down for most of the session |

### Verified by reading the exact source lines

High-severity items in `13-known-issues.md` marked **✔ verified** — notably S1 (STOMP SUBSCRIBE
unauthorised), S2 (evidence publicly downloadable, never purged), S5 (`resume` revives a
moderator-unlisted listing), E1 (listing-service can drop user events), E2 (cancel leaves dates
blocked), M1/M2/M3/M6 (mobile), D1 (no admin under Compose), and the doc-drift items X1–X8.

### NOT verified (do not state as fact)

- Backend tests green (last recorded green run: **27 August 2026**, per `poc.md`).
- Nothing was run against a **running stack**: no `docker compose up`, no seed, no browser, no
  emulator. So S3 (WebSocket upgrade 401 through the gateway) and S4 (webhook unreachable) remain
  **"? to confirm"**.
- The build plan's **search < 300 ms p95** target has never been measured.

---

## 3. Project status against the plan

Percentages are estimates from the code, not measurements.

| Part | Status | Estimate |
| --- | --- | --- |
| Backend (7 services) | Phases 00–03 end to end. Phase 04 ~⅔ (manual checks + Block A moderation). Phase 05 ledger only, sandbox gateway, fee ₹0 | **~75–80%** of the build plan |
| Web app | Every planned route exists, works in `npm run dev`. Cannot produce a production build. No notifications UI. Styling/nav gaps | **~85%** of features; **not shippable** |
| Android app | Feed, search, listing, create, booking, chat, profile in basic form. No map/comments/ratings/reports/verification/admin. Guests cannot sign in | **~35–40%** of web parity; **not runnable** |
| Hosting | Nothing deployed anywhere; laptop only | **0%** |

### `poc.md` Part 3 backlog, re-checked against code

**~30 open items; exactly 1 closed since 27 August 2026** (the empty `poc/` directory is gone,
though `poc.md` still lists it as open). Last commit: `b3945fa`, 14 September 2026.

| Block | Done | Notes from code check |
| --- | --- | --- |
| A · Moderation | built; **0/3 follow-ups** | Member-level `profile.rating_avg`/`completed_count` still unwritten; seed never completes a booking (so the "verified booking" badge is invisible in a demo); mobile has none of Block A |
| **B · Admin console** ("← start here") | **0/2** | Only `POST /api/moderation/listings/{id}/unlist` exists. No listings browse/search, no member lookup |
| C · Accounts | 0/5 | `password_hash` column exists, unused. No `username` column anywhere |
| D · Drafts + sale parity | 0/3 | `Listing.Status` is still `{LIVE, PAUSED, UNLISTED}`. No Buy CTA |
| E · From OLX | 0/7 | No `COUNTERED`, no `proposed_amount_minor`, no favourites, no share, no similar listings |
| Older backlog | 1/11 | Testcontainers (declared but unused), Kafka Streams, DLQ, Redis feed cache + accept lock, finish Phase 05, Debezium, FCM/WebPush, cookie sessions, mobile map, readability pass — all still open |

---

## 4. Decisions made this session

| Question | Answer |
| --- | --- |
| Where should docs live? | **`docs/` folder in the repo**, versioned with the code |
| Audience? | All three: new developers, maintainers/tech lead, product/business |
| What about existing `README.md` / `poc.md`? | **Keep them as the authorities**; docs link to them rather than copying |
| Android "done" means? | **A test APK installable on our phones**, talking to a hosted backend (Play Store later) |
| Backend hosting? | **Decide later — laptop for now.** Fix/finish the apps against the local stack first |
| How to proceed? | *"see ig we have plan check where are we wrt plan"* → status-against-plan assessment was delivered; **implementation not yet started** |

---

## 5. Open question awaiting the user

**I proposed adding a new block at the top of `poc.md` Part 3** covering the work needed for
"web ships + Android test APK", which the current plan does not cover, with Block B moving down.
**The user has not yet answered.** Nothing was written to `poc.md`.

The proposed block, in order:

1. **Android starts at all** — repair the mobile install (`npx expo install --fix`, commit the new
   lockfile); fix the `expo-secure-store` default-import crash (M6, one line); give guests a route
   to sign-in (M1) and a feed that loads (M2); send coordinates from feed/search (M4); change the
   "Verified" badge to "ID checked" (M3).
2. **An APK exists** — `eas.json` or local Gradle; icon + splash assets; Android `versionCode`;
   a non-emulator API URL. Needs **either an Expo account (EAS cloud build) or the Android SDK
   installed** — *neither exists on this machine* (see §6).
3. **Web ships** — fix the `/sign-in` prerender failure (W1); install/configure ESLint (W5); add a
   Requests tab for phones (W3); clean up the dead design tokens/classes (W4).
4. **Backend fixes both clients depend on** — S1 chat authorisation, S3 WebSocket through gateway,
   S2 evidence privacy + purge, E1 event loss, E2 cancel unblock, D1 admin under Compose.
5. **Android→web parity** (the bulk of the remaining work) — badges/availability, ratings,
   comments, report, booking date picker + clash check, requester cancel, verification +
   professional screens, map, notifications, theme toggle.

Items 1–4 are small, well-defined fixes. Item 5 is the large one.

---

## 5b. Fixes started (14 October 2026, later in the session)

The user asked to fix the blockers, in parallel. **Uncommitted** working-tree changes:

Done directly (small, verified by reading; not yet re-run):

| Issue | File | Change |
| --- | --- | --- |
| M6 Android launch crash | `apps/mobile/lib/useTheme.tsx` | `import * as SecureStore from 'expo-secure-store'` (was a default import that resolves to `undefined`) |
| M3 copy rule | `apps/mobile/app/u/[id].tsx` | "Verified" → "ID checked" |
| W1 web prod build | `apps/web/app/sign-in/page.tsx` | Page wrapped in `<Suspense>`; form moved to `SignInForm` |
| W2 booking backdrop | `apps/web/components/BookingSheet.tsx` | `scrim` → `scrim-bg` |

Delegated to three parallel agents (separate folders, no docs edits):

- **Mobile** (`apps/mobile/`): M10/M11 clean install + version alignment; M1/M2 guest flow and
  sign-in reachability; M4 coordinates in feed/search; M5 no-location guard; requester Cancel.
- **Web** (`apps/web/`): W3 Requests tab on phones; W4 dead tokens/classes; W5 ESLint; W6 search
  chip; W8 sign-out on failed refresh; W7 client-side money sum. Must end with `next build` passing.
- **Backend** (`services/`, `infra/docker-compose.yml`, `scripts/mvn-docker.ps1`): waits for the
  baseline `mvn test` first, then S1 STOMP subscribe auth, S3 `/ws` public at the gateway,
  E1 idempotency key burnt on failure, E2 cancel unblocks dates, S5 resume guard, D1 admin under
  Compose, D6 script image pull. New tests for the domain rules.

Not attempted: S2 (evidence storage redesign), APK config (M12/M13), Android→web parity.
**Status, 15 October 2026: all three finished and checked; still uncommitted.** The mobile
agent hit a rate limit after writing its edits, so its proof was run afterwards: `tsc`, `npm ci`,
`expo-doctor` (17/18, only M12) and `expo export --platform android` all pass. Backend: 60 tests,
0 failures. Web: `next build`, typecheck and lint pass. Docs updated: `13-known-issues.md` (fixed
rows moved to *Fixed*), `06-api-reference.md` (`listing_unlisted`), `poc.md` and README test
counts. Nothing has been exercised against the running stack yet.

Later on 15 October: the Docker stack was run, seeded, and 13/13 end-to-end checks passed
through the gateway (see `13-known-issues.md` → *Fixed*). Along the way: MinIO images can no
longer be pulled, so Compose now uses `pgsty/minio` + `pgsty/mc` (ADR-023); Zscaler holds
9000/9010 on this machine, so MinIO runs with `MINIO_PORT=9100` (D8); recreating one service
needs a gateway restart (D7). The owner-delete question is decided: deleting is final (ADR-024).
16 October: `/verify` and `/review` run. Review found a real hole (S16: forged chat via client
SEND, confirmed on the stack, now fixed and re-tested), plus the `/\host` sign-in redirect and
the `/ws` prefix; all fixed. 69 tests, 0 failures; e2e 13/13. Still not done: a click-through
of the web and mobile UIs, and the commit.

---

## 6. Environment facts (this machine)

| Fact | Value |
| --- | --- |
| Repo root | `C:\Users\parmahaj\Documents\New folder\rentitout` |
| OS / shell | Windows 11, PowerShell 5.1 (no `&&`, no ternary) |
| Docker | **Running** as of end of session (server 29.7.2). Was down for most of it |
| Java | **Only JDK 8 locally.** Java 21 comes from Docker — never run bare `mvn`/`java` |
| Maven | `RADIUS_MAVEN_HOME=C:\Users\parmahaj\Documents\apache-maven-3.9.9`; always use `.\scripts\mvn-docker.ps1` |
| Node / npm | v24.21.0 / 11.19.0 |
| **Android SDK** | **Not installed.** `ANDROID_HOME` and `ANDROID_SDK_ROOT` empty, no `adb`, no `%LOCALAPPDATA%\Android\Sdk` |
| node_modules | Installed this session in **both** `apps/web` and `apps/mobile` (were absent) |
| Network | `expo-doctor` could not reach the Expo API (connect timeout) — corporate network may block it, which **matters for EAS cloud builds** |

---

## 7. If you are resuming this work

1. Read [`docs/13-known-issues.md`](13-known-issues.md) first — it is the actionable register.
2. Check whether the backend test run finished:
   `.\scripts\mvn-docker.ps1` and compare against `poc.md`'s claim of 47 tests (source has **49**
   `@Test` methods in 8 classes).
3. Ask the user about the §5 open question before editing `poc.md`.
4. Respect `.claude/rules/build-record.md`: tick an item in `poc.md` only when it is built **and
   exercised**, and only update the header numbers after a real run.
5. The 11 non-negotiables in [`CLAUDE.md`](../CLAUDE.md#non-negotiables) still apply — breaking one
   is a bug even when tests pass.
