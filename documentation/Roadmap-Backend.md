# Roadmap: Backend

Planned backend work, by `PLAN.md` phase. Move items to [[Current State: Backend]] when they land.

- **Phase 1:** done. Households (create, rename, switch, OWNER/MEMBER) landed in #41, email-bound
  invites in #42, member management in #43, the tenant-isolation guard test in #44 and the
  allowlist admin page in #45. Still open: ending a removed person's live sessions (#59).
- **Phase 2:** `catalog` module: meals, tags, search, archive. The module, its migration (V6) and
  its services start now, beside the UI redesign, because they need no page; the meal pages follow
  once the new app shell exists (`PLAN.md` D24, §9).
- **Phase 3:** `planning` module: slots per date, week queries, optimistic locking. Its backend
  (migration V7, after the Phase 2 backend because a slot points at a meal) also runs beside the
  redesign; the week view waits for the shell and the meal pages.
- **Phase 4:** copy day/week, `PlanTemplate` (DAY/WEEK), occasions and occasion templates.
- **Phase 5:** `feedback` (ratings), `sharing` (print view, share links).
- **Phase 6:** data export/delete, rate limits, audit fixes.
- **Phase 7:** `assistant` module: Spring AI (pluggable provider), ONNX embeddings → pgvector,
  preference profile, tool-calling week drafts, spend cap.
