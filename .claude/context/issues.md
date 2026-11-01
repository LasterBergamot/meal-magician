# Issue conventions

Read by the `new-issue` skill (from `ai-common`), and by `repo-audit` when it drafts issues.
The skill holds the procedure; this file holds the project's values.

- **Repo:** `LasterBergamot/meal-magician` (`$REPO`; `$OWNER` = `LasterBergamot`, `$NAME` = `meal-magician`).
- **Status:** the repo, labels, milestones and board (#7) exist.

## Body extras

- `## Privacy & security`: add it to issues that touch personal data, auth, invites, share
  links, external services (Google, the AI provider) or logging. Say what data is involved and
  how it is protected.
- Audit-made issues end with `_Found in the <Mon YYYY> repository audit._`

## Labels

- Exactly one `type:*`: bug, feature, chore, docs, security, epic.
- One or more `area:*`: identity, household, catalog, planning, feedback, sharing, assistant,
  ui, db, infra, ci, tooling, docs.
- One `priority:*`: `p0` fix now, `p1` next up, `p2` when convenient.
- `no-docs`: the PR has no doc impact (docs gate).
- `release:minor` / `release:major`: on a PR, the automatic release (`release.yml`, #51) raises the
  minor or major version instead of the patch. Without either, the patch rises.
- `dependencies`: Dependabot PRs.

## Milestones

- `M0 – Foundation`: PLAN.md Phase 0 (scaffold, auth, CI, hosting, first deploy v0.1.0).
- `M1 – Core planner`: Phases 1–5 → v1.0.0.
- `M2 – Hardening`: Phase 6.
- `M3 – Assistant`: Phase 7 (AI).
- `Mx – Parked`: later ideas, deliberately not planned now.

## Parent epics

One epic per PLAN.md phase, mirrored in PLAN.md §3: #1 Phase 0 Foundation, #2 Households,
#3 Meal library, #4 Weekly plan, #5 Reuse and occasions, #6 Feedback/sharing/PWA (v1.0.0),
#7 Hardening, #8 AI assistant. Each phase's sub-issues are filed when the phase starts.

## Board

- Project **#7** (owner `LasterBergamot`, linked to the repo). Turn on its **Auto-add** workflow
  in the board's Workflows settings (only possible in the UI); until then, add new issues with
  `gh project item-add 7 --owner LasterBergamot --url <issue-url>`.
- Fields: Area (same values as `area:*`), Priority (P0, P1, P2), Size (S ≤ half a day,
  M ≤ 2 days, L bigger), Status (`Todo` for new issues).
