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
  A join table (`meal_tag`) carries `household_id` too, with composite foreign keys
  `(…_id, household_id)` to each side; the parent table gets a `unique (id, household_id)` for
  that. The database then refuses a link across households, whatever the code does.
  Repository methods take the household id explicitly (`findByIdAndHouseholdId`), and a missing
  row means 404 — never fetch by id alone and check afterwards.
- `ingredient_seed` (V12, D31) is the one table **without** `household_id`: a global, read-only
  starter list of ingredient names that the picker suggests from. Never write to it from code, and
  never join household data through it. `tag` and `ingredient` rows are created with `NameWriter`
  (`ON CONFLICT DO NOTHING`, then look up), not look-up-then-save, so two people adding the same
  new name at once both succeed.
- Postgres-only assistant tables (`meal_embedding`, V14, #158) live in `db/migration/postgresql/`
  and are read and written only through `assistant.internal.EmbeddingStore`, always filtered by
  `household_id`; the table is derived data, rebuilt lazily from the meals.
- `assistant_draft` and `assistant_draft_item` (V15, common, #159) hold the assistant's short-lived
  drafts (purged after 7 days when a new one is stored). The item carries `household_id` with
  composite foreign keys to its draft and, when `meal_id` is set, to `meal`; a check requires
  exactly one of `meal_id` and `idea_name`. A draft changes status only through the conditional
  update `DraftRepository.claim`.
- Aggregates that users edit concurrently (`meal`, `meal_slot`, `plan_template`, `occasion`,
  `occasion_template`, `household`) carry a `version` column (`@Version`).
- Child rows of a template or menu (`plan_template_item`, `occasion_item`,
  `occasion_template_item`) carry `household_id` with composite foreign keys to their parent and to
  `meal`; the `meal` side has no `ON DELETE`, so a used meal cannot be deleted.
- `rating` (V10, D27) has a composite foreign key `(meal_id, household_id)` to `meal` (no
  `ON DELETE`) and deliberately **no foreign key to `meal_slot`**: a rating outlives its slot, so it
  copies the slot's date, type and meal. `(slot_id, user_id)` is unique; a second writer loses the
  insert, so write it in its own transaction and retry (`feedback.internal.RatingWriter`), because a
  failed statement aborts a Postgres transaction.
- The plan is stored **per date** (`meal_slot.date`); a week is a query over seven dates.
- Timestamps: `timestamptz` / `Instant`; dates of the plan: `date` / `LocalDate`.
- Migrations must be compatible with the previous release (expand/contract), since Flyway
  runs at startup during a deploy (PLAN.md D13).
- Hikari: small pool, `minimumIdle: 0`, so idle connections don't keep Neon awake (D11).
