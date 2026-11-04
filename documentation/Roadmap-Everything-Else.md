# Roadmap: Everything Else

Planned cross-cutting work, by `PLAN.md` phase. Move items to [[Current State: Everything Else]]
when they land.

- **Phase 0:** GitHub repo, labels, milestones, board, epics/issues; `compose.yml` (Postgres + pgvector) and `compose.e2e.yml` (stub OIDC),
  Dockerfile and the E2E + Trivy steps are in [[Current State: Everything Else]] (#15); Google OAuth clients ([[Auth Setup]]); Cloud Run + Neon
  ([[Hosting Setup]]); sign in on a phone on the deployed v0.1.0 and check scale to zero (#18; the release itself is done, see [[Current State: Everything Else]]).
- **Phase 6, in this order:**
  1. **Ingredients** on meals (#155, #156): a per-household ingredient list with a starter list of common
     Hungarian ingredients and a searchable dropdown where a missing ingredient can be typed.
  2. **Switching the AI assistant on** (built in #157 to #162, see [[AI Assistant]]): the owner runs the model
     eval (`task ai:eval`), reviews the report and signs off the chat model (D33), then sets the GitHub
     variables of Hosting Setup, Part 9, and tries the drafts on a phone.
  3. **Backups** (#62, #154), moved behind the assistant on 2026-11-04 at the owner's request: a nightly `db-backup.yml` dumps the Neon database (without the login
     sessions), checks that the dump restores and keeps it (plain, not encrypted) in a private Cloud Storage
     bucket in Frankfurt (under a cent a month). Daily copies are kept 7 days, monthly copies about 6
     months, and a copy taken before each release 14 days. A failed run opens a `Backup failed` issue,
     and a Google Cloud alert tells the owner if no backup arrives for two days (for example when GitHub
     switches the schedule off). `task db:restore` and the "Backup and Restore" guide cover getting the
     data back; there is no recurring manual drill, because the nightly job already test-restores.
     Until the owner has done the one-time setup, the backup workflow (PR #166) stays unmerged and only
     Neon's own ~6-hour restore window protects the data.
  4. The repo audit, then GDPR basics, security hardening (rate limits, headers, CSP, request size) and
     the cost and cold-start review (JVM vs native image).
- **Later:** custom domain.
