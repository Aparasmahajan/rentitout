---
paths:
  - "poc.md"
  - "README.md"
---

# Keeping poc.md and README.md honest

`poc.md` is the single build record. `README.md` covers how to run the project, plus a short
version of the decisions.

- Use the status legend `[x]` built, `[~]` partial and `[ ]` not started. Flip an item only
  when it is built **and exercised**.
- The header table (phases, test count, last verified) states facts. Update the date and
  numbers only after actually running the build and tests.
- "What has actually been run" lists observed behaviour against the running stack, never
  "should work". If you could not run something, don't add it.
- Part 3 is ordered. When finishing an item, tick it, move "← start here" if the block is done,
  and note any follow-up it uncovered under the same block.
- Record the *why* for any new design decision in Part 2, in the existing voice: plain
  sentences, the trade-off named, and no marketing.
- Dates are absolute ("27 August 2026"), never "yesterday".
