#!/usr/bin/env node
/**
 * Seeds a running stack through the public API — not through SQL.
 *
 * Listing owner ids live in user-service's database, so a Flyway seed in
 * listing-service would have to guess them. Going through the API also proves
 * the whole chain works: OTP, JWT, the outbox, Kafka, and the search projection
 * all get exercised before anyone opens the app.
 *
 *   node infra/seed/seed.mjs [--api http://localhost:8080]
 */

const apiFlag = process.argv.indexOf('--api');
const API = apiFlag > -1 ? process.argv[apiFlag + 1] : (process.env.API_URL ?? 'http://localhost:8080');

const MEMBERS = [
  { phone: '+491700000001', name: 'Amara Okafor',   area: 'Kreuzberg',      lat: 52.4996, lon: 13.418 },
  { phone: '+491700000002', name: 'Jonas Meyer',    area: 'Neukölln',       lat: 52.481,  lon: 13.435 },
  { phone: '+491700000003', name: 'Priya Raman',    area: 'Kreuzberg',      lat: 52.501,  lon: 13.426 },
  { phone: '+491700000004', name: 'Tomas Novak',    area: 'Friedrichshain', lat: 52.515,  lon: 13.454 },
  { phone: '+491700000005', name: 'Sofia Almeida',  area: 'Neukölln',       lat: 52.479,  lon: 13.429 },
  { phone: '+491700000006', name: 'Erik Lindqvist', area: 'Kreuzberg',      lat: 52.496,  lon: 13.421 },
];

const LISTINGS = [
  { owner: 0, kind: 'RENT_ITEM',      title: 'Aluminium ladder, 3 m',        price: 500,  unit: 'DAY',     deposit: 2000, tags: ['ladder'],          description: 'Light enough to carry up a stairwell. Please bring it back dry.' },
  { owner: 0, kind: 'SKILL_FOR_HIRE', title: 'Electrician — small jobs',      price: 3500, unit: 'HOUR',    deposit: 0,    tags: ['electrical'],      description: 'Sockets, lights, dodgy switches. Not fuse boxes.' },
  { owner: 1, kind: 'RENT_ITEM',      title: 'Cordless drill + bit set',      price: 400,  unit: 'DAY',     deposit: 3000, tags: ['drill'],           description: 'Two batteries, both hold charge.' },
  { owner: 1, kind: 'TRADE_SERVICE',  title: 'Flat-pack furniture assembly',  price: 2500, unit: 'HOUR',    deposit: 0,    tags: ['carpentry'],       description: 'I own every Allen key ever made.' },
  { owner: 2, kind: 'TEACHING',       title: 'Video editing, the basics',     price: 3000, unit: 'SESSION', deposit: 0,    tags: ['video-editing'],   description: 'Two hours, your footage, my screen. DaVinci or Premiere.' },
  { owner: 2, kind: 'SKILL_FOR_HIRE', title: 'Video editor for hire',         price: 4500, unit: 'HOUR',    deposit: 0,    tags: ['video-editing'],   description: 'Ten years of it. Weddings excepted.' },
  { owner: 3, kind: 'SPACE_OR_VEHICLE', title: 'Van, weekends only',          price: 4000, unit: 'DAY',     deposit: 15000, tags: ['moving-help'],    description: 'Fits a two-room flat if you pack properly.' },
  { owner: 3, kind: 'RENT_ITEM',      title: 'Pressure washer',               price: 900,  unit: 'DAY',     deposit: 5000, tags: ['pressure-washer'], description: 'Loud. Your neighbours will know.' },
  { owner: 4, kind: 'RENT_ITEM',      title: 'Camping gear for two',          price: 1200, unit: 'DAY',     deposit: 4000, tags: ['camping-gear'],    description: 'Tent, two mats, two bags. All dry-stored.' },
  { owner: 4, kind: 'OPEN_NEED',      title: 'Anyone got a sewing machine?',  price: null, unit: null,      deposit: 0,    tags: ['sewing-machine'],  description: 'One curtain hem. Half an hour, tops.' },
  { owner: 5, kind: 'RENT_ITEM',      title: 'Projector + 2 m screen',        price: 1500, unit: 'DAY',     deposit: 8000, tags: ['projector'],       description: 'Film-night kit. HDMI only.' },
  { owner: 5, kind: 'SELL_ITEM',      title: 'Laser printer, working',        price: null, unit: null,      deposit: 0,    tags: ['printer'],         buyPrice: 4000, description: 'Outgrew it. Toner half full.' },
];

const TAGS = [
  { owner: 0, slug: 'electrical', relation: 'has' },
  { owner: 0, slug: 'drill', relation: 'has' },
  { owner: 1, slug: 'carpentry', relation: 'has' },
  { owner: 2, slug: 'video-editing', relation: 'has' },
  { owner: 2, slug: 'video-editing', relation: 'teaches' },
  { owner: 3, slug: 'moving-help', relation: 'has' },
  { owner: 4, slug: 'gardening', relation: 'has' },
  { owner: 4, slug: 'sewing-machine', relation: 'needs' },
  { owner: 5, slug: 'photography', relation: 'has' },
];

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * The gateway rate-limits sign-in hard on purpose — five in a burst, then one a
 * second. Seeding six members trips that immediately, so this backs off and
 * retries rather than asking anyone to weaken the limit for a demo.
 */
async function call(path, { method = 'GET', body, token, attempt = 1 } = {}) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (res.status === 429 && attempt <= 8) {
    await sleep(1500 * attempt);
    return call(path, { method, body, token, attempt: attempt + 1 });
  }

  if (!res.ok) {
    const text = await res.text();
    throw new Error(`${method} ${path} → ${res.status} ${text}`);
  }
  return res.status === 204 ? null : res.json();
}

async function signIn(member) {
  const started = await call('/api/auth/otp', { method: 'POST', body: { phone: member.phone } });
  if (!started.devCode) {
    throw new Error(
      'user-service is not exposing the OTP. Start it with SPRING_PROFILES_ACTIVE=dev (or OTP_EXPOSE_CODE=true).',
    );
  }
  const tokens = await call('/api/auth/verify', {
    method: 'POST',
    body: { phone: member.phone, code: started.devCode, displayName: member.name },
  });
  return tokens.accessToken;
}

async function main() {
  console.log(`seeding ${API}`);
  const tokens = [];

  for (const member of MEMBERS) {
    const token = await signIn(member);
    tokens.push(token);
    await call('/api/me/profile', {
      method: 'PATCH',
      token,
      body: {
        displayName: member.name,
        areaLabel: member.area,
        lat: member.lat,
        lon: member.lon,
        searchRadiusKm: 5,
        openToRequests: true,
        bio: `${member.name.split(' ')[0]} from ${member.area}.`,
      },
    });
    console.log(`  member ${member.name}`);
  }

  for (const tag of TAGS) {
    await call('/api/me/tags', {
      method: 'POST',
      token: tokens[tag.owner],
      body: { slug: tag.slug, relation: tag.relation },
    }).catch((e) => console.warn(`  tag ${tag.slug} skipped: ${e.message}`));
  }

  const created = [];
  for (const listing of LISTINGS) {
    const owner = MEMBERS[listing.owner];
    const made = await call('/api/listings', {
      method: 'POST',
      token: tokens[listing.owner],
      body: {
        kind: listing.kind,
        title: listing.title,
        description: listing.description,
        priceMinor: listing.price,
        unit: listing.unit,
        depositMinor: listing.deposit,
        buyPriceMinor: listing.buyPrice ?? null,
        // Scatter listings a little around the owner's point.
        lat: owner.lat + (Math.random() - 0.5) * 0.004,
        lon: owner.lon + (Math.random() - 0.5) * 0.006,
        tags: listing.tags,
      },
    });
    created.push(made);
    console.log(`  listing ${listing.title}`);
  }

  // One live booking so the requests screen is not empty on first run.
  const ladder = created[0];
  const tomorrow = new Date(Date.now() + 2 * 86_400_000).toISOString().slice(0, 10);
  const request = await call('/api/requests', {
    method: 'POST',
    token: tokens[2],
    body: { listingId: ladder.id, startDate: tomorrow, units: 2, message: 'Hi Amara — Saturday morning if that suits?' },
  });
  await call(`/api/threads/${request.id}`, {
    method: 'POST',
    token: tokens[0],
    body: { body: 'That works. It lives in the cellar, I will bring it up.' },
  });

  console.log(`
done.
  ${MEMBERS.length} members, ${created.length} listings, 1 open request.
  Sign in on the web with ${MEMBERS[0].phone} — the code comes back in the response.
  The search index fills from Kafka a second or two later.`);
}

main().catch((e) => {
  console.error(`\nseed failed: ${e.message}`);
  process.exit(1);
});
