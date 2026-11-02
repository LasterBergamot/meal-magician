-- Which occasion types a meal suits (#88, PLAN.md Phase 2): a birthday cake, a Christmas roast.
-- Portable SQL: it runs unchanged on H2 (PostgreSQL mode) and on Postgres.
--
-- The enum (BIRTHDAY, HOLIDAY, PARTY, OTHER) is owned by the catalog module, not by planning:
-- planning already depends on catalog, so the other direction would be a module cycle, and Phase 4's
-- occasions import the same enum. The values are stored as text and limited by a CHECK, like
-- meal_slot.slot_type in V7, so a new value is a deliberate migration.
--
-- Expand-only: a new table, no change to meal, so the previous release keeps working during a deploy.

-- One row per (meal, occasion type). household_id is stored too, and the composite foreign key
-- requires the meal to belong to that same household (persistence.md: join tables carry the tenant,
-- as meal_tag does in V6). There is no second side to check: the type is an enum, not a row.
CREATE TABLE meal_occasion_type (
    meal_id       BIGINT      NOT NULL,
    occasion_type VARCHAR(10) NOT NULL,
    household_id  BIGINT      NOT NULL REFERENCES household (id) ON DELETE CASCADE,
    CONSTRAINT pk_meal_occasion_type PRIMARY KEY (meal_id, occasion_type),
    CONSTRAINT ck_meal_occasion_type CHECK (occasion_type IN ('BIRTHDAY', 'HOLIDAY', 'PARTY', 'OTHER')),
    CONSTRAINT fk_meal_occasion_type_meal FOREIGN KEY (meal_id, household_id)
        REFERENCES meal (id, household_id) ON DELETE CASCADE
);
-- The library reads the household's rows in one query; it is also the tenant check on every join.
CREATE INDEX ix_meal_occasion_type_household ON meal_occasion_type (household_id);
