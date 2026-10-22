---
paths:
  - "apps/web/**"
---

# Web (Next.js 15, App Router)

- **Client-rendered today.** Pages are `'use client'`, and data comes from TanStack Query over
  `lib/api.ts`. The session is a bearer token in `localStorage` (`radius.access` /
  `radius.refresh`). Moving to server components needs cookie sessions plus a BFF. That is a
  planned step (see `poc.md`), so don't start it inside an unrelated change.
- **All HTTP goes through `lib/api.ts`** to `NEXT_PUBLIC_API_URL` (the gateway). Never call a
  service port directly. Add a typed function there, with types in `lib/types.ts`.
- Errors arrive as `ApiError { status, code, message, fields }`. Branch on `code`. `fields`
  carries per-field validation messages for forms.
- Token refresh is serialized, with one in-flight refresh at a time. Keep it that way. Five
  parallel 401s must not rotate the token five times.
- **Money:** render the server's `Breakdown` with `lib/format.ts` (en-IN grouping, ₹). Never
  add, multiply or apply a fee in the client.
- **Theme:** "Postcard". Use the CSS custom properties in `app/globals.css` (`--ink`, `--accent`,
  `--rule`, ...), and never hard-code colours. Light is the default. Dark is `data-theme="dark"`
  on `<html>` via `lib/theme.tsx`, and is deliberately **not** tied to `prefers-color-scheme`.
  Bodoni Moda is for display and prices, Karla for everything else. Near-square corners and
  hairline rules, with almost no shadow.
- Guests can browse the feed, search, the map, listings and profiles. Gate only the act of
  asking, booking, commenting or posting, and return to the same page after sign-in.
- Badge copy says "ID checked" and "Professional", never "verified".
- Check with `npm run typecheck` and `npm run lint` in `apps/web`.
