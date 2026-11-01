# Roadmap: Backend

Planned backend work, by `PLAN.md` phase. Move items to [[Current State: Backend]] when they land.

- **Phase 1:** the rest of the `household` module: email-bound invites (#42), member management
  (#43), the reusable tenant-isolation guard test (#44), and the allowlist admin page (#45).
  Households themselves (create, rename, switch, OWNER/MEMBER) landed in #41.
- **Phase 2:** `catalog` module: meals, tags, search, archive.
- **Phase 3:** `planning` module: slots per date, week queries, optimistic locking.
- **Phase 4:** copy day/week, `PlanTemplate` (DAY/WEEK), occasions and occasion templates.
- **Phase 5:** `feedback` (ratings), `sharing` (print view, share links).
- **Phase 6:** data export/delete, rate limits, audit fixes.
- **Phase 7:** `assistant` module: Spring AI (pluggable provider), ONNX embeddings → pgvector,
  preference profile, tool-calling week drafts, spend cap.
