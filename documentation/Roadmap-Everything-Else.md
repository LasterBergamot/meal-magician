# Roadmap: Everything Else

Planned cross-cutting work, by `PLAN.md` phase. Move items to [[Current State: Everything Else]]
when they land.

- **Phase 0:** GitHub repo, labels, milestones, board, epics/issues; `compose.yml` (Postgres + pgvector) and `compose.e2e.yml` (stub OIDC),
  Dockerfile and the E2E + Trivy steps are in [[Current State: Everything Else]] (#15); Google OAuth clients ([[Auth Setup]]); Cloud Run + Neon
  ([[Hosting Setup]]); sign in on a phone on the deployed v0.1.0 and check scale to zero (#18; the release itself is done, see [[Current State: Everything Else]]).
- **Phase 6:** backups (#62): a nightly `db-backup.yml` that dumps the Neon database, checks that
  the dump restores, encrypts it with `age` and keeps it in a Cloud Storage bucket in Frankfurt
  (under a cent a month), plus `task db:restore` and a restore drill; it comes after the last
  regular feature and before the AI assistant. Also the cost and cold-start review (JVM vs native
  image).
- **Later:** custom domain.
