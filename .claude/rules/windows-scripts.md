---
paths:
  - "scripts/**"
  - "infra/**"
---

# Dev-stack scripts

**The primary path is Docker.** `scripts/mvn-docker.ps1` runs the host Maven
(`RADIUS_MAVEN_HOME`) inside `eclipse-temurin:21-jdk`, and `infra/docker-compose.yml` runs
the infra and the services. Keep `mvn-docker.ps1` free of a `param` block, so Maven flags pass
through `$args`.

**The fallback for machines without Docker** is `devstack.ps1` + `services.ps1`. These scripts exist to run on a corporate Windows box with no Docker, no admin rights and
aggressive endpoint security. Each workaround in them was hit for real, is commented where it
lives, and is listed in README's table. **Don't "simplify" one away** without reading that
table.

- Unpack with `tar.exe`, not `Expand-Archive`. The antivirus scans each file `Expand-Archive`
  writes: 28 minutes against 24 seconds.
- Start processes with `Start-Process -Wait`, never `| Out-Null`. An inherited pipe handle
  hangs the pipeline forever.
- Kafka starts as a direct JVM launch with a wildcard classpath. The `.bat` launchers break
  (line length, the removed `wmic`).
- Use `DB_OPTIONS=?sslmode=disable` locally, because endpoint security breaks loopback TLS.
- Set `MANAGEMENT_HEALTH_REDIS_ENABLED=false` locally, because the Windows Redis `INFO` output
  breaks Spring's parser.
- The ports are off-default and must match across `devstack.ps1`, `services.ps1` and
  `docker-compose.yml`: Postgres 5433, Redis 6380, Kafka 9092 and MinIO 9010/9011. Docker uses
  the standard ports.
- Everything installs under `%LOCALAPPDATA%\radius-dev` (or `RADIUS_DEV_HOME`). That is never
  OneDrive, and nothing goes system-wide.
- Every Maven call passes `-s services/settings-central.xml`.
- Target Windows PowerShell 5.1: no `&&`, no `??`, no ternary.
