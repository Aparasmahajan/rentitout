# 09 · Development guide

> **Audience:** new developers. Goal: from a fresh clone to a running stack and a first safe change.
> Run commands from the **repo root in PowerShell** unless noted.

## 1. Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| Docker Desktop | current | Provides **Java 21** and every backing service. Must be running (`docker info`) |
| Maven | **3.9.x** on the host | Set its folder as `RADIUS_MAVEN_HOME` (in `.claude/settings.local.json`, or your shell) |
| Node.js | 20 LTS or newer | Web, mobile, seed script |
| Git | any | |
| (optional) Android Studio / Xcode | — | Emulator or simulator for mobile |

**Do not run bare `mvn` or `java` for the services.** Host JDKs are often older than 21, and a
hook blocks bare `mvn` when `JAVA_HOME` or `PATH` points at an old one. Use
`scripts\mvn-docker.ps1`, which runs your host Maven inside `eclipse-temurin:21-jdk` with
`~/.m2` shared.

> No Docker on your machine? Use the portable fallback in the root [README](../README.md#run-it-without-docker)
> (`devstack.ps1` + `services.ps1`, ports 5433/6380/9092/9010). Don't mix the two stacks.

## 2. First run (about 15 minutes)

```powershell
# 0. once per machine
$env:RADIUS_MAVEN_HOME = 'C:\path\to\apache-maven-3.9.9'

# 1. build and run the unit tests (Java 21 in Docker)
.\scripts\mvn-docker.ps1                     # = test

# 2. start everything: backing services + 7 services built into JDK 21 images
cd infra; docker compose --profile all up -d --build; cd ..
docker compose -f infra/docker-compose.yml ps    # wait until all are Up

# 3. seed six members, twelve listings and one booking THROUGH THE API
node infra\seed\seed.mjs

# 4. web
cd apps\web; copy .env.example .env.local; npm install; npm run dev
```

Open <http://localhost:3000>. Browse as a guest, then sign in with **`+491700000001`**. The OTP
is shown on screen, because there is no SMS provider in dev. To use `/admin` under Docker, see
[§6.6](#66-make-yourself-admin-locally).

| Useful URL | What |
| --- | --- |
| <http://localhost:8080> | API gateway |
| <http://localhost:8090> | Kafka UI: topics, messages, consumer lag |
| <http://localhost:16686> | Jaeger traces |
| <http://localhost:9001> | MinIO console (`radius` / `radius-secret`) |
| `localhost:5432` | Postgres (`radius` / `radius`), one DB per service |

Service ports 8081–8086 are **not published** by Compose (only the gateway is). To see a
service's Swagger UI, run that service from the IDE with `--profile infra`, or add a port
mapping locally.

## 3. Daily workflow

```powershell
# backing services only, then run one service from the IDE (JDK 21) with SPRING_PROFILES_ACTIVE=dev
cd infra; docker compose --profile infra up -d

# rebuild and restart one service in Docker after a change
docker compose -f infra/docker-compose.yml up -d --build booking-service
docker compose -f infra/docker-compose.yml logs -f booking-service

# test one module (and what it depends on)
.\scripts\mvn-docker.ps1 -pl booking-service -am test

# package without tests
.\scripts\mvn-docker.ps1 -DskipTests package

# clients
cd apps\web;    npm run typecheck; npm run dev
cd apps\mobile; npm run typecheck; npx expo start
```

- Build a single module with **`-pl <module> -am`**. `radius-common` is built only in the reactor
  and is not installed to `~/.m2`.
- Maven always uses `services/settings-central.xml` (Maven Central over HTTPS), so a corporate
  mirror in your global settings does not break resolution.
- `docker compose down` stops the stack. **`down -v` wipes the databases.** Ask before running it
  on a shared machine.

## 4. Branches, commits, PRs

- Work happens on feature branches off **`dev`**. PRs go to **`main`**. Never push directly to
  `main`/`master` or force-push (a hook blocks both).
- One logical change per PR. If it changes an endpoint, table, event or env var, update the
  matching doc in `docs/` **in the same PR**. If it changes what is built, update `poc.md`
  (`/update-record`).
- Before opening a PR, run **`/verify`** (or the equivalent commands in [§7](#7-definition-of-done))
  and **`/review`**.

## 5. Code conventions (summary)

The full rules are in `.claude/rules/*.md`. They load automatically for Claude Code, but they
apply to humans too.

### Java services

- **Package shape:** `io.radius.<svc>.{api, domain, repo, service, messaging, client, config}`.
- **Controllers are thin.** They take and return DTO `record`s from `api/Dtos.java`, never
  entities, and carry springdoc annotations.
- **Invariants live in the domain.** State machines (`BookingRequest.ALLOWED`, `Report.LEGAL`) are
  maps on the entity. Only the owning service class drives them.
- **Constructor injection, `private final` fields.** No field `@Autowired`, no Lombok.
- **Explain *why*, not *what*.** Every class has a Javadoc saying why it exists. Banner comments
  group sections. See `RequestService` and `V4__comments_ratings_reports.sql`.
- **Errors:** `ApiException.badRequest|conflict|forbidden|notFound(code, message)`. The `code` is a
  stable `snake_case` string. The message is friendly human copy.
- **Auth:** take an `AuthUser me` parameter, `@Nullable` for guest reads. Admin endpoints call
  `me.requireAdmin()` first. Add guest GET paths to `radius.security.public-get-paths` (and the
  gateway's `PUBLIC_GET` if needed).
- **Money:** `long` minor units; only `PricingService` / `FeeConfig` compute.
- **Geo:** `Geo` for rounding; `ST_DWithin` in native SQL for distance filters.
- **Time:** `Instant` for timestamps, `LocalDate` for booking dates.
- **Paging:** keyset via `Cursor` for any list ordered by distance.

### SQL / Flyway

`V{n}__snake_case.sql` per service, opening with a `-- Why:` block. UUID PKs, `timestamptz`,
`*_minor bigint`, generated `geography` + GiST, partial indexes where most rows are null, unique
constraints for money idempotency. **Never edit an applied migration.** See
[05 §12](05-data-model.md#12-writing-a-migration).

### TypeScript clients

Strict TypeScript. All HTTP goes through `lib/api.ts`. TanStack Query for server state. CSS
tokens only. Keep web and mobile `lib/` in step. See [08](08-frontend.md).

### Copy

"ID checked", "Professional", "verified booking" (for a rating backed by a completed booking).
Never "verified" for a person.

## 6. Recipes

### 6.1 Add a REST endpoint

1. Pick the service that **owns the data**. If it needs another service's data, use that
   service's projection or an event, not a cross-DB query.
2. Add request/response `record`s to `api/Dtos.java`, with Jakarta validation annotations.
3. Add a thin controller method. Admin: `me.requireAdmin()` first.
4. Put the logic in a `@Transactional` service method. Throw `ApiException` with a new stable
   `code`. Publish via `outbox.publish(...)` if other services care.
5. If guests may read it: add the path to that service's `radius.security.public-get-paths`, and
   to the gateway `AuthenticationFilter.PUBLIC_GET` if the prefix is not already covered. If
   it's a new path prefix, add it to a gateway route in `api-gateway/.../application.yml`.
6. Tests: a domain unit test for the rule; a `@WebMvcTest` only for status codes and auth.
7. Clients: type in `lib/types.ts`, helper in `lib/api.ts` (both apps if both use it), query hook.
8. Update [06 · API reference](06-api-reference.md).

The `add-endpoint` skill walks through this step by step.

### 6.2 Add or change an event

See [07 §10](07-events-and-messaging.md#10-adding-an-event-checklist) and the `add-event` skill.

### 6.3 Add a column or table

New `V{n+1}__*.sql` in the owning service, update the entity (Hibernate `validate` fails on
mismatch), update the DTO if it is exposed, and update [05](05-data-model.md).

### 6.4 Add a listing kind

A kind is a `text` value with no DB `CHECK`, so this is mostly a code change:

- `Listing.Kind` enum.
- `@Pattern` in `CreateListingRequest` and `SearchRequest.kinds`.
- `QueryParser` verb → kind mapping.
- Fee override row if needed.
- `ListingKind`, `KIND_LABEL` and `KINDS` in **both** `lib/types.ts` / `lib/format.ts`.
- The create flows and the docs ([01](01-product-vision.md#the-seven-listing-kinds),
  [04](04-domain-model.md#3-listings-listing-service)).

### 6.5 Add a notification

Publish `NotificationRequested(userId, "push", kind, title, body, deepLink, now)` from the
service where the thing happened, in the same transaction. No change to notification-service is
needed.

### 6.6 Make yourself admin locally

Admin is whoever `RADIUS_ADMIN_PHONE` names. user-service promotes that account at start-up, and
it takes effect at the next sign-in.

- **Docker-free scripts:** default `+491700000001`, set by `scripts/services.ps1`.
- **Docker Compose:** ⚠ `infra/docker-compose.yml` does **not** pass `RADIUS_ADMIN_PHONE` to
  user-service, so **nobody is admin** (the log says *"no radius.admin-phone set"*). Add
  `RADIUS_ADMIN_PHONE: "+491700000001"` under `user-service.environment` in your local Compose
  file and recreate the container. Then sign out and back in. Tracked in
  [13](13-known-issues.md#developer-experience).

## 7. Definition of done

| Changed | Run | Must be |
| --- | --- | --- |
| Any Java / SQL | `.\scripts\mvn-docker.ps1 test` (or `-pl <svc> -am test`) | Green. Report the real counts |
| Web | `cd apps\web; npm run typecheck` (+ `npm run lint` once ESLint is configured) | Clean |
| Mobile | `cd apps\mobile; npm run typecheck` | Clean |
| Behaviour across services | Run the stack, exercise the path, watch Kafka UI | Observed, not assumed |
| Shapes (endpoint / table / event / env) | Update `docs/` | Same PR |
| Status of a planned item | `/update-record` → `poc.md` | Ticked only if built **and exercised** |

Plus the [11 non-negotiables](../CLAUDE.md#non-negotiables). Breaking one is a bug even when the
tests pass.

## 8. Claude Code tooling

The repo ships configuration for Claude Code under `.claude/`. It is optional for humans but
documents the team's workflow.

| Kind | Name | What it does |
| --- | --- | --- |
| Command | `/next-task` | Takes the first `[ ]` item in the "← start here" block of `poc.md` Part 3, cites existing code, proposes the smallest plan, and stops for a go-ahead |
| Command | `/verify [all\|services\|web\|mobile]` | Runs the build and tests for what changed, re-checks the non-negotiables and web/mobile parity, and reports failures verbatim |
| Command | `/review [base=main]` | Runs the `code-reviewer` agent on the diff, plus `security-auditor` when auth, admin, gateway, geo, photos or payments are touched |
| Command | `/update-record` | Brings `poc.md` in line with what was actually built and exercised; shows the diff first |
| Skill | `add-endpoint`, `add-event`, `run-local-stack` | Step-by-step recipes that load by task |
| Agent | `code-reviewer` | Correctness, money, service boundaries, idempotency, migrations, state machines, API contract, tests |
| Agent | `security-auditor` | 401 vs 403, token handling, admin gates, IDOR, home-visit gates, location privacy, evidence, uploads, payment idempotency |
| Hook | `guard-shell.mjs` | **Blocks** force-push, push to main, `--no-verify`, and bare `mvn` on an old JDK. **Asks** before `reset --hard`, `clean -f`, `down -v`, `DROP`/`TRUNCATE`, `devstack reset` |
| Hook | `protect-files.mjs` | **Blocks** writing `.env*` (except `.env.example`) and editing a tracked `V*.sql` migration. **Asks** for `package-lock.json` and `design/_ds/` |
| Hook | `check-invariants.mjs` | **Flags** `double`/`float`/`BigDecimal` money variables, `ST_Distance` used as a filter, `KafkaTemplate.send` outside the outbox, and booking status set outside `BookingRequest` |

Personal overrides go in `CLAUDE.local.md` and `.claude/settings.local.json` (both gitignored).

## 9. Where to start contributing

`poc.md` Part 3 is the ordered backlog. **Block B, the admin console, is "start here"**:
listings browse/search and member lookup. Several smaller, high-value fixes found while writing
these docs are in [13 · Known issues](13-known-issues.md) and make good first tasks.
