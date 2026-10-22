---
paths:
  - "services/**/src/test/**"
  - "services/**/service/**"
  - "services/**/domain/**"
---

# Testing

- JUnit 5 + AssertJ. Test classes are package-private, as in `class FooTest`.
- **Test names are sentences** in snake_case:
  `a_request_can_be_completed_straight_from_accepted`,
  `requireAdmin_throws_for_a_member_rather_than_returning_false`.
- Give each test class a Javadoc saying why this unit is tested directly ("this is where a
  bug costs someone money or a wasted trip").
- Priorities, highest first:
  1. state machines and transitions, including illegal ones
  2. money arithmetic (fees, breakdowns, rounding)
  3. parsing (the query parser, units like `min` vs `m`)
  4. geo rounding and privacy
  5. auth boundaries (401 vs 403, admin gates)
- Controller slice tests (`@WebMvcTest`) are for auth and status-code behaviour. Test domain
  logic directly, without a controller.
- **The known gap:** there are no integration tests over Testcontainers for the event paths
  yet. It is item 1 in the README's "What I would add next". `testcontainers.version` is
  already in the parent POM.
- Run with `.\scripts\mvn-docker.ps1 test`, or `-pl <module> -am test` for one service. Report the actual pass/fail counts, and update the
  test count in `poc.md` when it changes.
