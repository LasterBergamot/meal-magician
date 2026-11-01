# Current State: Backend

What exists in `main` today: the build skeleton, the database, the `identity` module (sign-in) and
the first part of the `household` module (create, rename, switch); no other business modules yet.

- **Application class** `MealMagicianApplication` starts Spring Boot; everything lives below its
  package `hu.lasterbergamot.mealmagician`.
- **Modules** (Spring Modulith): `identity`, `household`, `catalog`, `planning`, `feedback`,
  `sharing` and the open shared kernel `shared`. Each has a `package-info.java` that says what the
  module owns; `catalog`, `planning`, `feedback` and `sharing` are still empty. The rule: a module's root package is
  its public API, everything else goes in `internal/` and other modules may not import it.
  `ModularityTests` fails the build on a violation and writes module docs to
  `build/spring-modulith-docs`.
- **Guard tests (#14)** enforce the conventions so nobody has to remember them:
  `EveryEndpointNeedsSignInTests` calls every mapped controller endpoint anonymously and fails if
  one outside `SecurityConfig.PUBLIC_PATHS` answers with anything but a redirect to the login page
  (a new controller is covered automatically); `DocsToolingTests` checks the agent docs (see
  [[Current State: Everything Else]]); `UiGuardTest` covers templates and message bundles
  ([[Current State: Frontend]]). Each has tests that run the check on a deliberately broken example.
  The household endpoints have their own 404/403 tests (`HouseholdPagesTest`); the reusable
  tenant-isolation guard that every later endpoint must register with is #44.
- **Households (#41), module `household`:** `HouseholdService` is the public entry point
  (create, list, rename, and the membership lookup `find`/`require`). Every method takes the
  asking person's id and checks membership itself: another household gives
  `HouseholdNotFoundException` (404, the same answer as "does not exist", so nothing leaks) and a
  member doing an owner-only action gives `OwnerOnlyException` (403). The creator becomes OWNER;
  a household name is 1 to 100 characters. `CurrentHouseholdResolver` finds the household of a
  request: the id lives in the session but is re-checked against `membership` on **every**
  request, falling back to the person's first household. Controllers of other modules get it
  with a `CurrentHousehold` parameter (an argument resolver; a person with no household gets
  404) and pass `id()` to every repository call. `household/internal` holds the entities
  (`Household` with `@Version`, `Membership`), the repositories and `HouseholdController`.
  `shared.web.HouseholdNavSource` is how the header learns the household without `shared`
  depending on `household` (the same trick as `CsrfMetaSource`). Member management and the
  reusable tenant guard are #43 and #44.
- **Invites (#42), module `household`:** an owner invites an e-mail address and shares a
  `/join/<code>` link through any messenger (no e-mail is sent). `InviteService` is the public entry
  point: `create` (owner only; validates the address, adds it to the allowlist with source `INVITE`
  through `identity.AllowlistEditor`, returns the raw code once), `list`, `revoke`, `preview` and
  `join`. The owner picks a lifetime of 2 hours, 24 hours (default) or 3 days.
  - **Why it is safe:** the code is 256 random bits (`SecureRandom`); the `invite` table (V5) stores
    only its SHA-256 (`CodeHasher`), so a leaked row is no link. Joining needs the signed-in Google
    e-mail to equal the invited one (checked first, so a link that is not yours reveals nothing
    else). An invite works once: `markUsed` is a guarded `UPDATE` that reports how many rows it
    changed, so two simultaneous clicks cannot both win.
  - **Refusals:** unknown code 404, other Google account 403, revoked, used or expired 410; each has
    its own message on the page. Owner-only actions give 403 to members and 404 to strangers, and
    another household's invite id is 404.
  - **Where the code appears:** only in the one response that creates the invite and in the link
    itself, so it can show up in access logs of whatever sits in front of the app (and, briefly, in
    the session's saved request while someone signs in). That is why it expires and is single use.
    It is never logged by the app.
  - **Revoking** keeps the address on the allowlist (removing it is the admin page, #45).
  - `/join/**` needs a session like every page: an anonymous visitor is sent to `/login` and
  Spring Security returns them to the link after Google sign-in. Not rate limited yet (Phase 6).
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
    (`app_user`, `allowed_email` and the Spring Session tables) and `V4__household.sql`
    (`household` with a `version` column, and `membership` with the role OWNER or MEMBER).
  - **Profiles:** `dev` points at in-memory H2 in PostgreSQL mode; `postgres` points at the
    compose Postgres (override with `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD`) and adds the
    `postgresql/` folder. Use them together as `dev,postgres`. With no profile there is no
    datasource configured; the `prod` profile (Cloud Run) takes it from environment variables.
  - **Connection pool:** at most 5 connections and `minimum-idle: 0`, with idle ones closed
    after a minute, so nothing keeps the Neon database awake (D11).

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
    database and keep the app and the database from scaling to zero.
  - **Rules** (`identity/internal/SecurityConfig`): everything needs a session except the explicit
    `PUBLIC_PATHS` (login page, error page, health check, static files, PWA files). CSRF stays on.
    An anonymous browser request is redirected to `/login`; an anonymous **htmx** request gets
    `401` + `HX-Redirect: /login`, so a click after the session expired navigates the whole page
    instead of swapping a login form into a fragment. Responses carry `X-Frame-Options: DENY`,
    `Referrer-Policy: same-origin` and `nosniff`.
  - **Fail closed in production:** `ProductionConfigGuard` stops the `prod` profile from starting,
    before any other bean exists, unless the Google client id and secret, `MM_ADMIN_EMAILS`, a
    `jdbc:postgresql:` datasource and `forward-headers-strategy=framework` are all set. It lists
    every missing setting in one message. Without it a forgotten Cloud Run variable would fail in a
    confusing place, or worse, start half-open.
  - Setup for a developer: [[Auth Setup]].

The planned modules, data model and request flow are in [[Roadmap: Backend]] and `PLAN.md` §4.
