# 11 · Configuration and operations

> **Audience:** developers and whoever runs the stack. Read from every `application.yml`,
> `infra/docker-compose.yml`, `services/Dockerfile` and `scripts/*.ps1`, 14 October 2026.

## 1. Ports

| Component | Port | Published by Compose? | Docker-free port |
| --- | --- | --- | --- |
| api-gateway | 8080 | ✅ | 8080 |
| user-service | 8081 | ❌ (internal) | 8081 |
| listing-service | 8082 | ❌ | 8082 |
| search-service | 8083 | ❌ | 8083 |
| booking-service (HTTP + `/ws`) | 8084 | ❌ | 8084 |
| payment-service | 8085 | ❌ | 8085 |
| notification-service | 8086 | ❌ | 8086 |
| PostgreSQL | 5432 | ✅ | **5433** |
| Redis | 6379 | ✅ | **6380** |
| Kafka | 9094 external (9092 in-network) | ✅ 9094 | **9092** |
| MinIO API / console | 9000 / 9001 (`MINIO_PORT` / `MINIO_CONSOLE_PORT` override the host side) | ✅ | **9010 / 9011** |
| Kafka UI | 8090 | ✅ | — |
| Jaeger UI / OTLP | 16686 / 4318 | ✅ | — |
| Web (Next dev) | 3000 | host | 3000 |

## 2. Environment variables

| Variable | Default | Used by | Purpose |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | — (Compose: `dev`) | all | `dev`: DEBUG logging for `io.radius`; user-service also loads `db/seed` and exposes the OTP |
| `DB_HOST` / `DB_PORT` | `localhost` / `5432` | 6 services | Postgres |
| `DB_NAME` | `radius_<service>` | 6 services | Per-service database |
| `DB_USER` / `DB_PASSWORD` | `radius` / `radius` | 6 services | Credentials |
| `DB_OPTIONS` | empty | 6 services | Appended to the JDBC URL. The Docker-free stack uses `?sslmode=disable` |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | all incl. gateway | Rate limits, OTP, idempotency |
| `KAFKA_BOOTSTRAP` | `localhost:9094` (Compose: `kafka:9092`) | all except gateway | Kafka |
| `JWT_SECRET` | `dev-only-secret-change-me-at-least-32-bytes-long` | **all** (must match) | HMAC key, ≥ 32 bytes or start-up fails. **Override outside dev** |
| `RADIUS_ADMIN_PHONE` | empty (scripts: `+491700000001`) | user | Promotes this phone to ADMIN at start-up. ⚠ Not set in Compose |
| `OTP_EXPOSE_CODE` | `false` (dev profile forces `true`) | user | Return the OTP in the API response. **Never true in production** |
| `S3_BUCKET` | `radius-photos` | listing | Photo bucket |
| `S3_REGION` | `us-east-1` | listing | Presigner region |
| `S3_PUBLIC_ENDPOINT` | `http://localhost:9000` | listing | Endpoint the **browser** can reach; URLs are signed against it (path-style) |
| `S3_ENDPOINT` | `http://localhost:9000` | listing | Declared, but not read by any code today |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | `radius` / `radius-secret` | listing | MinIO credentials |
| `USER_SERVICE_URL` … `NOTIFICATION_SERVICE_URL` | `http://<svc>:808x` | gateway | Route targets |
| `BOOKING_SERVICE_WS_URL` | `ws://booking-service:8084` | gateway | `/ws/**` route |
| `LISTING_SERVICE_URL` / `PAYMENT_SERVICE_URL` | `http://localhost:8082` / `:8085` (Compose sets service names) | booking | Sync clients |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://jaeger:4318/v1/traces` | all | Trace export. From an IDE on the host, set `http://localhost:4318/v1/traces` |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75 -XX:+UseZGC -XX:+ExitOnOutOfMemoryError` | containers | JVM flags |
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080` | web | Gateway URL; the WebSocket URL is derived from it |
| `EXPO_PUBLIC_API_URL` | emulator defaults | mobile | Gateway URL from a device (LAN IP on a real phone) |
| `API_URL` | `http://localhost:8080` | `seed.mjs` | Seed target (or `--api`) |
| `RADIUS_MAVEN_HOME` | — | `mvn-docker.ps1` | Host Maven 3.9 folder |
| `RADIUS_DEV_HOME` | `%LOCALAPPDATA%\radius-dev` | `devstack.ps1` | Docker-free install folder; keep it off OneDrive |

### Application properties worth knowing

| Property | Default | Where |
| --- | --- | --- |
| `radius.security.public-paths` / `public-get-paths` | per service | which paths skip auth (all methods / GET only) |
| `radius.jwt.access-ttl` / `refresh-ttl` | `15m` / `30d` | user-service |
| `radius.outbox.enabled` / `interval-ms` / `cleanup-cron` | `true` / `500` / `0 15 3 * * *` | all producers (notification: `false`) |
| `radius.booking.expiry-interval-ms` | `60000` | booking |
| `radius.search.digest-cron` | `0 0 8 * * *` | search |
| `radius.search.llm.enabled` | `false` | search. ⚠ `true` stops start-up (no implementation) |
| `radius.notify.push.enabled` / `sms.enabled` | `true` / `false` | notification |
| `resilience4j.circuitbreaker.*` | window 20, 50%, 10 s | booking |
| `spring.jpa.hibernate.ddl-auto` | `validate` | all. Flyway owns the schema |
| Hikari pool | user 10, listing 15, search 15, booking 15, payment 10, notification 8 | per service |
| `management.tracing.sampling.probability` | `1.0` | all (lower it in production) |

## 3. Observability

| Signal | Where | Notes |
| --- | --- | --- |
| Health | `/actuator/health`, `/health/liveness`, `/health/readiness` | Probes enabled everywhere. ⚠ Compose defines **no healthcheck for app containers** |
| Metrics | `/actuator/prometheus` | Exposed on every service; **nothing scrapes it** yet |
| Circuit breakers | `/actuator/circuitbreakers` (booking) | Also part of booking's health |
| Gateway routes | `/actuator/gateway` | ⚠ Unauthenticated (GlobalFilters don't apply to actuator) |
| Traces | Jaeger <http://localhost:16686> | Micrometer Tracing → OTel → OTLP HTTP, 100% sampled |
| Logs | `docker compose logs -f <svc>` | `dev` profile logs `io.radius` at DEBUG; `userId` is put in the MDC by `JwtAuthFilter` |
| Kafka | Kafka UI <http://localhost:8090> | Topics, messages with headers, consumer-group lag |
| API docs | `:808x/swagger-ui.html`, `/v3/api-docs` | Per service (not via the gateway) |

**Backlog (from `poc.md`):** Prometheus + Grafana in the infra profile with one dashboard per
service, a consumer-lag alert, and a dead-letter topic.

## 4. Runbook

### Start / stop

```powershell
cd infra
docker compose --profile all up -d --build      # everything
docker compose --profile infra up -d            # backing services only
docker compose ps
docker compose logs -f booking-service
docker compose down                              # stop, keep data
# docker compose down -v                         # ⚠ WIPES ALL DATA, ask first
```

### Health check, all services

```powershell
docker compose -f infra/docker-compose.yml ps
curl http://localhost:8080/actuator/health
docker compose -f infra/docker-compose.yml exec user-service wget -qO- localhost:8081/actuator/health
```

### Reseed

`node infra\seed\seed.mjs` is safe to re-run against an existing stack (OTP sign-in finds
existing members). It creates listings again, so for a clean demo use `down -v` (with care) and
then seed.

### Rebuild the search index

1. `docker compose stop search-service`
2. In `radius_search`: `TRUNCATE listing_doc, member_doc;`
3. Kafka UI → Consumers → `search-service.listing` and `search-service.user` → *Reset offsets* →
   **Earliest**.
4. `docker compose start search-service` and watch the logs.

### Inspect a stuck outbox

```sql
-- in the producing service's DB
SELECT topic, event_type, attempts, created_at
FROM outbox_event WHERE published_at IS NULL ORDER BY created_at LIMIT 20;
```

The oldest row with a climbing `attempts` count is blocking that service's outbox. Read the
service log for the send error.

### Rotate the JWT secret

Set the same new `JWT_SECRET` on **every** service and the gateway, then restart all of them
together. Existing access tokens become invalid at once; clients will refresh. Refresh tokens are
signed with the same key, so every member must sign in again.

## 5. Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `docker info` fails: *cannot find dockerDesktopLinuxEngine* | Docker Desktop not running | Start it. Don't work around it |
| `mvn` blocked by a hook | Old JDK/Maven on `PATH` | Use `.\scripts\mvn-docker.ps1` |
| Maven cannot resolve Spring Boot | Global `settings.xml` points at an internal mirror | Scripts pass `services/settings-central.xml`; by hand use `-s` |
| Single-module build cannot find `radius-common` | `common` is not installed to `~/.m2` | Use `-pl <svc> -am` |
| Flyway: *checksum mismatch* | An applied migration was edited | Revert the edit; add `V{n+1}` instead |
| Hibernate: *Schema-validation: missing column* | Entity changed without a migration | Add the migration |
| Service up but feed empty | Seed not run, or no location sent | `node infra\seed\seed.mjs`; guests must send lat/lon |
| `no_location` for a member | Profile has no home point | Set it in `/me` |
| Search misses a new listing | search-service consumer lag or down | Kafka UI lag for `search-service.listing`; service logs |
| `/admin` says "Not for you" under Docker | `RADIUS_ADMIN_PHONE` not passed to user-service | See [09 §6.6](09-development-guide.md#66-make-yourself-admin-locally) |
| Chat shows "reconnecting…" | WebSocket upgrade refused at the gateway (no bearer header on the upgrade), or token expired | Messages still arrive via REST on reload. See [13](13-known-issues.md#security-and-api) |
| 429 during seeding | Gateway OTP bucket (1/s, burst 5) | The seed backs off and retries; wait |
| `The connection attempt failed` (Docker-free) | Endpoint security breaks loopback SSL | `DB_OPTIONS=?sslmode=disable` (scripts do this) |
| Redis health `DOWN` but Redis works (Docker-free) | Windows Redis `INFO` path parsing | `MANAGEMENT_HEALTH_REDIS_ENABLED=false` locally only |

More Windows-specific workarounds are in the root [README](../README.md#things-a-locked-down-windows-box-does-that-the-scripts-already-work-around).

## 6. Before a real deployment (checklist)

Nothing deploys to a cloud yet. Before it does:

- [ ] Real `JWT_SECRET` (and consider asymmetric keys so services verify without signing).
- [ ] `OTP_EXPOSE_CODE=false`, `dev` profile off, and a real SMS provider behind `OtpService`.
- [ ] Per-service DB users with rights only on their own database (today one superuser).
- [ ] CORS origins for the real web domain (today localhost and `exp://` only).
- [ ] Pin `latest` images; app container healthchecks; resource limits.
- [ ] Kafka topics created with partitions and replication; DLQ and lag alerting.
- [ ] Broker relay for STOMP, shared fan-out for SSE, ShedLock on `@Scheduled` jobs.
- [ ] Webhook signature verification; gateway public path for `/api/webhooks/**`.
- [ ] Tracing sampling < 100%; Prometheus scraping; log aggregation.
- [ ] Restrict `/actuator/*` (including the gateway's) to the internal network.
- [ ] Backups for Postgres; lifecycle rules for evidence objects (`purge_after`).
- [ ] Resolve the open items in [13](13-known-issues.md) marked **High**.
