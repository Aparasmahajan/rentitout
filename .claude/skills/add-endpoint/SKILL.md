---
name: add-endpoint
description: Use when adding or changing a REST endpoint in a Radius service and wiring it through to the web and/or mobile client — new controller method, new DTO, new admin action, new guest-readable route, or a new screen that needs data from the API.
---

# Adding an endpoint, end to end

## Service side
1. **Which service owns the data?** See the table in CLAUDE.md. Never read another service's tables; use its projection or an event.
2. **DTOs** — records in `api/Dtos.java` (or a topic file like `ModerationDtos.java`). Validate with `jakarta.validation` annotations; money as `long *Minor`.
3. **Controller** — `api/<Thing>Controller.java`. Pattern: `listing-service/.../api/ModerationController.java`.
   - Path `/api/<resource>...`; `@Operation(summary = ...)` on each method.
   - `AuthUser me` for members; `@Nullable AuthUser me` if a guest may read it.
   - Admin: `me.requireAdmin();` first line (see `AdminModerationController.java`).
   - Thin: delegate to a `service/` method.
4. **Service** — `@Transactional` method; errors as `ApiException.<kind>("snake_code", "Friendly message")`; publish events via the outbox if state changed (see the `add-event` skill).
5. **Security config** — guest-readable GET? Add it to `radius.security.public-get-paths` in that service's `application.yml`. Check the gateway routes it (`api-gateway/src/main/resources/application.yml`).
6. **Test** — domain/service logic directly; a `@WebMvcTest` only for auth/status codes (401 for no token, 403 for a member on an admin route).

## Client side (both, unless the item is explicitly one-client)
7. Types → `apps/web/lib/types.ts` **and** `apps/mobile/lib/types.ts`.
8. Call → `apps/web/lib/api.ts` **and** `apps/mobile/lib/api.ts`, using the existing request helper (it handles auth, refresh, `ApiError`).
9. Screen → TanStack Query hook in the page/screen; branch on `ApiError.code`; render money with `lib/format.ts`; theme tokens only.
10. Guest flow: if the action needs a member, send to sign-in and return to the same page.

## Verify
`/verify` — services tests + typecheck in each client touched. If the stack is up, call it through the gateway on :8080 (sign in as `+491700000001`).
