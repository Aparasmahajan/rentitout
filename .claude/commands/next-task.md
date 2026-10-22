---
description: Pick up the next unbuilt item from poc.md Part 3 and plan it against the existing code
argument-hint: "[block letter or item text, optional]"
---

Read `poc.md` Part 3 ("What to develop next"). If `$ARGUMENTS` names a block or item, use it; otherwise take the first `[ ]` item in the block marked "← start here".

Then, before writing any code:

1. Quote the item and any notes around it (they often say "a flow, not a migration" or "already exists" — believe them, then confirm in the code).
2. Find what already exists for it: entities, columns, endpoints, events, client screens. Cite `file:line`.
3. Propose the smallest plan that fits the existing machinery: which service(s), migration (next `V` number), events, endpoints, web + mobile screens, tests.
4. Call out anything that touches a Non-negotiable in CLAUDE.md.

Stop after the plan and wait for go-ahead. When the item is later finished, run `/update-record`.
