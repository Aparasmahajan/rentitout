---
name: run-local-stack
description: Use when starting, stopping, seeding, or debugging the local Radius stack — "run the app", "build/test the services", "services won't start", "health is DOWN", "Kafka/Postgres/Redis/MinIO problem", "connection attempt failed", "seed the database", or exercising a change against the running services.
---

# Running Radius locally

The team toolchain is **host Maven 3.9.x (`RADIUS_MAVEN_HOME`) plus Java 21 and every backing
service from Docker**. Host JDKs may be older than 21, so never run bare `mvn` or `java`.

## 0. Preflight
```powershell
docker info --format '{{.ServerVersion}}'   # fails with "dockerDesktopLinuxEngine" -> ask the user to start Docker Desktop
$env:RADIUS_MAVEN_HOME                      # set via .claude/settings.local.json
```

## 1. Build and test (no stack needed)
```powershell
.\scripts\mvn-docker.ps1                         # mvn test in eclipse-temurin:21-jdk
.\scripts\mvn-docker.ps1 -DskipTests package
.\scripts\mvn-docker.ps1 -pl listing-service -am test
```
`~/.m2/repository` is mounted, so the first run downloads dependencies and later runs reuse
them. `target/` is written to the host checkout.

## 2. Bring the stack up
```powershell
cd infra; docker compose --profile all up -d --build   # infra + seven services in JDK 21 images
# or only infra:  docker compose --profile infra up -d
docker compose -f infra/docker-compose.yml ps          # wait for postgres/redis/kafka "healthy"
node infra\seed\seed.mjs                               # six members, twelve listings, one booking — via the gateway
cd apps\web; npm install; npm run dev                  # http://localhost:3000
```
Run long-lived processes (`npm run dev`, `expo start`, `logs -f`) in the background.

Sign in with `+491700000001` (also admin). The OTP comes back in the response and is in the
user-service log.

## Ports (Docker)
| | Host |
| --- | --- |
| API gateway | http://localhost:8080 — the **only** service published; services talk over the `radius` network by DNS name |
| Postgres + PostGIS | localhost:5432 · radius / radius (one DB per service, created by `infra/db/init`) |
| Redis | localhost:6379 |
| Kafka | localhost:9094 (external listener) · `kafka:9092` inside the network |
| MinIO | :9000 API, :9001 console · radius / radius-secret |
| Kafka UI | http://localhost:8090 |
| Jaeger | http://localhost:16686 |

Per-service Swagger (`:808x/swagger-ui.html`) isn't reachable from the host in the `all`
profile. Use the gateway, or `docker compose exec <svc>`.

## Diagnose
```powershell
docker compose -f infra/docker-compose.yml ps
docker compose -f infra/docker-compose.yml logs --tail 200 booking-service
docker compose -f infra/docker-compose.yml up -d --build listing-service   # rebuild one after a code change
docker compose -f infra/docker-compose.yml restart api-gateway             # ...then this: the gateway caches the old container IP
```
- A service is restarting in a loop: read its logs. It is usually Flyway (a checksum
  mismatch means a committed migration was edited) or a dependency that isn't healthy yet.
- The image build can't reach Maven Central: the corporate proxy or mirror is blocking the
  container. Report it to the user, and don't loosen `settings-central.xml`.
- MinIO won't bind on 9000: on a Zscaler laptop ZSATunnel holds 9000 and 9010. Set
  `$env:MINIO_PORT='9100'; $env:MINIO_CONSOLE_PORT='9101'` before `docker compose up`.
- A route answers 500 with `Connection refused: <svc>/<old ip>`: that service was recreated
  and the gateway still has the old address. Restart the gateway.
- Port already in use on 5432 or 6379: a local Postgres or Redis is running. Ask the user to
  stop it, rather than editing ports.

## Stop / reset
`docker compose -f infra/docker-compose.yml down` stops the stack and keeps the data.
`down -v` **deletes the volumes (all data)**, so run it only with explicit user approval.

## Machines without Docker
Use the fallback, `scripts/devstack.ps1` + `scripts/services.ps1`. It runs portable binaries
on ports 5433/6380/9092/9010. See README, "Run it without Docker", and its table of
Windows workarounds.
