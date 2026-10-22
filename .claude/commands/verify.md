---
description: Build and test everything the current change touches, and report the real results
argument-hint: "[all|services|web|mobile]"
---

Verify the working tree. Scope is `$ARGUMENTS`; if empty, infer it from `git diff --name-only main...HEAD` plus uncommitted changes.

1. **services/** changed (or scope `all|services`): run `.\scripts\mvn-docker.ps1 test` in PowerShell (host Maven, JDK 21 from Docker). If Docker Desktop is not running or `RADIUS_MAVEN_HOME` is unset, stop and say so — do not fall back to bare `mvn`.
2. **apps/web/** changed: in `apps/web`, run `npm run typecheck` then `npm run lint`.
3. **apps/mobile/** changed: in `apps/mobile`, run `npm run typecheck`.
4. Changed `*.java` / `*.sql`: re-read them against the Non-negotiables in CLAUDE.md (money as minor units, outbox, ST_DWithin, migration immutability, requireAdmin).
5. If an API shape changed in a service, check both `apps/web/lib` and `apps/mobile/lib` were updated.

Report as a short table: check · result · counts (e.g. "47 tests, 0 failures"). Quote the first failing output verbatim. Do not summarise a failure as a pass.
