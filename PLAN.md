# Plan: Meal Magician v1 — household meal planner

Status: **Phase 0 in progress** (#9, #10, #11 done) · Started: 2026-10-31 · Owner: @LasterBergamot

This file is the working plan for building Meal Magician from an empty repo to a v1 that the
household uses every day, plus the later AI assistant. Pick it up at any point: every phase lists
its scope and a checklist, and the **Progress log** at the bottom records what happened and when.
Update both as work lands: tick the box, add the issue and PR numbers, and add a log row.

> **Agent starting work:** read [§9 Start here](#9-start-here-next-agent) first.

---

## 1. Why, and what "done" looks like

The household plans meals ad hoc, mostly lunches, and keeps reinventing the same weeks. Meal
Magician is a **private, household-only** web app that should:

- plan a week of meals (breakfast, lunch, dinner, snack), mostly lunches, plus **special
  occasions** (birthdays, Christmas, public holidays) as dated events with their own menus,
- keep a **meal library** and let you reuse meals, **single days** and **whole weeks**
  (copy them or save them as templates),
- let anyone with a Google account sign in, **if they are on the allowlist**. They create a
  **household** (becoming its owner) and invite the other members, who all see and edit the
  same plan,
- work well in a browser on a PC, a laptop and a **phone** (responsive, mobile-first, and
  installable as a PWA),
- record the history and ratings that a later **AI assistant** (Phase 7) needs to learn the
  household's preferences and draft plans, using RAG over pgvector.

**v1 is done when** the household plans and eats from Meal Magician for two weeks in a row: the
week is planned on a phone, a past day or week is reused, one occasion is planned, and meals
are rated. It runs on Railway at a cost close to the Hobby-plan minimum, with nobody looking
after a server.

---

## 2. Decisions

| # | Decision | Status |
|---|---|---|
| D1 | **Java 25 (current LTS)**, through a Gradle toolchain. Java 27 is out (Sept 2026) but is not LTS; move to the next LTS when it ships. | Decided |
| D2 | **Spring Boot 4.x, latest patch**, with the exact version pinned when scaffolding (check the latest release then) *(Amended 2026-10-31: pinned Boot 4.1.2, Modulith 2.1.2, JTE 3.2.4, Gradle 9.8.2; the newest Boot and Modulith releases were still RCs, so the latest stable ones were taken)*. **Gradle Kotlin DSL** with a version catalog (`gradle/libs.versions.toml`). | Decided |
| D3 | **Modular monolith with Spring Modulith**: `ApplicationModules.verify()` runs in a test, modules talk through public APIs and application events, and module docs are generated. Worth it because the AI is added later as its own module. | Decided |
| D4 | **UI: JTE + htmx + Alpine.js + Tailwind CSS.** JTE templates are precompiled and type-safe. htmx swaps in partial pages so the app feels like a single-page app. Alpine uses its **CSP build** and handles small client-side state. Tailwind runs through its **standalone CLI**, so there is no Node toolchain. Server-rendered, mobile-first, responsive. | Decided |
| D5 | **PWA**: a manifest, icons and a minimal service worker so the app can be installed on a phone's home screen. **No offline mode.** The layout is responsive in a plain browser too; installing only adds the icon and full-screen mode. | Decided |
| D6 | **Auth: Google sign-in through Spring Security's `oauth2Login` (OIDC)**, with no Supabase or Keycloak. **The allowlist lives in the DB** (`AllowedEmail`, with a source of ADMIN or INVITE) and is checked after sign-in. Anyone not on it gets 403 and no user row is created. `MM_ADMIN_EMAILS` (env) names the bootstrap instance admins, who manage the allowlist on a small admin page. Changing who has access never needs a redeploy. | Decided |
| D7 | **Server-side sessions in Postgres (Spring Session JDBC)**, so a redeploy or the app waking from sleep doesn't sign anyone out. CSRF stays on, and htmx sends the token in a header. | Decided |
| D8 | **One user can belong to many households.** Roles are OWNER and MEMBER, and the UI has a household switcher. Every query is scoped by household. Another household's data returns **404** (so its existence doesn't leak), and owner-only actions return **403** to members. | Decided |
| D9 | **Invites are email-bound.** The owner enters an email: it is added to the allowlist (source INVITE) and gets a `/join/<code>` link. The link expires after 2 h, 24 h (default) or 3 days, can be revoked, and only a SHA-256 hash of the code is stored. Joining needs the signed-in Google email to match the invited one. No email sending: the owner shares the link through any messenger. | Decided |
| D10 | **DB: PostgreSQL 17+ with pgvector from day one**, so RAG needs no data migration. Schema changes go through **Flyway**. **H2 (PostgreSQL mode)** runs fast unit and slice tests and `task serve` without Docker. Integration tests and CI always run on **Testcontainers Postgres**, which is the source of truth. Postgres-only SQL, such as pgvector, goes in `db/migration/postgresql/`. | Decided |
| D11 | **Hosting: Railway**, EU region: one app service plus a Postgres service with pgvector, on the free `*.up.railway.app` domain for now. **Cost is kept as low as possible**: the app has Railway's Serverless/app-sleeping mode on (it sleeps after about 10 minutes idle and wakes on a request). The Hikari pool is allowed to shrink to zero connections when idle (`minimumIdle=0`), and there are no in-app schedulers, keepalives or metrics exporters. Check current Hobby-plan pricing, sleeping behaviour and backup options in Phase 0. | Decided |
| D12 | **Branching: `main` only**, with no `develop`. `main` is protected: PRs only, required checks green, no force-pushes. Branch names: `feat/<issue>-…`, `fix/<issue>-…`, `chore/<issue>-…`, `docs/…`. **Merge commits only.** Commit subjects use `#N - <imperative summary>`. | Decided |
| D13 | **Deploy on a release tag.** A manual `release` workflow bumps the version, tags `vX.Y.Z`, builds the image, pushes it to GHCR, deploys it to Railway and polls `/actuator/health`. Merges to `main` only run CI. Migrations run at app startup (Flyway), so keep them backwards compatible (expand/contract). | Decided |
| D14 | **i18n: Hungarian by default, English selectable.** All UI text lives in `messages_hu.properties` / `messages_en.properties`, and a test fails if a JTE template contains a hard-coded literal. Time zone is Europe/Budapest and weeks start on Monday (ISO weeks). | Decided |
| D15 | **AI comes later (Phase 7)**, but v1 already records what it needs: history, tags, occasions and ratings. | Decided |
| D16 | **Quality gates as in lineup:** Taskfile is the single entry point and CI calls the same tasks. **JaCoCo at 100 %** line and branch coverage, with every exclusion explained. **Javadoc is required** on public types and methods (Checkstyle), written for a junior developer. Spotless runs google-java-format. Also the docs gate, Dependabot targeting `main`, a Trivy image scan, base images pinned by digest, a non-root container, and third-party actions pinned to a SHA. | Decided |
| D17 | **Runtime: JVM with an AppCDS / Spring AOT cache** to keep cold starts short (aim for about 2–4 s). After the first month, measure the bill and the cold starts, and **move to a GraalVM native image only if they hurt**. Stay native-friendly: precompiled JTE, no runtime classpath-scanning tricks. | Decided |
| D18 | **Lombok is allowed**, mainly for JPA entities and builders. `lombok.config` sets `lombok.addLombokGeneratedAnnotation = true` so JaCoCo skips the generated code. Use records for DTOs, commands and value objects. | Decided |
| D19 | **The in-app AI provider is pluggable through Spring AI**, so it doesn't have to be Claude. The default is a cheap hosted model on a **paid tier**, so household data isn't used for training: Gemini Flash, a GPT mini model, Claude Haiku, or OpenRouter to switch freely. The model is chosen by config; the final pick comes from a small eval in Phase 7. **Embeddings run in-process** (Spring AI ONNX transformers, e.g. all-MiniLM-L6-v2), so they cost nothing per call. Ollama on Railway is rejected: it would cost far more and be slow on CPU. | Decided |
| D20 | **Base package `hu.lasterbergamot.mealmagician`.** | Decided |
| D21 | **JDK: Eclipse Temurin 25** (Adoptium's OpenJDK build), used the same way everywhere. The **Gradle wrapper** (`./gradlew`, committed) pins Gradle. The **Gradle toolchain** (`languageVersion = 25`, `vendor = ADOPTIUM`) with the foojay resolver plugin downloads the JDK automatically, so nobody installs Java 25 by hand. The container uses `eclipse-temurin:25-jdk` for the build and `eclipse-temurin:25-jre` at runtime, both pinned by digest. Railway runs our image, so production uses exactly that JRE: there's no separate host JVM to match. | Decided |

---

## 3. Phases and issues

Each phase is an **epic** with sub-issues, created with the `new-issue` skill (conventions in
`.claude/context/issues.md`). Fill in the `#N` numbers here when the issues exist.

### Phase 0 — Foundation (milestone `M0 – Foundation`, epic #1)
- [x] Create the GitHub repo `LasterBergamot/meal-magician` (**public**, so branch protection
      and the wiki work on the free plan): `main` as default, PRs required, merge commits only,
      wiki on, labels, milestones, Dependabot alerts, secret scanning.
- [x] Project board #7 (Status/Area/Priority/Size), with all issues added.
- [x] File the epics for Phases 0–7 (#1–#8) and the Phase 0 sub-issues (#9–#18).
- [x] Claude Code setup: ai-common `workflow` skills through APM, router `CLAUDE.md`,
      `.claude/context`, `.claude/rules`, settings. *(Done when the repo was initialised.)*
- [x] Docs skeleton: `README.md`, `documentation/` (Current-State and Roadmap pages), and this plan.
- [x] #9 Gradle skeleton: Java 25 toolchain, Spring Boot 4, Spring Modulith, JTE (precompiled),
      Lombok + `lombok.config`, Spotless, Checkstyle (Javadoc), JaCoCo 100 %, version catalog.
      *(Done in PR #20.)*
- [x] #10 `Taskfile.yml` + `scripts/doctor.sh`. The tasks listed in `.claude/context/workflow.md`.
      Tasks that need later Phase 0 files (`serve:pg`, `test-e2e`, `migrate-check`, `docs:check`,
      and `up`/`down`/`build`) exist and stop with a message naming their issue.
      *(Done in PR #21.)*
- [x] #11 `compose.yml` (Postgres with pgvector) and the H2 dev profile; the Flyway baseline
      (`V1__init.sql`, plus the pgvector extension in `postgresql/`). *(Done in PR #22.)*
- [ ] #12 Identity: Google OIDC login, the DB allowlist and `MM_ADMIN_EMAILS` bootstrap, `AppUser`,
      Spring Session JDBC, sign-out, fail-closed production config.
- [x] #13 Base layout: JTE layout, Tailwind standalone CLI, htmx + Alpine (CSP build), the `t()`
      i18n helper (hu/en) and language switcher, PWA manifest + service worker, error pages,
      htmx session-expiry handling (`HX-Redirect`). *(PR 23. Language is a cookie for now and
      the server-side `HX-Redirect` comes with #12; the client already redirects on 401/403.
      Home-screen install on a real phone is still a manual check.)*
- [ ] #14 Tests: the Modulith verify test, an "every endpoint needs auth" test, the template
      hard-coded-text test, a docs-tooling test (router paths exist, rule globs match,
      `CLAUDE.md` under 100 lines).
- [ ] #15 `Dockerfile` (multi-stage, pinned by digest, uid 10001, AppCDS) and `compose.e2e.yml`
      with a stub OIDC provider; one Playwright-Java e2e test (sign in → hello page) on phone
      and desktop viewports.
- [ ] #16 CI: `ci.yml`, `docs-check.yml` (+ `scripts/check_docs_touched.sh`), `wiki-sync.yml`,
      `dependabot.yml`, PR template already in place.
- [ ] #17 Railway: project, Postgres with pgvector, app service from the GHCR image, sleeping on,
      variables. The Google OAuth client is created in Google Cloud. Runbooks:
      `documentation/Railway-Setup.md`, `documentation/Auth-Setup.md`.
- [ ] #18 `release.yml` → first deploy **v0.1.0**: sign in on a phone and see the hello page.
      Confirm that the app really sleeps when idle.

### Phase 1 — Households (`M1 – Core planner`, epic #2)
- [ ] Create a household (the creator becomes OWNER), rename it, switch between households
      (stored in the session).
- [ ] Email-bound invites: create, list, revoke, `/join/<code>`, expiry options.
- [ ] Members: list, remove (owner), leave, transfer ownership. The last owner can't leave.
- [ ] Tenant-isolation test (404 for another household's data), plus 403 tests for owner-only actions.
- [ ] Admin page for the allowlist (instance admins only).

### Phase 2 — Meal library (`M1`, epic #3)
- [ ] Meal CRUD: name, notes, recipe URL, tags, which occasion types it suits, archive.
- [ ] Tags per household; search and filter (name, tag), recently used meals first.
- [ ] Quick-add from a phone (name only, add the details later).

### Phase 3 — Weekly plan (`M1`, epic #4)
- [ ] Week view: a grid on desktop, a list of days on phone; ISO-week navigation; today highlighted.
- [ ] Slots: BREAKFAST / LUNCH / DINNER / SNACK. Add a meal from the library or create one inline;
      a note per slot; reorder and move slots.
- [ ] Optimistic locking (`@Version`) with a friendly "someone else changed this, reload" message.

### Phase 4 — Reuse and occasions (`M1`, epic #5)
- [ ] Copy a day onto another date, and copy a week onto another week (merge or replace).
- [ ] `PlanTemplate` with scope DAY or WEEK: save from a plan, apply to a date or week (merge
      or replace), rename and delete.
- [ ] Occasions: a dated event (name, date, type: birthday, holiday, party, other) with its own
      menu items; shown on the week view; save as a template and reuse next year.

### Phase 5 — Feedback, sharing, PWA polish (`M1`, epic #6) → **v1.0.0**
- [ ] Rate an eaten meal: each member gives a thumbs up/down or 1–5, with an optional comment;
      a "how did it go" prompt on past slots.
- [ ] Print-friendly week view; read-only share link (hashed token, expires, can be revoked).
- [ ] PWA polish: icons, theme colour, install hint. Lighthouse check on a phone.

### Phase 6 — Hardening (`M2 – Hardening`, epic #7)
- [ ] Backups: Railway's own backups, if the plan has them, plus `db-backup.yml` (scheduled
      `pg_dump` → encrypted with `age` → artifact or object storage). One **restore drill**,
      documented.
- [ ] GDPR basics: export my data, delete my account; privacy note page.
- [ ] Security headers and CSP review, rate limit on invite and join, request size limit.
- [ ] `repo-audit` run; fix or file its findings.
- [ ] Review the cost and cold starts after one month (D17): stay on the JVM or move to native.

### Phase 7 — AI assistant (`M3 – Assistant`, epic #8)
- [ ] `assistant` module: Spring AI `ChatClient`, with the provider chosen by config (D19);
      monthly spend cap per household.
- [ ] In-process ONNX embeddings → `PgVectorStore`; re-index on `MealChanged` and
      `RatingGiven` events.
- [ ] Preference profile per member, built from ratings and history.
- [ ] Agent tools: `recentWeeks`, `searchMeals` (semantic), `householdPreferences`,
      `upcomingOccasions`, `draftWeek`. **Drafts only**: a person accepts or edits them.
- [ ] "Suggest my week" with chat-style refinement ("less meat", "Thursday is busy").
- [ ] A small eval set (10–20 prompts) to pick the model; record the choice as a decision.

### Later ideas (`Mx – Parked`)
Ingredients and a generated shopping list, meal photos, leftovers and "eat again soon" hints,
an ICS calendar feed, a custom domain, email notifications.

---

## 4. Architecture

### 4.1 Modules (Spring Modulith, `hu.lasterbergamot.mealmagician.<module>`)

| Module | Owns | Publishes / listens |
|---|---|---|
| `identity` | Google OIDC sign-in, `AllowedEmail`, `AppUser`, the instance-admin page, locale preference | publishes `UserSignedIn` |
| `household` | `Household`, `Membership(role)`, `Invite`, the current-household resolver | listens for nothing; its API is used by every module below |
| `catalog` | `Meal`, `Tag` | publishes `MealChanged` |
| `planning` | `MealSlot` (per date), `PlanTemplate(scope DAY|WEEK)`, `Occasion`, `OccasionTemplate` | publishes `SlotPlanned` |
| `feedback` | `Rating` (member × slot) | publishes `RatingGiven` |
| `sharing` | print view, `ShareLink` | uses `planning`'s API |
| `assistant` *(Phase 7)* | embeddings, preference profile, agent tools | listens for `MealChanged` and `RatingGiven` |
| `shared` | base entity, auditing, errors, i18n `t()` helper, htmx helpers | a shared kernel, not a business module |

Package layout inside a module: the module's root package is its **public API** (services,
events, DTO records). `internal/` holds entities, repositories, controllers and templates
support. Other modules may only use the root package; the Modulith test enforces this.

### 4.2 Data model sketch (v1)

- `app_user(id, google_sub, email, display_name, locale, created_at)`
- `allowed_email(email PK, source ADMIN|INVITE, added_by, created_at)`
- `household(id, name, created_at, version)`; `membership(household_id, user_id, role)`
- `invite(id, household_id, email, code_hash, expires_at, revoked_at, used_at, created_by)`
- `meal(id, household_id, name, notes, recipe_url, occasion_types, archived, version)`, `tag`, `meal_tag`
- `meal_slot(id, household_id, date, slot_type, position, meal_id NULL, free_text NULL, note, version)`.
  The plan is stored **per date**. A "week" is just a query over its seven dates, which keeps
  copying a day or a week trivial.
- `plan_template(id, household_id, name, scope DAY|WEEK)`, `plan_template_item(template_id, day_offset, slot_type, position, meal_id, note)`
- `occasion(id, household_id, name, date, type, notes)`, `occasion_item(occasion_id, position, meal_id, note)`,
  and an occasion template (the same shape, without a date).
- `rating(slot_id, user_id, score, comment, created_at)`
- `share_link(id, household_id, token_hash, scope, expires_at, revoked_at)`
- Spring Session tables (`spring_session*`). Phase 7 adds `vector_store` (pgvector).

### 4.3 Request flow (for the Newcomer Guide)

1. The browser requests a page or an htmx partial.
2. Spring Security checks the session (Google OIDC sign-in when there is none) and the allowlist.
3. The current-household resolver loads the household from the session and checks membership.
4. The controller (in the module's `internal/`) calls the module's service.
5. The JTE template renders the result: a full page, or a fragment when `HX-Request` is set.

### 4.4 Cross-cutting

- **Security:**
  - Security headers and a CSP with no inline scripts.
  - Request size limit and rate limits on invite and join.
  - **Fail-closed production config:** the app won't start under the `prod` profile without
    the Google client id/secret, `MM_ADMIN_EMAILS`, a Postgres URL and
    `server.forward-headers-strategy=framework` behind Railway's proxy.
- **Concurrency:** `@Version` on the aggregates, so two family members editing the same week
  get a friendly conflict message instead of losing an edit.
- **Cold starts (D11, D17):** AppCDS, lazy init where it is safe, a small Hikari pool with
  `minimumIdle=0`. The first request after the app has slept takes a few seconds.
- **No in-app schedulers:** anything periodic (backups) runs in GitHub Actions, so nothing keeps
  the app awake.

---

## 5. Repo layout (target)

```
meal-magician/
├── build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml, lombok.config
├── src/main/java/hu/lasterbergamot/mealmagician/{identity,household,catalog,planning,feedback,sharing,shared}/
├── src/main/jte/                 # templates (layout/, <module>/, fragments)
├── src/main/resources/
│   ├── application.yml, application-{dev,prod}.yml
│   ├── db/migration/{common,postgresql}/   # Flyway; postgresql/ = Postgres-only SQL
│   ├── messages_hu.properties, messages_en.properties
│   └── static/                   # tailwind output, htmx, alpine, manifest, icons, sw.js
├── src/test/java/…               # unit, slice, Testcontainers integration
├── src/e2eTest/java/…            # Playwright for Java
├── Taskfile.yml, compose.yml, compose.e2e.yml, Dockerfile, .dockerignore
├── scripts/                      # doctor.sh, check_docs_touched.sh, …
├── .github/workflows/            # ci, docs-check, wiki-sync, release, db-backup; dependabot.yml
├── documentation/                # mirrored to the GitHub wiki
├── .claude/, CLAUDE.md, apm.yml, apm.lock.yaml
└── README.md, PLAN.md
```

---

## 6. Hosting and cost (Railway)

- **Services:** `app` (the Docker image from GHCR) and `postgres` (an image or template with
  pgvector), both in an EU region and talking over the private network.
- **Variables:**
  - `SPRING_PROFILES_ACTIVE=prod`
  - `SPRING_DATASOURCE_URL`/`USERNAME`/`PASSWORD` (Railway references to the DB service)
  - `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
  - `MM_ADMIN_EMAILS`
  - `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 …`
- **Sleeping:** on for `app`. Verify that it actually sleeps; DB keepalives or outbound
  telemetry would keep it awake.
- **Deploy (D13):** `release.yml` pushes `ghcr.io/lasterbergamot/meal-magician:<version>` and
  points the Railway service at it (Railway CLI/API with a project token, `RAILWAY_TOKEN` secret).
  Pick the exact mechanism in Phase 0 and document it in `documentation/Railway-Setup.md`.
- **Watch:** the first month's bill. If it is clearly above the Hobby minimum, look at native
  (D17) and memory limits first.

---

## 7. Caveats

- **Google OAuth:** the `openid email profile` scopes don't need Google's app verification. The
  allowlist, not the consent screen's test-user list, controls who gets in. Publish the consent
  screen ("In production") so you don't hit the test-user limit; the allowlist still guards access.
- **H2 vs Postgres drift:** H2 tests can pass where Postgres fails. CI runs the Testcontainers
  suite on every PR; keep the SQL portable outside `postgresql/`.
- **htmx and session expiry:** an htmx request that gets 401/403 must redirect the whole page
  (`HX-Redirect`) instead of swapping a sign-in page into a fragment.
- **100 % coverage:** plan the exclusions up front (generated JTE classes, Lombok-generated
  code, `main()`), each with a comment saying why.
- **Spring AI and Boot 4:** check which Spring AI release supports the pinned Boot version
  before Phase 7.
- **Local Java (D21):** the dev machine's default JDK is 27, and only Gradle itself runs on it.
  The toolchain downloads Temurin 25 for compiling and testing. If the pinned Gradle version
  can't run on JDK 27 yet, run the wrapper on a supported JDK (`JAVA_HOME` or mise).
  *(Checked 2026-10-31: Gradle 9.8.2 runs on JDK 27, so no workaround is needed.)*
- **`task` vs `go-task`:** on Arch the Task binary is `go-task`, and `task` is only a shell
  alias, so it isn't available in scripts or in Claude Code's non-interactive shell. Docs say
  `task …`; scripts and agents run `go-task …`, or the owner adds a real symlink
  (`ln -s /usr/bin/go-task ~/.local/bin/task`).

---

## 8. Verification (per phase)

- **Every PR:** `task lint`, `task test` (100 %), `task test-e2e` when the UI flow or the
  container changed; CI green.
- **Phase 0:** sign in on a phone against Railway, check that someone not on the allowlist gets
  403, check that the app goes to sleep and wakes up.
- **Phase 1–5:** the scenario from §1 on a real phone and a laptop, with two Google accounts in
  one household and a third account that isn't a member (it must get 404s).
- **Phase 7:** the eval set passes; suggestions are only drafts; the spend cap is enforced.

---

## 9. Start here (next agent)

1. Read `CLAUDE.md`, then this file, then `.claude/context/workflow.md` and `pr-checklist.md`.
2. The repo, labels, milestones, epics (#1–#8) and Phase 0 issues (#9–#18, with blocked-by
   links) exist. Still **ask the owner before** pushing to `main`, merging PRs, and anything
   in Google Cloud or Railway.
3. #9 and #10 are done. The dependency order is #9 → #10/#11/#13 → #12 → #14/#15
   → #16 → #18; #17 runs in parallel and needs the owner (consoles). #11 and #13 are done too; next: #12 (identity), then #14 and #15.
4. Then work on Phase 0 issue by issue: a branch per issue, PRs through `create-pr`.
5. Before scaffolding, look up the current latest versions (Spring Boot 4.x, Spring Modulith,
   JTE, the Gradle plugins, Testcontainers, Playwright) and pin them in the version catalog.

---

## 10. Progress log

| Date | What | Refs |
|---|---|---|
| 2026-10-31 | Base package `hu.lasterbergamot.mealmagician` (D20), Temurin 25 through the wrapper and toolchain (D21). Repo created on GitHub as public (free-plan branch protection and wiki). Labels, milestones, board #7, epics and Phase 0 issues filed. | #1–#18 |
| 2026-10-31 | Plan agreed with the owner (interview: hosting, auth, UI, AI, workflow). Repo initialised locally, ai-common `workflow` v0.1.1 installed through APM, docs skeleton written. | — |
| 2026-10-31 | Gradle skeleton: wrapper 9.8.2, Boot 4.1.2, Modulith 2.1.2, JTE 3.2.4, Temurin 25 toolchain, Spotless, Checkstyle (Javadoc), JaCoCo 100 % gate, empty module packages, `ModularityTests`. Gates proven to fail on untested/undocumented code. | #9, #20 |
| 2026-10-31 | `Taskfile.yml` with the workflow.md tasks (later ones stop with a message naming their issue), `scripts/doctor.sh`, checksum-pinned Tailwind CLI download, `task test:fast` via the `integration` JUnit tag. | #10, #21 |
| 2026-10-31 | `compose.yml` (pgvector Postgres 17, digest-pinned), `dev` profile on in-memory H2 and `postgres` profile, Flyway `common/` + `postgresql/` baseline (V1, V2 pgvector), Hikari `minimum-idle: 0`, shared Testcontainers base class, `task serve:pg` and `task migrate-check` made real. | #11, #22 |
| 2026-10-31 | Base UI: JTE layout + hello and error pages (full page / htmx fragment), Tailwind v4 standalone CLI (watch in `task serve`, minified in the Gradle build), vendored htmx 2.0.11 + Alpine CSP 3.17.5, `ctx.t("key")` i18n (hu default, cookie language switch), CSP/security-header filter, PWA manifest + icons + static-only service worker, guard tests (bundles, template literals, CSP-unsafe markup). | #13, 23 |
