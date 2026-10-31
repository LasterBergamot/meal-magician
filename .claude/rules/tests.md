---
paths:
  - "src/test/**"
  - "src/e2eTest/**"
---

# Tests and coverage

- **100 % JaCoCo line + branch coverage** (`task test`). Exclusions only for generated code
  (JTE classes, Lombok via `@Generated`) and the `main()` class, each with a comment why.
- Layers:
  - **Unit** (JUnit 5 + AssertJ + Mockito) for services and domain logic; fixed `Clock`.
  - **Slice** (`@WebMvcTest` + `spring-security-test` `oidcLogin()`) for controllers and
    templates; assert both full-page and `HX-Request` fragment responses.
  - **Integration** on **Testcontainers Postgres** (pgvector image, reused container) for
    repositories, migrations and cross-module flows (`@ApplicationModuleTest`, `Scenario`).
  - **H2** variants are allowed for speed (`task test:fast`) but never replace the Postgres run.
  - **E2E** (`src/e2eTest`, Playwright for Java) against `compose.e2e.yml` with a stub OIDC
    provider; run at phone (390×844) and desktop (1280×800) viewports.
- Always-present guard tests: `ModularityTests` (Modulith verify + docs), endpoint-auth walk,
  tenant isolation (other household → 404), message bundles have equal keys, no hard-coded
  template text, docs tooling (router paths exist, rule globs match, `CLAUDE.md` ≤ 100 lines).
- Test names describe behaviour (`copiesDayIntoEmptyDate`), one behaviour per test.
- Never hit real Google or the real AI provider in tests; stub them.
