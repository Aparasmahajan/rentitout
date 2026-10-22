# Radius documentation

Radius (repo `rentitout`) is a neighbourhood index of people, skills and things. It shows
everything you can **rent, buy, hire or learn from people near you**, inside a radius you
choose, and lets you arrange it in the app.

This folder explains the system layer by layer. Two existing files remain the authorities,
and these docs link to them rather than copying them:

| File | Role |
| --- | --- |
| [`../poc.md`](../poc.md) | **Build record.** What is built `[x]`, partial `[~]`, next `[ ]`, and why. The ordered to-do list is Part 3. |
| [`../README.md`](../README.md) | **How to run it**, including the Docker-free Windows path. |
| [`../CLAUDE.md`](../CLAUDE.md) | Agent/contributor quick guide and the **11 non-negotiables**. |
| [`../design/Radius Build Plan.dc.html`](../design/Radius%20Build%20Plan.dc.html) | Original phased plan the code implements. |

## Contents

| # | Document | What it answers | Main reader |
| --- | --- | --- | --- |
| 01 | [Product vision](01-product-vision.md) | Why does this exist, who is it for, what does it do, what is next? | Everyone |
| 02 | [Architecture](02-architecture.md) | How is the system shaped, and why? | Developers, tech lead |
| 03 | [Tech stack](03-tech-stack.md) | Which frameworks and versions, and why each one? | Developers |
| 04 | [Domain model](04-domain-model.md) | Listings, bookings, checks, moderation, money: the rules and state machines | Developers, product |
| 05 | [Data model](05-data-model.md) | Every database, table, index and projection | Developers |
| 06 | [API reference](06-api-reference.md) | Every endpoint through the gateway, auth level, error codes | Developers |
| 07 | [Events and messaging](07-events-and-messaging.md) | Kafka topics, event shapes, the outbox, consumers, sync calls | Developers |
| 08 | [Frontend](08-frontend.md) | Web (Next.js) and mobile (Expo): routes, state, auth, theme, parity | Developers |
| 09 | [Development guide](09-development-guide.md) | Set up, build, run, change things safely, recipes | New developers |
| 10 | [Testing](10-testing.md) | What is tested, how to run it, what is missing | Developers |
| 11 | [Configuration and operations](11-configuration-and-operations.md) | Env vars, ports, observability, runbook, troubleshooting | Developers, ops |
| 12 | [Security and privacy](12-security-and-privacy.md) | Auth, tokens, roles, rate limits, location privacy, threats | Tech lead, security |
| 13 | [Known issues and tech debt](13-known-issues.md) | Defects found while writing these docs, ranked | Tech lead |
| 14 | [Decision log](14-decision-log.md) | Architecture decisions in ADR form, with trade-offs | Tech lead |

## Suggested reading paths

**Product or business (30 minutes):** [01](01-product-vision.md) → the *Roadmap* and *Open
decisions* sections → [`poc.md` Part 4 (OLX comparison)](../poc.md).

**New developer, day one:** [01](01-product-vision.md) → [02](02-architecture.md) →
[09](09-development-guide.md) (get it running) → [04](04-domain-model.md) →
[`CLAUDE.md` non-negotiables](../CLAUDE.md#non-negotiables).

**Before your first backend change:** [05](05-data-model.md), [06](06-api-reference.md),
[07](07-events-and-messaging.md), [10](10-testing.md), and the
[recipes in 09](09-development-guide.md#6-recipes).

**Before your first frontend change:** [08](08-frontend.md) and [06](06-api-reference.md).

**Maintainer / tech lead:** [02](02-architecture.md), [12](12-security-and-privacy.md),
[13](13-known-issues.md), [14](14-decision-log.md).

## Keeping these docs true

- Docs live next to the code and change in the **same PR** as the code they describe.
- `poc.md` stays the status record. When a feature changes status, update `poc.md` (see
  `.claude/rules/build-record.md`), and update the matching doc here if the change alters a
  shape (an endpoint, table, event or env var).
- Facts in these docs were read from the source on **14 October 2026** (branch `addn`, based on
  `dev`). Where the code and an older doc disagree, the code wins, and the disagreement is listed
  in [13 · Known issues](13-known-issues.md#documentation-drift).
- Diagrams are Mermaid, which GitHub renders inline.
