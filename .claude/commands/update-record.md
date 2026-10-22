---
description: Bring poc.md (and README.md if needed) in line with what this branch actually built and ran
---

Compare this branch's changes (`git diff main...HEAD`, plus uncommitted) with `poc.md`, following `.claude/rules/build-record.md`.

- Tick `[x]` only items that are built **and** were exercised in this session; mark `[~]` for partial, with one line on what is left.
- Add new design decisions to Part 2 with their reasoning.
- Update the header table (test count, last verified date) **only** with numbers from a run in this session — run `/verify` first if there isn't one.
- Add to "What has actually been run" only what was observed.
- Touch README.md only if run instructions, ports, services or the decisions summary changed.

Show the proposed diff of `poc.md` and wait for approval before writing.
