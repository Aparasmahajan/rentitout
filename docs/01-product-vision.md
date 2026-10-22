# 01 · Product vision

> **Audience:** everyone. Product, business, new developers. No code knowledge needed.
> **Source of truth for scope:** [`design/Radius Build Plan.dc.html`](../design/Radius%20Build%20Plan.dc.html).
> **Source of truth for status:** [`poc.md`](../poc.md).

## The aim in one sentence

**Radius shows you everything you can rent, buy, hire or learn from people who live near you,
and lets you arrange it with them in the app.**

You can rent a ladder, buy a printer, hire an electrician, book two hours of video editing, or
post what you need. The same person can also be the one listing things. Search covers the
members who opted in **inside a radius you choose**. It does not search the web.

## The problem

- Most households own things they use a few times a year: a drill, a ladder, a projector, a
  pressure washer. Buying one costs a lot, and it then sits idle.
- Most neighbourhoods have skills nobody knows about: an electrician three buildings away, a
  student who tutors maths, someone who edits video.
- Marketplaces such as OLX and classifieds sort by category and city, not by walking distance.
  They also push the deal off-platform, to a phone call or WhatsApp, and then disclaim it.
  So you have no record, no deposit and no recourse.

The prototype's tagline sums it up: *"Everything and everyone useful within walking distance.
Renters get things cheap; owners earn from what already sits idle."*

## Who it is for

The schema has no separate "owner" or "provider" account. **One person is a single member
who can be both customer and provider.** The build plan calls that duality *"a capability,
not a role"*.

| Persona | What they come for | Listing kinds they use |
| --- | --- | --- |
| **The borrower** | Needs a ladder for two days, not forever | browses `RENT_ITEM`, `SPACE_OR_VEHICLE` |
| **The owner of idle things** | Earns from a drill that sits in a cupboard | lists `RENT_ITEM`, `SELL_ITEM` |
| **The neighbour with a skill** | Video editing, tutoring, furniture assembly | lists `SKILL_FOR_HIRE`, `TEACHING`, `TRADE_SERVICE` |
| **The professional tradesperson** | Electrician, plumber, AC service; visits homes | lists home-visit services after an **ID check** and a **professional check** |
| **The asker** | "Anyone got a sewing machine?" | posts an `OPEN_NEED` |
| **The admin / moderator** | Reviews ID checks, reports, bans | uses `/admin` |

## What a member can do today

| Capability | Built? | Where |
| --- | --- | --- |
| Sign in with a phone number and OTP | Yes | Web and mobile |
| Browse a **nearby feed** sorted closest-first, without signing in | Yes | Web (mobile needs sign-in, see [known issues](13-known-issues.md)) |
| **Search in plain language**, e.g. "I need someone who knows video editing and lives within 5 km" | Yes | Web and mobile |
| Search with structured filters (kind, keyword, radius, sort) | Yes | Web |
| See results on a **map** | Yes | Web only |
| **List something** (7 kinds) with photo, price, deposit and tags | Yes | Web (3 steps), mobile (1 page) |
| **Request / book** a listing and see a server-computed price breakdown | Yes | Web and mobile |
| **Chat** with the other side of a booking in real time | Yes | Web and mobile |
| Accept → hand over → complete a booking. Booked days block the calendar | Yes | Web and mobile |
| Comment on a listing, rate it, report it | Yes | Web only |
| Get an **"ID checked"** badge, then a **"Professional"** badge | Yes, reviewed by hand | Web only |
| Pay in the app, have deposits held, receive payouts | **Ledger only.** The gateway is a sandbox and the fee is ₹0 | API only |
| Notifications inbox | API and stream exist; **no UI yet** | — |

## The seven listing kinds

| Kind | Example | Price shape |
| --- | --- | --- |
| `RENT_ITEM` | Aluminium ladder, 3 m | rate per unit (hour/day/week) + refundable deposit |
| `SELL_ITEM` | Laser printer | one-off buy price |
| `TRADE_SERVICE` | Flat-pack furniture assembly | rate per hour/session |
| `SKILL_FOR_HIRE` | Electrician, video editor | rate per hour/session; can be a **home visit** |
| `TEACHING` | Video editing basics, guitar | rate per session |
| `SPACE_OR_VEHICLE` | Van, weekends only | rate per day + deposit |
| `OPEN_NEED` | "Anyone got a sewing machine?" | no price; a request for supply |

## Product principles

These are the decisions the product leans on. Each one also shows up in the code.

1. **Closest first is the product.** The feed is ordered by distance. The build plan rejects
   paid promotion because *"at neighbourhood density this corrupts a feed whose entire promise
   is closest first"*.
2. **Browse freely; sign in only to act.** A guest can read the feed, search, the map and any
   listing. The sign-in wall goes up only when you ask a neighbour for something.
3. **Privacy by default.** A home location is rounded to about 100 m before it is stored.
   That is enough to say "2 km away" and not enough to find a front door. Guests see points
   snapped to a 250 m grid. A professional's **shop** is shown exactly, because it is a business
   address published on purpose.
4. **Say what was checked, not what is vouched for.** Badges say **"ID checked"** and
   **"Professional"**, never "verified". The platform states what an admin looked at. It does not
   promise anything about the quality of someone's wiring.
5. **The server does the money.** Every price, deposit, fee and total is computed on the server
   in integer paise. The apps only display them. This lets the fee be switched on later without
   changing any screen.
6. **Keep the deal on-platform.** Unlike OLX, Radius keeps the chat, the breakdown and,
   eventually, the deposit in the app. That is safer for members, and it is what will justify a
   fee when one is switched on.
7. **Each phase ships something a real neighbour can use.** Nothing depends on a later phase.

## Roadmap (phases from the build plan)

Status is taken from [`poc.md`](../poc.md) as of its last verification (27 August 2026).

| # | Phase | What it delivers | "Done when…" | Status |
| --- | --- | --- | --- | --- |
| 00 | Foundations | Accounts, phone OTP, profile, home location, tags | A new member signs up on a phone, sets their area, adds four skills and sees their own profile | ✅ Built |
| 01 | Listings + nearby feed | 7 listing kinds, photos, availability, closest-first feed | A member lists a ladder in four minutes and a neighbour 2 km away sees it | ✅ Built |
| 02 | Search, both ways | Plain-sentence and filter search, map, saved searches + daily digest | That sentence returns editors within 5 km in < 300 ms at p95 | ✅ Built |
| 03 | Requests, booking, chat | Request → accept → hand over → complete; chat; price breakdown | Two members complete a two-day ladder rental and the days show as booked | ✅ Built |
| 04 | Trust | ID check, reviews, reporting, moderation, new-account limits | Every card carries a badge and an honest rating; a reported listing is out of the index within a minute | 🟡 Partial: manual ID/pro checks, comments, ratings, reports, bans are built. Two-sided reviews and member-level trust numbers are not |
| 05 | Money | In-app payment, held deposits, payouts, the fee switch | A rental is paid in the app, the deposit held and released, and the owner sees a payout with the fee itemised | 🟡 Partial: ledger, deposits and payouts work against a sandbox gateway; fee reads ₹0 |

Work requested after the build (blocks A–E in `poc.md` Part 3):

| Block | Theme | Status |
| --- | --- | --- |
| A | Moderation plumbing: comments, ratings, reports, bans, new-account limits | ✅ Built (web only) |
| **B** | **Admin console**: browse/search listings and members | ⬜ **Next. "Start here"** |
| C | Accounts: email + password login, usernames | ⬜ |
| D | Listing lifecycle: `DRAFT` state, sale parity, "Buy" button | ⬜ |
| E | From OLX: Make Offer, favourites, pre-booking enquiry, share link, similar listings, sold badge | ⬜ |

## Business model

- **Today the platform takes nothing.** The booking screen reads *"Radius fee: none for now"*,
  and money changes hands between the two members at handover.
- The fee machinery is built in [payment-service](02-architecture.md#4-service-catalogue): a
  percentage in basis points, an optional cap, a minimum and a per-category override. It is
  stored on each payment so that changing the fee never re-prices an old booking. It reads
  **zero**, and no code path assumes it is non-zero.
- Switching it on is a configuration change plus a real payment gateway (Phase 05). The apps
  need no change, because they display the breakdown the server sends.

## Two business decisions still open

Both are flagged in the build plan and `poc.md`. **They need an owner before launch.**

1. **Launch geography.** *"Radius is worthless at low density and good at high density."*
   Pick one or two neighbourhoods and saturate them before opening a second. The code is already
   scoped by point and radius, so this is a go-to-market choice, not a technical one.
   (The design prototype is set in **Kothrud, Pune**, while the seed data and default map centre
   are **Kreuzberg, Berlin**. See [known issues](13-known-issues.md#product-and-content).)
2. **Liability on damage.** A held deposit covers a scratched grinder, not a stolen one. Decide
   whether rentals above a value threshold need a **signed handover checklist with photos**.
   It is cheap to add in Phase 03 and expensive to retrofit.

## What Radius deliberately is *not*

- **Not a web search.** Results come only from members who opted in, inside the radius.
- **Not paid placement.** Ranking is distance and relevance, never money.
- **Not a city-wide classifieds board.** Its value depends on density in a small area.
- **Not a guarantor.** Checks are factual statements about what an admin saw, not warranties.

## Glossary

| Term | Meaning |
| --- | --- |
| **Member** | Any signed-in user. Can list and request. |
| **Guest** | Not signed in. Can browse. Must send coordinates; sees coarser (250 m) locations. |
| **Listing** | One thing a member offers or needs. Has a kind, price shape, location, tags, photos. |
| **Request / booking** | A member asking for a listing for given dates and units. Has a lifecycle (state machine). |
| **Breakdown** | The server-computed price: rate × units, deposit, fee, total. |
| **Radius** | The distance (km) around a point that a search or feed covers. Default 5 km. |
| **Home point** | A member's location, rounded to ~100 m before storage. |
| **ID checked** | An admin has looked at government ID. |
| **Professional** | ID checked **and** an admin has looked at trade paperwork. Required to publish a home-visit listing. |
| **Home visit** | A listing where the provider comes to your home. Needs both checks. |
| **Minor units / paise** | Money stored as whole numbers: ₹12.50 is stored as `1250`. |
| **Basis points (bps)** | Fee percentage × 100: `250` = 2.5%. |
| **Outbox / event** | How services tell each other that something happened. See [events](07-events-and-messaging.md). |
