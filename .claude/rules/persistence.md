---
paths:
  - "src/main/resources/db/**"
  - "src/main/java/**/internal/*Entity.java"
  - "src/main/java/**/internal/*Repository.java"
  - "src/main/resources/application*.yml"
---

# Persistence: entities, Flyway, H2 vs Postgres

- **Flyway owns the schema**; Hibernate runs with `ddl-auto=validate`. Never edit an applied
  migration; add a new `V<n>__<description>.sql`.
- Locations: `db/migration/common/` (portable SQL, runs on H2 and Postgres) and
  `db/migration/postgresql/` (Postgres-only: `CREATE EXTENSION vector`, vector columns,
  partial indexes). The `postgres` profile adds the second location. Keep version numbers
  unique across both folders.
- **H2 runs in PostgreSQL mode** (in memory, `dev` profile) for fast tests and `task serve`; Testcontainers Postgres is the
  source of truth and runs in CI. If something only works on H2, it's a bug.
- **Tenant scoping:** every table with household data has `household_id NOT NULL` + FK + index.
  Repository methods take the household id explicitly (`findByIdAndHouseholdId`), and a missing
  row means 404 — never fetch by id alone and check afterwards.
- Aggregates that users edit concurrently (`meal`, `meal_slot`, `plan_template`, `occasion`,
  `household`) carry a `version` column (`@Version`).
- The plan is stored **per date** (`meal_slot.date`); a week is a query over seven dates.
- Timestamps: `timestamptz` / `Instant`; dates of the plan: `date` / `LocalDate`.
- Migrations must be compatible with the previous release (expand/contract), since Flyway
  runs at startup during a deploy (PLAN.md D13).
- Hikari: small pool, `minimumIdle: 0`, so idle connections don't keep Neon awake (D11).
