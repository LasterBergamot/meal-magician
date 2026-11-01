---
paths:
  - "src/test/**"
  - "src/e2eTest/**"
---

# Tests and coverage

- **100 % JaCoCo line + branch coverage** (`task test`). Exclusions only for
  generated code (JTE classes, Lombok via `@Generated`) and the `main()` class, each with a comment
  why. The exclusion list lives in `coverageExclusions` in `build.gradle.kts`.
- Layers:
  - **Unit** (JUnit 5 + AssertJ + Mockito) for services and domain logic; fixed `Clock`.
  - **Slice** (`@WebMvcTest` + `spring-security-test` `oidcLogin()`) for controllers and
    templates; assert both full-page and `HX-Request` fragment responses.
  - **Integration** on **Testcontainers Postgres** (pgvector image, reused container) for
    repositories, migrations and cross-module flows (`@ApplicationModuleTest`, `Scenario`).
  - **H2** variants are allowed for speed (`task test:fast`) but never replace the Postgres run.
    Tag every Testcontainers test `@Tag("integration")`: `task test:fast` (`-Pfast`) skips those.
    Extend `AbstractPostgresIntegrationTest` (a shared pgvector container, profiles
    `dev,postgres`) rather than starting containers yourself.
  - **E2E** (`src/e2eTest`, Playwright for Java) against `compose.e2e.yml` with a stub OIDC
    provider; run at phone (390×844) and desktop (1280×800) viewports. `task test-e2e` runs them
    (Gradle task `e2eTest`, outside `task test` and the coverage gate). The app greets in Hungarian
    whatever the browser's language, so assert the Hungarian text or switch the language first.
- Always-present guard tests: `ModularityTests` (Modulith verify + docs),
  `EveryEndpointNeedsSignInTests` (endpoint-auth walk), tenant isolation (other household → 404;
  arrives with the first household endpoint), `UiGuardTest` (message bundles have equal keys, no
  hard-coded template text), `DocsToolingTests` (router paths exist, rule globs match,
  `CLAUDE.md` ≤ 100 lines; `task docs:check`). Every guard has tests that run it on a
  deliberately broken example, so it cannot silently stop guarding.
- Test names describe behaviour (`copiesDayIntoEmptyDate`), one behaviour per test.
- Never hit real Google or the real AI provider in tests; stub them.
