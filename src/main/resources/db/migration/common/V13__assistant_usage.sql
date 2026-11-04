-- What the AI assistant has used, per household and calendar month (#157, PLAN.md D32). Portable
-- SQL: it runs unchanged on H2 (PostgreSQL mode) and on Postgres.
--
-- One row per household and month, only ever changed by an atomic "x = x + n" update (see
-- UsageLedger), so two simultaneous calls cannot lose each other's usage. Money is kept as a whole
-- number of micro-USD (1 USD = 1 000 000), never as a decimal, so adding up cannot drift.
-- Contains counters only: no prompt, no answer and no person.
CREATE TABLE assistant_usage (
    household_id   BIGINT NOT NULL REFERENCES household (id) ON DELETE CASCADE,
    -- The first day of the month in the household's time zone (Europe/Budapest). Quoted because
    -- MONTH is a reserved word in H2; Postgres treats the quoted lower-case name the same.
    "month"        DATE   NOT NULL,
    input_tokens   BIGINT NOT NULL DEFAULT 0,
    output_tokens  BIGINT NOT NULL DEFAULT 0,
    cost_micro_usd BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (household_id, "month")
);
