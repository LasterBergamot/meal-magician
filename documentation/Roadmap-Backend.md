# Roadmap: Backend

Planned backend work, by `PLAN.md` phase. Move items to [[Current State: Backend]] when they land.

- **Phase 1:** the allowlist admin page (#45) is the rest of the `household` module. Households
  themselves (create, rename, switch, OWNER/MEMBER) landed in #41, email-bound invites in #42,
  member management in #43 and the tenant-isolation guard test in #44.
- **Phase 2:** `catalog` module: meals, tags, search, archive.
- **Phase 3:** `planning` module: slots per date, week queries, optimistic locking.
- **Phase 4:** copy day/week, `PlanTemplate` (DAY/WEEK), occasions and occasion templates.
- **Phase 5:** `feedback` (ratings), `sharing` (print view, share links).
- **Phase 6:** data export/delete, rate limits, audit fixes.
- **Phase 7:** `assistant` module: Spring AI (pluggable provider), ONNX embeddings → pgvector,
  preference profile, tool-calling week drafts, spend cap.
