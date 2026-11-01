# Roadmap: Everything Else

Planned cross-cutting work, by `PLAN.md` phase. Move items to [[Current State: Everything Else]]
when they land.

- **Phase 0:** GitHub repo, labels, milestones, board, epics/issues; `compose.yml` (Postgres + pgvector) and `compose.e2e.yml` (stub OIDC),
  Dockerfile and the E2E + Trivy steps are in [[Current State: Everything Else]] (#15); Google OAuth clients ([[Auth Setup]]); Cloud Run + Neon
  ([[Hosting Setup]]); first deploy v0.1.0 (running the `release.yml` that #18 added, and checking scale to zero).
- **Phase 6:** `db-backup.yml` + restore drill; cost and cold-start review (JVM vs native image).
- **Later:** custom domain.
