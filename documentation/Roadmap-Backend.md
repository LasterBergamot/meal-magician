# Roadmap: Backend

Planned backend work, by `PLAN.md` phase. Move items to [[Current State: Backend]] when they land.

- **Phase 1:** done. Households (create, rename, switch, OWNER/MEMBER) landed in #41, email-bound
  invites in #42, member management in #43, the tenant-isolation guard test in #44 and the
  allowlist admin page in #45, and ending a removed person's live sessions in #59.
- **Phase 2:** the meal pages (controllers, templates, the `TenantEndpoints` registrations, e2e)
  follow once the new app shell exists (`PLAN.md` D24, §9). The backend (the `catalog` module,
  migration V6, `MealService`) is in [[Current State: Backend]]. Still open there: the occasion
  types a meal suits, and "recently used first", which waits for the planning backend.
- **Phase 3:** `planning` module: slots per date, week queries, optimistic locking. Its backend
  (migration V7, after the Phase 2 backend because a slot points at a meal) also runs beside the
  redesign; the week view waits for the shell and the meal pages.
- **Phase 4:** copy day/week, `PlanTemplate` (DAY/WEEK), occasions and occasion templates.
- **Phase 5:** `feedback` (ratings), `sharing` (print view, share links).
- **Phase 6:** data export/delete, rate limits, audit fixes.
- **Phase 7:** `assistant` module: Spring AI (pluggable provider), ONNX embeddings → pgvector,
  preference profile, tool-calling week drafts, spend cap.
