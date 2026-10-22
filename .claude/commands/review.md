---
description: Review the current branch against Radius conventions using the code-reviewer and security-auditor agents
argument-hint: "[base branch, default main]"
---

Base branch: `$ARGUMENTS` (use `main` if empty). Review `git diff <base>...HEAD` plus uncommitted changes.

1. Run the **code-reviewer** agent on the diff.
2. If the diff touches auth, `requireAdmin`, `SecurityPathProperties`/`public-get-paths`, the gateway, verification, location/geo, photos/S3, payments, or anything returning another member's data, also run the **security-auditor** agent. Run both in parallel.
3. Merge their findings, drop duplicates, and rank: correctness/money/security first, then convention, then nits.

Output: one list, each item `file:line — problem — why it matters here — suggested fix`. End with whether `poc.md` needs an update for this change.
