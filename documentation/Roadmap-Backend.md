# Roadmap: Backend

Planned backend work, by `PLAN.md` phase. Move items to [[Current State: Backend]] when they land.

- **Phase 1:** done. Households (create, rename, switch, OWNER/MEMBER) landed in #41, email-bound
  invites in #42, member management in #43, the tenant-isolation guard test in #44 and the
  allowlist admin page in #45, and ending a removed person's live sessions in #59.
- **Phase 3:** done. The `planning` module (migration V7, `PlanningService`, #80), the week page
  (#97) and week editing (#98) are in [[Current State: Backend]] and [[Current State: Frontend]].
- **Phase 4:** copy day/week, `PlanTemplate` (DAY/WEEK), occasions (each carries one
  `OccasionType`, which `catalog` owns) and occasion templates.
- **Phase 5:** `feedback` (ratings), `sharing` (print view, share links).
- **Phase 6:** data export/delete, rate limits, audit fixes.
- **Phase 7:** `assistant` module: Spring AI (pluggable provider), ONNX embeddings → pgvector,
  preference profile, tool-calling week drafts, spend cap.
