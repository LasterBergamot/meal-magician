-- The assistant's search index (#158, PLAN.md D32): one embedding vector per active meal.
-- Postgres only (pgvector, V2), so the H2 profile never has this table and the assistant is simply
-- unavailable there.
--
-- The index is derived data: it is rebuilt from the meals, lazily, inside the assistant's own
-- request (EmbeddingIndex.sync), so losing it costs only a few embedding calls.
--
-- household_id is on every row and part of the foreign key to meal, so the database refuses a row
-- whose household differs from its meal's, and deletes the row together with the meal
-- (persistence.md, tenant scoping). The model id and size are stored beside the vector so a changed
-- model is noticed instead of silently mixed with old vectors; the vector column itself is fixed to
-- 768 numbers (a different size needs a new migration).
CREATE TABLE meal_embedding (
    meal_id      BIGINT       PRIMARY KEY,
    household_id BIGINT       NOT NULL,
    -- SHA-256 of the model id, its size and the meal's text (MealDocument.hash): a different value
    -- means the meal or the model changed and the vector is stale.
    content_hash VARCHAR(64)  NOT NULL,
    model        VARCHAR(100) NOT NULL,
    dims         INT          NOT NULL,
    embedding    vector(768)  NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_meal_embedding_meal FOREIGN KEY (meal_id, household_id)
        REFERENCES meal (id, household_id) ON DELETE CASCADE
);

-- Every query starts from the household. There is deliberately no vector index: a household has
-- hundreds of meals at most, so a sequential scan is exact and fast.
CREATE INDEX ix_meal_embedding_household ON meal_embedding (household_id);
