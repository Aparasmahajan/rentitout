import { StyleSheet } from 'react-native';

/**
 * The "Postcard" palette, mirrored from apps/web/app/globals.css.
 *
 * Light is the default. Dark is opt-in through the toggle on the You tab —
 * deliberately not driven by the OS setting, so a first launch always lands on
 * light. `useThemedStyles()` returns the sheet for whichever is active.
 */

export interface Palette {
  bg: string;
  surface: string;
  card: string;
  raised: string;
  ink: string;
  inkSoft: string;
  muted: string;
  faint: string;
  rule: string;
  ruleStrong: string;
  accent: string;
  accentInk: string;
  accentWash: string;
  ok: string;
  danger: string;
  warn: string;
  onPhoto: string;
}

export const LIGHT: Palette = {
  bg: '#FBFAF7',
  surface: '#FFFFFF',
  card: '#FFFFFF',
  raised: '#F4F2EC',
  ink: '#131313',
  inkSoft: '#3D3A35',
  muted: '#6E6A63',
  faint: '#9A958C',
  rule: '#E4E1DA',
  ruleStrong: '#131313',
  accent: '#7A4B2A',
  accentInk: '#FFFFFF',
  accentWash: '#F3ECE5',
  ok: '#2F4C3A',
  danger: '#8C2F2F',
  warn: '#7A5B18',
  onPhoto: '#FFFFFF',
};

export const DARK: Palette = {
  bg: '#12110F',
  surface: '#1A1917',
  card: '#1A1917',
  raised: '#232120',
  ink: '#F2EFE9',
  inkSoft: '#DDD8D0',
  muted: '#9A958C',
  faint: '#6F6A63',
  rule: '#2B2825',
  ruleStrong: '#F2EFE9',
  accent: '#D09163',
  accentInk: '#1A1310',
  accentWash: '#2A211B',
  ok: '#7FAE8E',
  danger: '#D98B8B',
  warn: '#D8BB6A',
  onPhoto: '#FFFFFF',
};

export const space = { 1: 4, 2: 8, 3: 12, 4: 16, 5: 22, 6: 30, 7: 44 } as const;
export const radius = { sm: 2, md: 4, lg: 6, pill: 999 } as const;

/**
 * Bodoni is a webfont; on a device it needs expo-font and the .ttf shipped in
 * assets. Until that is wired, the platform serif carries the display role —
 * the closest metric match available without a download at launch.
 */
export const font = {
  display: undefined as string | undefined,
  body: undefined as string | undefined,
};

export function makeStyles(c: Palette) {
  return StyleSheet.create({
    screen: { flex: 1, backgroundColor: c.bg },
    content: { padding: space[4], gap: space[4], paddingBottom: space[7] * 2 },

    display: { fontSize: 34, color: c.ink, letterSpacing: -0.8, fontWeight: '400', lineHeight: 38 },
    h1: { fontSize: 28, fontWeight: '700', color: c.ink, letterSpacing: -0.6 },
    h2: { fontSize: 21, fontWeight: '700', color: c.ink, letterSpacing: -0.3 },
    h3: { fontSize: 17, fontWeight: '600', color: c.ink },
    body: { fontSize: 15, color: c.ink, lineHeight: 22 },
    muted: { fontSize: 13, color: c.muted },
    eyebrow: {
      fontSize: 11,
      letterSpacing: 1.6,
      textTransform: 'uppercase',
      color: c.faint,
      fontWeight: '600',
    },
    price: { fontSize: 22, fontWeight: '700', color: c.ink, letterSpacing: -0.4 },

    row: { flexDirection: 'row', alignItems: 'center', gap: space[3] },
    between: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'space-between',
      gap: space[3],
    },
    wrap: { flexDirection: 'row', flexWrap: 'wrap', gap: space[2] },
    stack: { gap: space[3] },
    grow: { flex: 1 },

    card: {
      backgroundColor: c.card,
      borderColor: c.rule,
      borderWidth: 1,
      borderRadius: radius.md,
      overflow: 'hidden',
    },
    cardBody: { padding: space[4], gap: space[2] },

    /* The photo tile: image, scrim, words on top. */
    tile: {
      borderRadius: radius.md,
      overflow: 'hidden',
      backgroundColor: c.raised,
      position: 'relative',
    },
    tileImage: { width: '100%', height: '100%' },
    tileBody: {
      position: 'absolute',
      left: 0,
      right: 0,
      bottom: 0,
      padding: space[4],
      gap: 5,
      backgroundColor: 'rgba(0,0,0,0.42)',
    },
    onPhoto: { color: c.onPhoto },

    chip: {
      minHeight: 40,
      paddingHorizontal: 16,
      paddingVertical: 9,
      borderRadius: radius.pill,
      borderWidth: 1,
      borderColor: c.rule,
      justifyContent: 'center',
    },
    chipOn: { backgroundColor: c.ink, borderColor: c.ink },
    chipText: { fontSize: 13, color: c.inkSoft },
    chipTextOn: { color: c.bg, fontWeight: '600' },

    btn: {
      minHeight: 48,
      backgroundColor: c.accent,
      borderRadius: radius.pill,
      paddingVertical: 13,
      paddingHorizontal: 22,
      alignItems: 'center',
      justifyContent: 'center',
      borderWidth: 1,
      borderColor: c.accent,
    },
    btnGhost: { backgroundColor: 'transparent', borderColor: c.ruleStrong },
    btnQuiet: { backgroundColor: 'transparent', borderColor: c.rule },
    btnDanger: { backgroundColor: 'transparent', borderColor: c.danger },
    btnText: { color: c.accentInk, fontWeight: '700', fontSize: 14, letterSpacing: 0.4 },
    btnTextGhost: { color: c.ink, fontWeight: '700', fontSize: 14, letterSpacing: 0.4 },
    btnTextDanger: { color: c.danger, fontWeight: '700', fontSize: 14 },
    disabled: { opacity: 0.45 },

    input: {
      minHeight: 48,
      borderWidth: 1,
      borderColor: c.rule,
      borderRadius: radius.md,
      paddingHorizontal: 13,
      paddingVertical: 12,
      backgroundColor: c.surface,
      color: c.ink,
      fontSize: 15,
    },

    badge: {
      paddingHorizontal: 9,
      paddingVertical: 3,
      borderRadius: radius.pill,
      borderWidth: 1,
      borderColor: c.rule,
    },
    badgeText: {
      fontSize: 10,
      letterSpacing: 1,
      textTransform: 'uppercase',
      color: c.muted,
      fontWeight: '600',
    },
    badgeOk: { borderColor: c.ok },
    badgeOkText: { color: c.ok },
    badgePro: { backgroundColor: c.ink, borderColor: c.ink },
    badgeProText: { color: c.bg },

    notice: {
      padding: space[4],
      borderWidth: 1,
      borderColor: c.rule,
      borderLeftWidth: 3,
      borderLeftColor: c.accent,
      borderRadius: radius.md,
      backgroundColor: c.accentWash,
      gap: space[2],
    },

    error: {
      borderWidth: 1,
      borderColor: c.danger,
      borderRadius: radius.md,
      padding: space[3],
    },
    errorText: { color: c.danger, fontSize: 14 },

    bubble: {
      maxWidth: '80%',
      padding: space[3],
      borderRadius: radius.lg,
      backgroundColor: c.raised,
      alignSelf: 'flex-start',
    },
    bubbleMine: { backgroundColor: c.accent, alignSelf: 'flex-end' },

    rule: { height: 1, backgroundColor: c.rule },

    /* Names the screens already use. Kept as aliases so the whole app did not
       have to be renamed alongside the redesign. */
    tiny: {
      fontSize: 11,
      letterSpacing: 1.6,
      textTransform: 'uppercase',
      color: c.faint,
      fontWeight: '600',
    },
    thumb: { width: '100%', aspectRatio: 4 / 3, backgroundColor: c.raised },
    thumbPlaceholder: {
      width: '100%',
      aspectRatio: 4 / 3,
      backgroundColor: c.raised,
      alignItems: 'center',
      justifyContent: 'center',
    },
    avatar: {
      width: 48,
      height: 48,
      borderRadius: radius.pill,
      backgroundColor: c.accentWash,
      alignItems: 'center',
      justifyContent: 'center',
    },
  });
}

export type Styles = ReturnType<typeof makeStyles>;

/** Deterministic wash for a listing with no photo, matching the web tiles. */
const WASHES = [
  ['#C4B79E', '#6E6252'],
  ['#8E9BA8', '#3D4854'],
  ['#C0A98C', '#5C4A33'],
  ['#A3A79C', '#4A4E45'],
  ['#99A891', '#434C3E'],
  ['#B3A5A0', '#544A46'],
];

export function washFor(seed: string): [string, string] {
  let hash = 0;
  for (let i = 0; i < seed.length; i += 1) hash = (hash * 31 + seed.charCodeAt(i)) >>> 0;
  return WASHES[hash % WASHES.length] as [string, string];
}
