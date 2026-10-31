# Current State: Backend

What exists in `main` today: **the build skeleton only**, no business logic yet.

- **Application class** `MealMagicianApplication` starts Spring Boot; everything lives below its
  package `hu.lasterbergamot.mealmagician`.
- **Modules** (Spring Modulith): `identity`, `household`, `catalog`, `planning`, `feedback`,
  `sharing` and the open shared kernel `shared`. Each is an empty package with a
  `package-info.java` that says what the module will own. The rule: a module's root package is
  its public API, everything else goes in `internal/` and other modules may not import it.
  `ModularityTests` fails the build on a violation and writes module docs to
  `build/spring-modulith-docs`.
- **`shared.ClockConfig`** provides the one `Clock` bean (Europe/Budapest). Code asks it for
  "today" instead of calling `LocalDate.now()`, so tests can fix the date.
- **Config:** `application.yml` turns on virtual threads and graceful shutdown, and tells JTE to
  use its precompiled templates.
- **Web plumbing (#13), in `shared`:** `shared.i18n` (`Messages` is the `t("key")` helper,
  `I18nConfig` the message bundles and the `mm_lang` cookie locale resolver, `SupportedLocales`)
  and `shared.web` (`SecurityHeadersFilter`, `ViewContext` + `ViewContextAdvice`, `Htmx`,
  `AppErrorController`, `ManifestController`, and the temporary `HelloController`). They are
  described from the UI side in [[Current State: Frontend]].
- **Database (#11):** Spring Data JPA (Hibernate set to `ddl-auto: validate`, so it only checks
  the schema) plus **Flyway**, which owns the schema. There are no tables or entities yet; they
  arrive with the modules that need them, starting with identity (#12).
  - Migrations live in `src/main/resources/db/migration/`. `common/` holds portable SQL that
    runs on H2 and Postgres; `postgresql/` holds Postgres-only SQL. Version numbers are shared by
    both folders. Today: `V1__init.sql` (an empty baseline) and `postgresql/V2__pgvector.sql`
    (`CREATE EXTENSION vector`, ready for the Phase 7 assistant).
  - **Profiles:** `dev` points at in-memory H2 in PostgreSQL mode; `postgres` points at the
    compose Postgres (override with `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD`) and adds the
    `postgresql/` folder. Use them together as `dev,postgres`. With no profile there is no
    datasource configured; the production profile arrives with #12.
  - **Connection pool:** at most 5 connections and `minimum-idle: 0`, with idle ones closed
    after a minute, so nothing keeps the Railway app awake (D11).

The planned modules, data model and request flow are in [[Roadmap: Backend]] and `PLAN.md` §4.
