# Roadmap: Everything Else

Planned cross-cutting work, by `PLAN.md` phase. Move items to [[Current State: Everything Else]]
when they land.

- **Phase 0:** GitHub repo, labels, milestones, board, epics/issues; `compose.yml` (Postgres + pgvector) and `compose.e2e.yml` (stub OIDC);
  Dockerfile (digest-pinned, non-root, AppCDS) and the real E2E + Trivy steps in `ci.yml` (#15; the
  rest of CI is in [[Current State: Everything Else]]); Google OAuth clients ([[Auth Setup]]); Cloud Run + Neon
  ([[Hosting Setup]]); `release.yml` and first deploy v0.1.0.
- **Phase 6:** `db-backup.yml` + restore drill; cost and cold-start review (JVM vs native image).
- **Later:** custom domain.
