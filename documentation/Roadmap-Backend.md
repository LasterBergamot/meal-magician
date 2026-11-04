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
  ingredients in `catalog` (#155 and #156, done and described in [[Current State: Backend]]
  and [[Current State: Frontend]]). Later: data export/delete, rate limits, audit fixes.
- **Phase 7:** the `assistant` module is built (#157 to #162) and described in
  [[Current State: Backend]] and [[AI Assistant]]. What is left is not code: the owner runs the eval
  (`task ai:eval`), reads the report and signs off the chat model (D33), then sets the GitHub variables to
  switch the assistant on (Hosting Setup, Part 9).
