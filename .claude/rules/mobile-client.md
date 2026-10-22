---
paths:
  - "apps/mobile/**"
---

# Mobile (Expo 52, expo-router)

- Screens live in `app/` (file-based routes, with tabs under `app/(tabs)/`). Shared code is in
  `lib/` and mirrors `apps/web/lib` (`api.ts`, `auth.tsx`, `types.ts`, `format.ts`,
  `useThread.ts`, `useViewerPoint.ts`). Keep the two in step when an API shape changes.
- Tokens are kept in `expo-secure-store`, never `AsyncStorage`.
- The API base is `EXPO_PUBLIC_API_URL`. The Android emulator uses `10.0.2.2:8080`, the iOS
  simulator uses `localhost`, and a real device needs the machine's LAN IP.
- React is pinned to 18.3.1 and RN to 0.76.9 by Expo 52. Run `npm run fix-deps`
  (`expo install --fix`) instead of bumping versions by hand.
- Mobile lags web in some areas. It has no map screen, comments or ratings yet (`poc.md`
  tracks this), so check before you assume parity.
- The money, theme and copy rules are the same as on web: render the server breakdown and
  say "ID checked".
- Check with `npm run typecheck` in `apps/mobile`.
