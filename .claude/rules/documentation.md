---
paths:
  - "documentation/**"
  - "README.md"
  - "PLAN.md"
  - ".github/workflows/wiki-sync.yml"
---

# Writing documentation

- `documentation/` is mirrored into the GitHub wiki by CI; keep it as current as `README.md`.
- **Current-State vs Roadmap:** `Current-State-*` describes only what exists in `main` today;
  `Roadmap-*` holds planned work. When a roadmap item lands, move it to the matching
  Current-State page in the same PR.
- `Newcomer-Guide.md` is the junior-developer walkthrough (tools and why, glossary, one request
  through the layers, run/test, first change). Update it when architecture, request flow,
  tooling or workflow change.
- `References.md` lists official docs for every library, tool, service and spec we use; add a
  row whenever you adopt one.
- Every new page goes into the page map in `wiki-sync.yml` and is linked from `Home.md`.
- Cross-links use `[[Display Title]]`.
- `PLAN.md`: tick checklist items with issue/PR numbers, add a progress-log row, amend
  decisions inline (`*(Amended YYYY-MM-DD; was …)*`) instead of rewriting history.
- Write for a newcomer with no context: explain the why, not just what changed.
