# 03 · Tech stack

> **Audience:** developers. Versions are read from `services/pom.xml`, `apps/*/package.json` and
> `infra/docker-compose.yml`, as of 14 October 2026.
> **Rule from the build plan:** *"Ask me before adding a dependency."* Every row below was a choice.

## Backend (`services/`)

| Layer | Technology | Version | Why it is here |
| --- | --- | --- | --- |
| Language | Java | **21** (`maven.compiler.release`) | Records for DTOs and events, pattern-matching `switch` in consumers, virtual-thread-ready |
| Framework | Spring Boot | **3.3.5** (parent POM) | Fixed by the build plan |
| Cloud | Spring Cloud | **2023.0.3** BOM | Gateway, circuit-breaker starters |
| Build | Maven multi-module | 3.9.x | Parent `services/pom.xml`; modules `common` + 7 services. Always `-s services/settings-central.xml` |
| Edge | Spring Cloud Gateway (reactive) | from BOM | Routing, JWT check, Redis rate limiter, CORS |
| Web | Spring MVC (servlet) | from Boot | Every service except the gateway |
| Security | Spring Security + **jjwt** | jjwt **0.12.6** | Stateless JWT; filter + argument resolver in `common` |
| Persistence | Spring Data JPA / Hibernate | from Boot | Entities; `open-in-view: false`, `ddl-auto: validate` |
| Spatial / text SQL | Native SQL over PostGIS + `tsvector` | — | Distance and full-text are done in the database, not in Java |
| Migrations | Flyway (+ `flyway-database-postgresql`) | from Boot | One history per service DB; `V{n}__name.sql`, immutable once applied |
| Messaging | Spring for Apache Kafka | from Boot | String key/value, JSON payloads, `acks=all`, idempotent producer |
| Cache / KV | Spring Data Redis | from Boot | Rate limits, OTP, idempotency keys, posting limits, parse cache |
| Object storage | AWS SDK v2 `s3` | BOM **2.28.29** | Pre-signed PUT URLs against MinIO (path-style) |
| Resilience | resilience4j (`resilience4j-spring-boot3` + AOP) | from Cloud BOM | Circuit breakers on booking's sync calls |
| Real-time | Spring WebSocket + STOMP (simple broker) | from Boot | Chat at `/ws`, topic `/topic/threads/{id}` |
| Push to browser | `SseEmitter` | from Boot | `GET /api/notifications/stream` |
| API docs | springdoc-openapi | **2.6.0** | `/swagger-ui.html`, `/v3/api-docs` per service |
| Observability | Actuator, Micrometer Prometheus, Micrometer Tracing → OTel OTLP | from Boot | Traces to Jaeger; metrics endpoint exposed (nothing scrapes it yet) |
| Testing | JUnit 5, AssertJ, Spring Boot Test, spring-security-test, H2 (user-service only) | from Boot | Testcontainers **1.20.3 is declared as a property but unused** |

**Deliberately not used:** Lombok (constructor injection with `private final` fields instead),
Eureka/Consul (DNS is enough), Elasticsearch (Postgres FTS + PostGIS at neighbourhood scale),
GraphQL, a service mesh. See [14 · Decision log](14-decision-log.md).

## Data and infrastructure (`infra/docker-compose.yml`)

| Component | Image | Port(s) on host | Role |
| --- | --- | --- | --- |
| PostgreSQL + PostGIS | `postgis/postgis:16-3.4` | 5432 | Six databases (one per service); `postgis` + `pg_trgm` extensions |
| Redis | `redis:7-alpine` (AOF on) | 6379 | Rate limits, OTP, idempotency, parse cache |
| Kafka | `apache/kafka:3.8.1` (KRaft, single node) | 9094 (external) | Event bus; topics auto-created |
| Kafka UI | `provectuslabs/kafka-ui:latest` | 8090 | Inspect topics, consumer groups, lag |
| MinIO | `minio/minio:latest` (+ `minio/mc` setup job) | 9000 API / 9001 console | Bucket `radius-photos`, anonymous download |
| Jaeger | `jaegertracing/all-in-one:1.62.0` | 16686 UI, 4318 OTLP | Distributed traces |
| Service runtime | `eclipse-temurin:21-jre-alpine` | gateway 8080 only | `-XX:MaxRAMPercentage=75 -XX:+UseZGC`, non-root user |

⚠ Three images float on `latest` (`kafka-ui`, `minio`, `mc`). Pin them before relying on
reproducible builds.

## Web client (`apps/web`)

| Concern | Technology | Version |
| --- | --- | --- |
| Framework | Next.js App Router | ^15.0.3 |
| UI | React / React DOM | ^19.0.0 |
| Language | TypeScript (strict) | ^5.6.3 |
| Server state | TanStack Query | ^5.59.16 |
| Real-time chat | `@stomp/stompjs` | ^7.0.0 |
| Styling | Hand-written CSS custom properties (`app/globals.css`), the **"Postcard"** design | — |
| Fonts | Bodoni Moda (display, prices) + Karla (body), via Google Fonts `@import` | — |
| Map | `components/RadiusMap.tsx`, a hand-built, tile-free equirectangular projection | — |
| PWA | `public/manifest.webmanifest` + SVG icon (no service worker) | — |
| Lint | `next lint`, ⚠ **no ESLint dependency or config present** | — |

Every page is a client component (`'use client'`). Server components and cookie sessions are a
planned step. See [08 · Frontend](08-frontend.md).

## Mobile client (`apps/mobile`)

| Concern | Technology | Version |
| --- | --- | --- |
| Runtime | Expo SDK | ~52.0.0 |
| Navigation | expo-router (file-based, tabs) | ~4.0.9 |
| UI | React Native / React | **0.76.3 / 18.3.1** (pinned by Expo 52; use `npm run fix-deps`, do not bump by hand) |
| Server state | TanStack Query | ^5.59.16 |
| Secure storage | expo-secure-store (tokens) | ~14.0.0 |
| Device | expo-location, expo-image-picker, expo-constants, expo-linking | SDK-52 versions |
| Real-time chat | `@stomp/stompjs` (`forceBinaryWSFrames`, `appendMissingNULLonIncoming`) | ^7.0.0 |
| New Architecture | enabled (`newArchEnabled: true`) | — |

## Tooling

| Tool | Purpose |
| --- | --- |
| `scripts/mvn-docker.ps1` | Runs **host Maven 3.9** inside `eclipse-temurin:21-jdk`, sharing `~/.m2`. The team's default build path |
| `scripts/devstack.ps1`, `scripts/services.ps1` | Docker-free stack with portable binaries in `%LOCALAPPDATA%\radius-dev` |
| `infra/seed/seed.mjs` | Node script that seeds through the **public API** (proves OTP → JWT → outbox → Kafka → projection) |
| `.claude/` | Claude Code commands (`/verify`, `/review`, `/next-task`, `/update-record`), skills, review agents, and guard hooks. See [09](09-development-guide.md#8-claude-code-tooling) |

## Upgrade notes

- **Spring Boot 3.3 → 3.4/3.5:** check the Spring Cloud BOM compatibility first (2023.0.x pairs
  with Boot 3.2/3.3; 3.4 needs 2024.0.x). The gateway is the most sensitive module.
- **Expo:** upgrade the SDK with `npx expo install expo@^NN && npm run fix-deps`. React and RN
  versions follow the SDK.
- **Next.js / React 19:** the web app uses no server components yet, so upgrades are low-risk.
  Run `npm run typecheck` and do a production `next build` (which currently fails on `/sign-in`;
  see [13](13-known-issues.md)).
