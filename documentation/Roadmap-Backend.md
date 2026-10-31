# Roadmap: Backend

Planned backend work, by `PLAN.md` phase. Move items to [[Current State: Backend]] when they land.

- **Phase 0:** `identity` module (Google OIDC,
  DB allowlist + `MM_ADMIN_EMAILS`, `AppUser`), Spring Session JDBC, fail-closed prod config;
  Flyway baseline (common + postgresql/pgvector); guard tests (modularity, endpoint auth).
- **Phase 1:** `household` module: households, OWNER/MEMBER, switcher, email-bound invites,
  member management, tenant isolation, allowlist admin.
- **Phase 2:** `catalog` module: meals, tags, search, archive.
- **Phase 3:** `planning` module: slots per date, week queries, optimistic locking.
- **Phase 4:** copy day/week, `PlanTemplate` (DAY/WEEK), occasions and occasion templates.
- **Phase 5:** `feedback` (ratings), `sharing` (print view, share links).
- **Phase 6:** data export/delete, rate limits, audit fixes.
- **Phase 7:** `assistant` module: Spring AI (pluggable provider), ONNX embeddings → pgvector,
  preference profile, tool-calling week drafts, spend cap.
