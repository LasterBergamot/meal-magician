# Issue conventions

Read by the `new-issue` skill (from `ai-common`), and by `repo-audit` when it drafts issues.
The skill holds the procedure; this file holds the project's values.

- **Repo:** `LasterBergamot/meal-magician` (`$REPO`; `$OWNER` = `LasterBergamot`, `$NAME` = `meal-magician`).
- **Status:** the repo, labels, milestones and board don't exist yet (PLAN.md §9). Create them
  with the owner's OK, then replace the `TBD` values below.

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
- `dependencies`: Dependabot PRs.

## Milestones

- `M0 – Foundation`: PLAN.md Phase 0 (scaffold, auth, CI, Railway, first deploy v0.1.0).
- `M1 – Core planner`: Phases 1–5 → v1.0.0.
- `M2 – Hardening`: Phase 6.
- `M3 – Assistant`: Phase 7 (AI).
- `Mx – Parked`: later ideas, deliberately not planned now.

## Parent epics

One epic per PLAN.md phase (Phase 0 … Phase 7). Numbers: TBD — fill in when created, and
mirror them in PLAN.md §3.

## Board

- Project: TBD (owner `LasterBergamot`), with Auto-add for new issues.
- Fields: Area (same values as `area:*`), Priority (P0, P1, P2), Size (S ≤ half a day,
  M ≤ 2 days, L bigger), Status (`Todo` for new issues).
