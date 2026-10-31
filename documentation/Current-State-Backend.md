# Current State: Backend

What exists in `main` today: the build skeleton, the database and the `identity` module (sign-in);
no business modules yet.

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
  and `shared.web` (`SecurityHeadersFilter` and `CsrfMetaSource`, `ViewContext` + `ViewContextAdvice`, `Htmx`,
  `AppErrorController`, `ManifestController`, and the temporary `HelloController`). They are
  described from the UI side in [[Current State: Frontend]].
- **Database (#11):** Spring Data JPA (Hibernate set to `ddl-auto: validate`, so it only checks
  the schema) plus **Flyway**, which owns the schema. There are only the identity entities so far; the rest
  arrive with the modules that need them (identity's came with #12).
  - Migrations live in `src/main/resources/db/migration/`. `common/` holds portable SQL that
    runs on H2 and Postgres; `postgresql/` holds Postgres-only SQL. Version numbers are shared by
    both folders. Today: `V1__init.sql` (an empty baseline), `postgresql/V2__pgvector.sql`
    (`CREATE EXTENSION vector`, ready for the Phase 7 assistant) and `V3__identity.sql`
    (`app_user`, `allowed_email` and the Spring Session tables).
  - **Profiles:** `dev` points at in-memory H2 in PostgreSQL mode; `postgres` points at the
    compose Postgres (override with `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD`) and adds the
    `postgresql/` folder. Use them together as `dev,postgres`. With no profile there is no
    datasource configured; the `prod` profile (Railway) takes it from environment variables.
  - **Connection pool:** at most 5 connections and `minimum-idle: 0`, with idle ones closed
    after a minute, so nothing keeps the Railway app awake (D11).

- **Identity (#12):** nothing is reachable without signing in. Why this design: the app holds a
  household's private data, so the default must be "closed".
  - **Sign-in:** Google OIDC through Spring Security's `oauth2Login`. People are identified by
    Google's stable `sub`, not their e-mail (e-mails change), and the e-mail must be *verified*.
  - **Allowlist:** after Google confirms who someone is, `AllowlistedOidcUserService` checks that
    their e-mail is in `MM_ADMIN_EMAILS` (instance admins, from the environment) or in the
    `allowed_email` table. Otherwise they get a **403** and **no `app_user` row** is created. E-mails
    are compared lower-case. Changing the table needs no redeploy; the admin page for it comes
    in Phase 1.
  - **Users:** `app_user` (Google `sub`, e-mail, name, UI locale `hu`/`en`, created at). The e-mail
    and name are refreshed at every sign-in. The principal that controllers receive is
    `identity.SignedInUser` (public API of the module): `@AuthenticationPrincipal SignedInUser user`
    gives `userId()`, `locale()` and `isAdmin()`.
  - **Sessions:** Spring Session JDBC (`spring_session*` tables), so a restart, a redeploy or the
    app waking from sleep does not sign anyone out. They last 30 days. The cookie is
    `HttpOnly`, `SameSite=Lax` and, in prod, `Secure`. Expired sessions are deleted at sign-in
    (`SessionCleanupOnSignIn`) instead of by Spring's timer, because a timer would poll the
    database and keep Railway from putting the app to sleep.
  - **Rules** (`identity/internal/SecurityConfig`): everything needs a session except the explicit
    `PUBLIC_PATHS` (login page, error page, health check, static files, PWA files). CSRF stays on.
    An anonymous browser request is redirected to `/login`; an anonymous **htmx** request gets
    `401` + `HX-Redirect: /login`, so a click after the session expired navigates the whole page
    instead of swapping a login form into a fragment. Responses carry `X-Frame-Options: DENY`,
    `Referrer-Policy: same-origin` and `nosniff`.
  - **Fail closed in production:** `ProductionConfigGuard` stops the `prod` profile from starting,
    before any other bean exists, unless the Google client id and secret, `MM_ADMIN_EMAILS`, a
    `jdbc:postgresql:` datasource and `forward-headers-strategy=framework` are all set. It lists
    every missing setting in one message. Without it a forgotten Railway variable would fail in a
    confusing place, or worse, start half-open.
  - Setup for a developer: [[Auth Setup]].

The planned modules, data model and request flow are in [[Roadmap: Backend]] and `PLAN.md` §4.
