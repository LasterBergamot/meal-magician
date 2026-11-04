# Roadmap: Backend

Planned backend work, by `PLAN.md` phase. Move items to [[Current State: Backend]] when they land.

- **Phase 1:** done. Households (create, rename, switch, OWNER/MEMBER) landed in #41, email-bound
  invites in #42, member management in #43, the tenant-isolation guard test in #44 and the
  allowlist admin page in #45, and ending a removed person's live sessions in #59.
- **Phase 3:** done. The `planning` module (migration V7, `PlanningService`, #80), the week page
  (#97) and week editing (#98) are in [[Current State: Backend]] and [[Current State: Frontend]].
- **Phase 4:** done. The backend (#109, see [[Current State: Backend]]): copy day/week,
  `PlanTemplate` (DAY/WEEK), occasions (each carries one `OccasionType`, which `catalog` owns) and
  occasion templates. The pages (#110–#113) are in [[Current State: Frontend]].
- **Phase 5:** done (#124 to #132). The `feedback` ratings and the meal history, and the `sharing`
  share links with the public page and the print view, are in [[Current State: Backend]]. What is
  left for Phase 5 is the owner's phone checks (#134), not code.
- **Phase 6:** first the backup scripts and workflow (#62, #154, see [[Roadmap: Everything Else]]) and
  ingredients in `catalog` (#155, the backend is done and described in [[Current State: Backend]];
  the picker UI is #156). Later: data export/delete, rate limits, audit fixes.
- **Phase 7:** `assistant` module (#157 to #162). Built: the Vertex AI connection and the monthly
  spend cap (#157), the meal search index (#158), and the read-only tools, the anonymised prompts, the
  stored drafts and `PlanningService.applyDraft` / `RatingService.recent` (#159); see
  [[Current State: Backend]]. Still to come: the week and one-slot pages (#160), the chat page and
  occasion menus (#161) and the evaluation that settles the model (#162).
