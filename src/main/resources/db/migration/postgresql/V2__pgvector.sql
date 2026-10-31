-- pgvector, for the Phase 7 assistant's embeddings (PLAN.md D10). Enabling it now means RAG needs
-- no data migration later. Postgres only: H2 has no such extension.
CREATE EXTENSION IF NOT EXISTS vector;
