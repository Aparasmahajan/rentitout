---
paths:
  - "services/**/db/migration/**"
  - "services/**/db/seed/**"
  - "services/**/domain/**"
  - "services/**/repo/**"
  - "infra/db/**"
---

# Database and migrations

- **One database per service** (`radius_user`, `radius_listing`, ...). The databases are
  created in `infra/db/init/01-databases.sql`. A service's migrations touch only its own
  schema.
- Flyway versioned files: `V{n}__snake_case_description.sql`, numbered per service.
  **Never edit a committed `V` file.** Add the next number. The `protect-files` hook enforces
  this.
- Use `R__*.sql` (repeatable) only for dev seed data, as in `user-service/db/seed/R__dev_members.sql`.
- Open each migration with a comment block that says **why**: which block or phase it belongs
  to, and why the data lives in this service. Comment non-obvious columns inline. See
  `listing-service/.../V4__comments_ratings_reports.sql`.
- Primary keys are `uuid` and generated in Java. Timestamps are `timestamptz NOT NULL DEFAULT now()`.
- Money uses `bigint` `*_minor` columns. Currency is `text NOT NULL DEFAULT 'INR'`. Rows carry
  the currency they were priced in.
- Spatial data: `lat`/`lon` `double precision` plus
  `point geography(Point, 4326) GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lon, lat), 4326)::geography) STORED`
  with a GiST index. The entity maps only `lat`/`lon` doubles. Don't map spatial types in
  Hibernate.
- Prefer soft delete (`deleted_at`, `deleted_by`) for anything moderation may need as
  evidence.
- Idempotency for money uses a **unique constraint** (for example, the gateway reference). Don't
  use a cache for it.
- Index what the query filters on. Use partial indexes (`WHERE parent_id IS NOT NULL`) when
  most rows are null.
