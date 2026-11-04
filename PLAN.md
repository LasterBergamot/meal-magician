# Plan: Meal Magician v1 — household meal planner

Status: **Phases 0–5 done; v1.0.0 released 2026-11-03. Phase 6 (hardening) is next, in the order set in §3 (the AI assistant, then the backups, the repo audit and the rest; backups moved behind the assistant on 2026-11-04). R9, the owner's walk-through (#57), is still open.** · Phase 0 done (owner checks #16, #18 closed 2026-11-02) · Started: 2026-10-31 · Owner: @LasterBergamot

This file is the working plan for building Meal Magician from an empty repo to a v1 that the
household uses every day, plus the later AI assistant. Pick it up at any point: every phase lists
its scope and a checklist, and the **Progress log** at the bottom records what happened and when.
Update both as work lands: tick the box, add the issue and PR numbers, and add a log row.

> **Agent starting work:** read [§9 Start here](#9-start-here-next-agent) first.
>
> **UI redesign:** the look, the app shell and the component set are planned separately in
> [`plan-ui-redesign.md`](plan-ui-redesign.md) (design system, phases R0–R9). Read it before building
> any page in Phases 2–5, and build those pages from its components.

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
are rated. It runs on Google Cloud Run + Neon at (close to) zero cost, with nobody looking
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
| D11 | **Hosting: Google Cloud Run + Neon Postgres**, both in Frankfurt (Cloud Run `europe-west3`, Neon `aws-eu-central-1`). The app is one Cloud Run service (min 0, max 1 instance, request-based billing, startup CPU boost) on its `*.run.app` URL; the database is a Neon project (Postgres 17, pgvector, direct endpoint, no pooler because Flyway and Spring Session need session semantics). **Cost is kept as low as possible**: both scale to zero when idle (Cloud Run after the last request, Neon after ~5 minutes) and should fit the free tiers; a billing budget alert guards it. The Hikari pool may shrink to zero connections (`minimumIdle=0`), and there are no in-app schedulers, keepalives or metrics exporters, so nothing keeps either side awake. Secrets live in Secret Manager, CI deploys through Workload Identity Federation (no keys). | Decided *(Amended 2026-11-01; was Railway app + pgvector service with app sleeping: Railway sign-ups were closed after an incident, and Cloud Run + Neon is likely free and reuses the Google Cloud project of the OAuth client)* |
| D12 | **Branching: `main` only**, with no `develop`. `main` is protected: PRs only, required checks green, no force-pushes. Branch names: `feat/<issue>-…`, `fix/<issue>-…`, `chore/<issue>-…`, `docs/…`. **Merge commits only.** Commit subjects use `#N - <imperative summary>`. | Decided |
| D13 | **Deploy on a release tag.** The `release` workflow (started automatically after CI passes on `main`, or by hand) works out the version, bumps it, tags `vX.Y.Z`, builds the image, pushes it to Artifact Registry (`europe-west3-docker.pkg.dev/<project>/meal-magician/app`), deploys it to Cloud Run (`gcloud run deploy`, authenticated by Workload Identity Federation) and polls `/actuator/health`. Code merged to `main` is released automatically; documentation-only merges are not. Migrations run at app startup (Flyway), so keep them backwards compatible (expand/contract). | Decided *(Amended 2026-11-01; was GHCR + Railway. Amended 2026-11-01 (#51); was: merges to `main` only run CI and a person starts the release with a typed version)* |
| D14 | **i18n: Hungarian by default, English selectable.** All UI text lives in `messages_hu.properties` / `messages_en.properties`, and a test fails if a JTE template contains a hard-coded literal. Time zone is Europe/Budapest and weeks start on Monday (ISO weeks). | Decided |
| D15 | **AI comes later (Phase 7)**, but v1 already records what it needs: history, tags, occasions and ratings. | Decided |
| D16 | **Quality gates as in lineup:** Taskfile is the single entry point and CI calls the same tasks. **JaCoCo at 100 %** line and branch coverage, with every exclusion explained. **Javadoc is required** on public types and methods (Checkstyle), written for a junior developer. Spotless runs google-java-format. Also the docs gate, Dependabot targeting `main`, a Trivy image scan, base images pinned by digest, a non-root container, and third-party actions pinned to a SHA. | Decided |
| D17 | **Runtime: JVM with an AppCDS / Spring AOT cache** to keep cold starts short (aim for about 2–4 s). After the first month, measure the bill and the cold starts, and **move to a GraalVM native image only if they hurt**. Stay native-friendly: precompiled JTE, no runtime classpath-scanning tricks. | Decided *(Amended 2026-11-01; the image uses the JDK 25 AOT cache, which builds on AppCDS and halves the start time, instead of a plain AppCDS archive)* |
| D18 | **Lombok is allowed**, mainly for JPA entities and builders. `lombok.config` sets `lombok.addLombokGeneratedAnnotation = true` so JaCoCo skips the generated code. Use records for DTOs, commands and value objects. | Decided |
| D19 | **The in-app AI provider is pluggable through Spring AI**, so it doesn't have to be Claude. The default is a cheap hosted model on a **paid tier**, so household data isn't used for training: Gemini Flash, a GPT mini model, Claude Haiku, or OpenRouter to switch freely. The model is chosen by config; the final pick comes from a small eval in Phase 7. **Embeddings run in-process** (Spring AI ONNX transformers, e.g. all-MiniLM-L6-v2), so they cost nothing per call. Ollama on Railway is rejected: it would cost far more and be slow on CPU. | Decided *(Amended 2026-11-03, D32; was: a key for a hosted model chosen by an eval, and in-process ONNX embeddings. The provider is now Gemini on Vertex AI with no key, and the embeddings come from Vertex too. The provider stays behind config.)* |
| D20 | **Base package `hu.lasterbergamot.mealmagician`.** | Decided |
| D22 | **Agents may merge their own PRs for planned work.** For an issue in this plan, an agent pushes its feature branch, opens the PR and merges it (merge commit) once every required check is green (before CI exists, #16: once `task lint` and `task test` pass locally). Repo settings, Railway, Google Cloud and anything outside the plan still need the owner's OK. Goal: the owner does one setup session and then only reviews results. | Decided (2026-10-31) |
| D23 | **One Google OAuth client** (`meal-magician`) with two redirect URIs, `localhost:8080` and the Cloud Run URL, instead of separate dev and prod clients: this is a home project, and rotating the one secret takes a minute. | Decided (2026-10-31) *(Amended 2026-11-01; was the Railway domain)* |
| D21 | **JDK: Eclipse Temurin 25** (Adoptium's OpenJDK build), used the same way everywhere. The **Gradle wrapper** (`./gradlew`, committed) pins Gradle. The **Gradle toolchain** (`languageVersion = 25`, `vendor = ADOPTIUM`) with the foojay resolver plugin downloads the JDK automatically, so nobody installs Java 25 by hand. The container uses `eclipse-temurin:25-jdk` for the build and `eclipse-temurin:25-jre` at runtime, both pinned by digest. Railway runs our image, so production uses exactly that JRE: there's no separate host JVM to match. | Decided |
| D24 | **Parallel work with subagents, in waves.** Work that touches different files runs at once: one subagent per issue, each in its own git worktree and branch. The session the owner talks to is the coordinator: it files a wave's issues first, gives every subagent the list of files it owns (one writer per file per wave), and merges the PRs itself, one at a time, rebasing on `main` first when `main` has moved (branch protection does not require up-to-date branches). Workers never append to the checklists or progress logs; the coordinator does. At most five subagents at once, across all tracks. Local `task test-e2e` uses fixed ports, so parallel workers leave E2E to CI. The UI waves and file table are in `plan-ui-redesign.md` §7; the tracks are in §9 below. | Decided (owner asked, 2026-11-01) |
| D25 | **Models and effort for coordinated work.** Whenever a coordinator runs subagents (D24 waves and anything later): the **plan is written by Opus at high effort** with every detail a cheaper model needs, as an `## Execution` section in each issue (files owned, read-first list, ordered steps with exact names, test table, docs, commands, pitfalls, stop conditions); the **coordinator is Haiku at high effort**; each **subagent is Haiku high, Sonnet high or Sonnet medium**, chosen per issue by the planner (pattern-following work → Haiku; migrations, concurrency, security, new logic → Sonnet high; recipe-style edits → Sonnet medium). A subagent that fails twice goes one step up, not to Opus; a missing decision goes back to the planner or owner. **Always the newest release of each family:** plans, issues and prompts use the aliases `opus`, `sonnet`, `haiku`, never a pinned model ID ("5.5" is only what was newest on 2026-11-02). Detail: `.claude/context/workflow.md`. | Decided (owner asked, 2026-11-02) |
| D26 | **Phase 4 reuse rules (planned 2026-11-02 by Opus, D25).** These rules apply to copy and to templates alike. (1) **Merge** appends copied meals after the meals already in each target date and slot type. **Replace** first removes every slot of the whole target day or week, then writes the copy. (2) Copying onto the same day or week, or copying or saving something empty, is refused before any write (422). (3) Notes and archived meals are copied as they are. Copy and apply publish one `SlotPlanned` per new slot; no new event types. (4) Template items are frozen: plan templates can be renamed and deleted, and occasion templates only deleted. (5) Occasion menus are ordered meal lists without notes or versions. "Reuse next year" is save-as-template, then "plan it again" on a date. (6) **Nothing in Phase 4 is owner-only**: every member may do all of it. Another household's data is 404 (D8). (7) Everything lives in `planning`. Flyway V9 holds all six tables. | Decided (planner, 2026-11-02; owner confirmed the four defaults, 2026-11-02) |
| D27 | **Rating rules (planned 2026-11-03).** A rating is one per member per slot: `thumb` UP/DOWN (required), `stars` 1–5 (optional) and a `comment` of at most 500 characters (optional). It can be given on a slot dated today or earlier, and its author can edit or clear it. Every current member sees every rating of a slot or meal, by display name (never an email). A rating stores `meal_id`, `slot_date` and `slot_type` and has **no foreign key to `meal_slot`**, so ratings outlive a slot that D26's Replace removes; it does have a composite foreign key to `meal`. Events: `RatingGiven` (create and update) and `RatingWithdrawn` (clear); the second is new. The "how did it go" prompt shows the latest unrated meal of the last 7 days, one at a time, and "Not now" hides it for the rest of the day. `feedback` depends on `planning`, `catalog`, `household` and `identity`, never the reverse, so the plan pages load its fragments over htmx. | Decided (owner confirmed the defaults 2026-11-03; #124, #128, #132) |
| D28 | **Share-link rules (planned 2026-11-03).** *Token:* 256 bits from `SecureRandom`, base64url, 43 characters; only the SHA-256 hex is stored; the raw link is shown in exactly one response and is never stored or logged. *Scope and expiry:* one household and one ISO week per link; expiry 1, 7 (default) or 30 days, never unlimited; a link is active only while `revoked_at IS NULL AND expires_at > now`, checked in the lookup query itself; at most 10 active links per household; any member may create and revoke; expired rows older than 30 days are purged when a link is created (no scheduler, D11). *Route:* `GET /share/{token}` is the only unauthenticated business route. It matches the single-segment, GET-only pattern `/share/*` (never `/share/**`; `/share/x/`, `/share/x.json`, `/share/x/y` and any POST stay protected), writes nothing (no view counter, no session, no cookie, no CSRF token), and answers an unknown, malformed, expired or revoked token with one identical 404, a malformed one without touching the database. It sends `X-Robots-Tag: noindex, nofollow, noarchive`, `Referrer-Policy: no-referrer` and `Cache-Control: no-store`; `/robots.txt` disallows everything; the service worker never caches it. *Data:* the public read goes through `planning.SharedWeekReader`, an unchecked reader whose result type holds only dates, slot types, meal names and occasion names (no notes, ids, ratings, member or household names); an ArchUnit test limits who may call it. *Throttle:* one global in-memory fixed window (120 a minute, 429 with `Retry-After`), because the client IP is not reliable behind Cloud Run's proxy; **per-IP limiting comes with Phase 6's invite and join limiter**. *Logs:* Cloud Run's own request log contains the path, so `/share/<token>` appears in it; **accepted** (only the owner reads the logs, and links expire and can be revoked). A Cloud Logging exclusion for `/share/` is possible later but is a Google Cloud change and needs the owner's OK. The application never logs the token (a test checks it). | Decided (owner confirmed 2026-11-03; #125, #129, #131) |
| D29 | **Print and release (planned 2026-11-03).** Print is a dedicated light-theme page (`/plan/print?week=`, with slot notes, members only) built from the same fragment as the share page, not a print stylesheet bolted onto the shell. Lighthouse on a phone, the PWA installed on a real phone and the share-link walk-through are owner-only (#134). **v1.0.0** is cut by a manual `release.yml` run (`version=1.0.0`) once #134 passes, because the last Phase 5 PR is docs-only and does not release itself; the coordinator needs the owner's OK to trigger it. | Decided (2026-11-03; #131, #134) |
| D30 | **Lunch is the main slot (2026-11-03).** The week page shows **Lunch** every day by default. Breakfast, dinner and snack are optional: they show when they hold a meal, and otherwise sit behind an "Other meals" disclosure on the day card (phone) or a "Show all meals" checkbox (desktop table). The backend still has all four `SlotType`s, so copying, templates, occasions and ratings are unchanged. The print and share pages list only slot types that hold a meal. | Decided (owner chose design A 2026-11-03; #127) |
| D31 | **Ingredients (planned 2026-11-03, owner interview).** Each household has its own list of ingredients, **names only** (no amounts, no categories), linked many-to-many to meals the way tags are (`ingredient`, `meal_ingredient`, Flyway V12). Names are trimmed, at most 60 characters, at most 30 per meal; the key folds case **and accents**, so "tojas" finds and equals "tojás". A name that is not in the list is created by typing it. A global read-only table `ingredient_seed` holds about 150 common Hungarian names (the only table without `household_id`); picking one copies it into the household on first use. The picker is a new searchable combobox (a dropdown with a search bar); tags stay a text field. Creating a tag or ingredient is race-safe (a `REQUIRES_NEW` writer that re-reads after a conflict), which also fixes the old tag race. A shopping list and amounts stay parked. | Decided (owner, 2026-11-03; #155, #156) |
| D32 | **Assistant rules (planned 2026-11-03, owner interview).** *Provider:* Gemini on Vertex AI through Spring AI's Google GenAI starter, using the runtime service account, so there is no API key (the owner wants a dedicated client, not a personal subscription; Spring AI's Anthropic module cannot use Vertex). *Scope:* library meals first, plus up to 5 "ideas" per draft that become library meals only when a person adds them; never planned by themselves. *Surfaces:* week draft, one slot, a chat page limited to food and the plan (a prompt file, widenable later), occasion menus. *Data:* meal names, tags, ingredients, dates, slot types and ratings with comments, members as "member N"; no dietary rules or allergies stored *(Amended 2026-11-04: the UI no longer says so; was "the UI says so")*. *Language:* the member's. *Memory:* the chat lives in the session only. *Seasons:* the model decides from the date and "Hungary". *Cap:* $5 a month per household in the app. *Index:* custom `meal_embedding` table, synced lazily per request by content hash (no events, no scheduler). *Off by default* (`mm.ai.enabled`). The model id, EU region and embedding size are fixed by #157's spike. *(Amended 2026-11-04, #157 spike: the chat model that answered is `gemini-3.5-flash` in the `eu` multi-region (`europe-west3` also answered for chat); the embedding model is `gemini-embedding-001` in `europe-west3` at 768 dimensions, because it is not offered in `eu`, so the config has its own embedding location. Prices per million tokens, not global: `gemini-3.5-flash` $1.65 in and $9.90 out. `gemini-2.5-flash` is retired. Google's terms: it does not train on customer data without permission, but it may log prompts for abuse monitoring, which the privacy note must say. The final chat model is D33.)* | Decided (owner, 2026-11-03; #157 to #162) |
| D33 | **Which Gemini model to use, and its prices.** Chosen from the eval report (#162) by the owner. | Pending (#162) |

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
- [x] #12 Identity: Google OIDC login, the DB allowlist and `MM_ADMIN_EMAILS` bootstrap, `AppUser`,
      Spring Session JDBC, sign-out, fail-closed production config. *(PR #24. The owner ran the
      real-Google checks locally on 2026-11-01 (admin sign-in works, a refused account gets 403):
      passed, issue closed. The header has no sign-out button yet and the
      per-user language is not applied yet, both planned for Phase 1.)*
- [x] #13 Base layout: JTE layout, Tailwind standalone CLI, htmx + Alpine (CSP build), the `t()`
      i18n helper (hu/en) and language switcher, PWA manifest + service worker, error pages,
      htmx session-expiry handling (`HX-Redirect`). *(PR #23. Language is a cookie for now and
      the server-side `HX-Redirect` comes with #12; the client already redirects on 401/403.
      Home-screen install on a real phone is still a manual check.)*
- [x] #14 Tests: the Modulith verify test, an "every endpoint needs auth" test, the template
      hard-coded-text test, a docs-tooling test (router paths exist, rule globs match,
      `CLAUDE.md` under 100 lines). *(PR #33. The Modulith, template and bundle guards already
      existed from #9 and #13; this adds the endpoint walk and the docs tooling, and makes
      `task docs:check` real. The tenant-isolation guard waits for the first household endpoint.)*
- [x] #15 `Dockerfile` (multi-stage, pinned by digest, uid 10001, AppCDS) and `compose.e2e.yml`
      with a stub OIDC provider; one Playwright-Java e2e test (sign in → hello page) on phone
      and desktop viewports. *(PR #32. The cache is a JDK 25 AOT cache, see D17. Cold start
      measured: 1.3 s with the cache vs 2.9 s without on H2; the Cloud Run number comes with #18.)*
- [x] #16 CI: `ci.yml`, `docs-check.yml` (+ `scripts/check_docs_touched.sh`), `wiki-sync.yml`,
      `dependabot.yml`, PR template already in place. *(PR #29. The E2E and Trivy steps pass
      without running until #15 adds the Dockerfile and `compose.e2e.yml`. The four checks are required in
      branch protection (2026-11-01, owner OK). Still open, owner: create the wiki's first page in
      the web UI so the sync can push; then #16 can close. Done 2026-11-02: the owner created the page and the docs sync.)*
- [ ] #17 Hosting (D11): Cloud Run service + Neon Postgres, secrets in Secret Manager, deploy
      through Workload Identity Federation. The owner does all console steps in **one setup
      session** up front (Google Cloud project, billing + budget, OAuth client, Artifact Registry,
      service accounts, WIF, secrets, Neon project, GitHub variables, auto-merge); agents write
      the deploy workflow and do the rest from CI. *(Owner setup session done 2026-11-01, parts
      1–8 of `Hosting-Setup.md`, by hand; `gcloud` is signed in on the owner's machine, so agents
      can read Cloud Run status and logs. The setup is not yet verified by a script: #27, parked.)* Runbooks: `documentation/Hosting-Setup.md`,
      `documentation/Auth-Setup.md`.
- [x] #18 `release.yml` → first deploy **v0.1.0**: sign in on a phone and see the hello page.
      Confirm that the app and the database really scale to zero when idle. *(Workflow written
      in PR #34; the first run failed on an env-var delimiter, fixed in PR #35; the second run
      deployed **v0.1.0** (2026-11-01): health 200, `/` redirects to `/login`, Flyway applied the
      3 migrations on Neon, the app started in 6.7 s on its first start. Still open, owner:
      sign in on a phone; and confirm scale to zero (Cloud Run has no minimum instance, Neon
      suspends after ~5 idle minutes; the instance count after idle is not checked yet).)*
- [ ] #51 CD: release automatically when a PR is merged into `main`. *(Owner asked 2026-11-01. `release.yml` now has a `plan` job (`scripts/next_release.sh`: next patch version, `release:minor` / `release:major` label or the manual `bump` input for the other parts, skips docs-only merges and commits older than the last release) and the `release` job as before plus a GitHub Release. PR #52; the merge of that PR is the first automatic release.)*
- [x] #58 CD: post-deploy smoke test against the live service. *(Filed 2026-11-01; built 2026-11-02,
      PR #108. `task smoke -- <url>` (`scripts/smoke.sh`) runs once per release in `release.yml`, after the health poll and before the tag is
      pushed, and checks anonymously: health, the login page, a protected path redirecting to
      sign-in, the security headers, static assets, no 5xx. Not in the PR workflow (the `E2E` job
      covers the image there) and never on a timer (D11). Blocked by #18.)*
- [x] #37 CI: make the E2E job faster (was ~3m20s per PR): BuildKit `type=gha` layer cache for the
      image, Playwright Chromium and Tailwind CLI caches, Trivy scans the image the e2e run used.
      *(Owner added it to the plan 2026-11-01. Docs-only E2E skipping (PR #39) keeps the job and
      its name and skips only its steps, so branch protection is unchanged. PR #38: E2E job 3m19s → 2m16s
      with warm caches (image build 147 s → 20 s); the first run on a new cache key is slower.)*

### Phase 1 — Households (milestone `M1 – Core planner`, epic #2)
Sub-issues filed 2026-11-01: #41 → #42, #43, #44 (blocked by #41); #45 stands alone.
- [x] #41 Create a household (the creator becomes OWNER), rename it, switch between households
      (stored in the session). *(PR #46. The session id is re-checked against `membership` on every
      request; the header shows the current household as a link, and switching is on the
      `/households` page, not in a header dropdown yet.)*
- [x] #42 Email-bound invites: create, list, revoke, `/join/<code>`, expiry options. *(PR #49.
      Opening the link only shows the household; joining is a separate POST. `/join/**` is not
      public: an anonymous visitor signs in first and Spring Security brings them back. The
      address stays on the allowlist when an invite is revoked; removing it is #45.)*
- [x] #43 Members: list, remove (owner), leave, transfer ownership. The last owner can't leave.
      *(PR #50. Transfer is a hand-over: the chosen member becomes OWNER and the asking owner a
      plain MEMBER, in one transaction, so "last owner" in practice means "the owner". Changes to
      who is in charge lock the household row first, so two owners leaving at once cannot both
      pass the check. A removed member needs no extra step to lose access: the household is
      re-checked against `membership` on every request. The page is `/households/members`.)*
- [x] #44 Tenant-isolation test (404 for another household's data), plus 403 tests for owner-only
      actions. *(PR #53. `TenantIsolationTests` finds every controller method with a
      `CurrentHousehold` parameter or a `{householdId}` path variable and fails if it is missing from
      `TenantEndpoints`; each registered endpoint is then called as four people of a two-household
      fixture. The household routes' `{id}` was renamed `{householdId}` so they are detected.
      Mutation-checked: dropping the household scoping in `InviteService.revoke` fails it. An
      endpoint with no id in its path always acts on the caller's own household, so only the
      "person in no household" check applies to it.)*
- [x] #45 Admin page for the allowlist (instance admins only). *(PR #56. `/admin/allowlist` lists the
      bootstrap admins (fixed) and the `allowed_email` rows, adds and removes addresses. The
      `/admin/**` rule in `SecurityConfig` (`hasRole("ADMIN")`) gives non-admins 403; admins get a
      header link. Removing an address blocked only the next sign-in at first; #59 now ends the live
      sessions too.)*
- [x] #59 End a person's live sessions when their address is removed from the allowlist (child
      of #2, `type:security`). *(PR #67. `AllowlistAdminService.remove` maps the address to its
      `app_user` rows and deletes the sessions whose principal name is that person's Google `sub`
      through `FindByIndexNameSessionRepository`, in the same transaction as the allowlist delete.
      Known limit: a sign-in that read the allowlist just before the removal committed could still
      create a session; the window is milliseconds.)*

### UI redesign (`M1`, epic #57) — **next, before Phase 2**
Full plan: [`plan-ui-redesign.md`](plan-ui-redesign.md) (design system, shell, components, phases
R0–R9). Epic #57 was filed 2026-11-01; its sub-issues are filed as each wave starts, and the `R#`
ids in that file are replaced with the real numbers then.
- [x] R0 Plan and design agreed. *(PR #55, PR #60.)*
- [x] Wave 1: R1 tokens, fonts and base CSS; R2a icons, button and the `/dev/ui` page (merged after
      R1; PRs #69, #70). R7 app icons landed early (PR #61); its manifest colours moved into R1.
- [x] Wave 2: R2b content and status components, R2c form components, R3 sheet and confirm dialog
      (PRs #77, #76, #78).
- [x] Wave 3: R4 shell and navigation (the only UI item; PR #82, with B3 in PR #81).
- [x] Wave 4: R5a sign-in/error/hello and R5c admin allowlist (#85, PR #90), R5b household pages
      (#86, PR #92), R6 theme preference (#87, PR #91), plus the Phase 2 pages (#88, PR #95).
- [x] Wave 5 (issues #96–#99, planned by Opus on 2026-11-02, D25): R5d remove the old styles
      (#96, PR #100); week view 1/2 (#97, PR #101, beside it); week view 2/2 (#98, PR #104);
      retire the hello page (#103, PR #105); R8 docs final pass (#99, PR #106). Only R9, the
      close-out with the owner, is left.
- Order: Phase 2 and 3 **pages** are built from these components, so they start once R1–R4 have
  landed (Phase 2 pages in Wave 4, Phase 3's week view after them). Their **backend** (module,
  migrations, services, tests) does not wait: it runs beside the waves (§9). *(Amended 2026-11-01;
  was: Phase 2 as a whole starts once R1–R4 have landed.)*

### Phase 2 — Meal library (`M1`, epic #3)
Filed as two sub-issues so the backend can start before the redesign is done (D24, §9): **B2
catalog backend** (the `catalog` module, Flyway **V6** `meal`/`tag`/`meal_tag`, `MealService` for
create, quick-add, edit, archive, tags, search and filter, the `MealChanged` event, tenant-scoped
queries, tests; no controllers or templates) and **the meal pages** (controllers, templates built
from the `ui/` components, `TenantEndpoints` registrations, e2e; after R4). "Recently used first"
needs `meal_slot`, so it lands with the pages once B3 is in.
- [x] #66 **B2 catalog backend** *(PR #68; backend only, no pages)*: V6 `meal`/`tag`/`meal_tag`
      (composite foreign keys so the database refuses a cross-household link), `MealService`
      (create, quick-add, edit with a tag diff, archive, restore, search by name and tag),
      `MealChanged`, tenant-scoped, recipe links limited to http/https. **Left out on purpose:**
      the occasion types a meal suits (which module owns the enum is undecided; decide before the
      meal pages or add a later migration) and "recently used first" (needs B3's `meal_slot`).
- [x] Meal CRUD: name, notes, recipe URL, tags, which occasion types it suits, archive. *(Pages: #88, PR #95. The occasion-type enum is owned by `catalog` (planning already depends on catalog, so the other way round would be a cycle), added with Flyway V8; owner OK'd 2026-11-02.)*
- [x] Tags per household; search and filter (name, tag), recently used meals first. *(#88, PR #95; the
      library sorts by `RecentMealsProvider`, which `planning` implements.)*
- [x] Quick-add from a phone (name only, add the details later). *(#88, PR #95: a floating button and
      sheet on a phone, an inline bar on a desktop.)*

### Phase 3 — Weekly plan (`M1`, epic #4)
Also split (D24, §9): **B3 planning backend** (the `planning` module, Flyway **V7** `meal_slot`
with a foreign key to `meal`, so after B2; the seven-date week query, add/move/reorder/remove a
slot, the note, `@Version` with a typed conflict exception the page turns into 409, `SlotPlanned`;
no controllers or templates) and **the week view** (after R4 and the Phase 2 pages).
- [x] #80 **B3 planning backend** *(PR #81; backend only, no pages)*: V7 `meal_slot` (composite
      foreign key to `meal`), `PlanningService` (week query, add, move, reorder, note, remove,
      `recentlyPlannedMealIds` for "recently used first"), `@Version` with `PlanConflictException`
      (409), `SlotPlanned`, tenant-scoped. Archiving a meal leaves its slots alone; a still-planned
      meal cannot be hard-deleted.
- [x] Week view: a grid on desktop, a list of days on phone; ISO-week navigation; today highlighted.
      *(#97, PR #101; read-only, at `/plan`. A 7 × 4 table from 1024 px, a week strip plus day
      sections below that. The Plan tab now points to `/plan`. Since #103 `/` redirects there.)*
- [x] Slots: BREAKFAST / LUNCH / DINNER / SNACK. Add a meal from the library or create one inline;
      a note per slot; reorder and move slots. *(#98, PR #104: one shared pick sheet with search,
      recent meals and quick add; a slot sheet for the note, move, reorder and remove, which asks
      first.)*
- [x] Optimistic locking (`@Version`) with a friendly "someone else changed this, reload" message.
      *(#98, PR #104: the 409 shows a banner with a Reload button, tested with two browser tabs.)*

### Phase 4 — Reuse and occasions (`M1`, epic #5)
Split for subagents (D24, D25; planned 2026-11-02): one backend slice (Flyway **V9**, all six
tables), then three page issues side by side, then the occasion menus and the docs close-out. Rules: D26.
- [x] #109 **Phase 4 backend** *(backend only; PR #115)*: V9 `plan_template`/`plan_template_item`,
      `occasion`/`occasion_item`, `occasion_template`/`occasion_template_item`; `PlanningService.copyDay`/`copyWeek`,
      `PlanTemplateService`, `OccasionService`, `OccasionTemplateService`, `SlotWriter`; tenant fixtures.
- [x] Copy a day onto another date, and copy a week onto another week (merge or replace). *(#110, PR #118)*
- [x] `PlanTemplate` with scope DAY or WEEK: save from a plan, apply to a date or week (merge
      or replace) *(#110, PR #118)*, rename and delete *(#111, PR #116: the `/plan/templates` list and detail pages; rename and delete are done)*.
- [x] Occasions: a dated event (name, date, type: birthday, holiday, party, other) with its own
      menu items; shown on the week view; save as a template and reuse next year. *(#112 pages and
      the Occasions tab, PR #117: done; #110 the strip on the week, PR #118: done; #113 the menu and occasion templates, PR #119: done)*

### Phase 5 — Feedback, sharing, PWA polish (`M1`, epic #6) → **v1.0.0**
- [x] Rate an eaten meal: each member gives a thumbs up/down or 1–5, with an optional comment;
      a "how did it go" prompt on past slots. *(D27: #124 backend and Flyway V10, PR #139; #128 the
      rating form and the prompt, PR #142; #132 the history on the meal page, PR #144. Known gap, in
      the #124 service: the list's limit of 10 is applied before former members' ratings are dropped.)*
- [x] Print-friendly week view; read-only share link (hashed token, expires, can be revoked).
      *(D28, D29: #125 backend and Flyway V11, PR #138; #129 the public route, PR #143; #131 the print
      view and link management, PR #145.)*
- [ ] PWA polish: icons, theme colour, install hint. Lighthouse check on a phone. *(Icons and the
      theme colour landed with #61 and R1; #126 adds the install hint, manifest shortcuts, iOS metas
      and a service-worker guard. Lighthouse and the real-phone install are #134, the owner's.
      The code landed in PR #136.)*
- [x] Lunch first: the week shows Lunch by default, other meals on request (#127, D30; PR #137).
- [x] Heading font: Baloo 2 replaces Fredoka, so ő and ű are drawn in one family (#130, PR #141; the
      owner chose it on 2026-11-03 after a specimen comparison). Open for the owner's walk-through: whether
      the largest headings go to weight 800 (the build uses 600–800 and the headings use 600 and 700).
- [x] #133 Phase 5 docs close-out (PR #147).
- [x] #134 the owner's checks (Lighthouse on a phone, the PWA installed on a phone, the share-link
      walk-through, the rating error state on a phone, the Baloo 2 weight), then **v1.0.0** (D29).
      The owner's checks passed on 2026-11-03 (*Amended 2026-11-03; the owner's words: "everything
      looks good"*), and **v1.0.0 was released on 2026-11-03** (tag `v1.0.0`, smoke test passed). The release was a manual `release.yml` run with `version=1.0.0`, and the
      coordinator needs the owner's go-ahead for it.

### Phase 6 — Hardening (`M2 – Hardening`, epic #7)
*Order (amended 2026-11-03; was: backups first, then the rest in no fixed order, all before Phase 7):*
**6a** backups (#62), **6b** ingredients (6b-0) and then the AI assistant (Phase 7 below), **6c** the
repo audit, **6d** the rest (GDPR basics, security hardening, the cost review). Each stage lists the
owner's manual steps. Agents write the code, tests and docs. Google Cloud and Neon changes need the
owner's OK first (§9). *(Amended 2026-11-03, owner interview; 6b-0 added. Issues filed: backups #62 and
#154; ingredients #155 and #156; assistant #157 to #162. Planned by a Sonnet session at the owner's
request, not Opus (D25), the same exception as Phase 5; the briefs say what to do on a gap.)*
*(Amended 2026-11-04, owner: **6a is done after 6b**, not before it. The code for 6a is merged except the
workflow (#62, PR #166), which stays open and green until the owner has done the setup in
`documentation/Backup-and-Restore.md`: it makes every release wait for a backup, so merging it before the
setup would stop all releases. Until then only Neon's ~6-hour restore window protects the data, and the
assistant's migrations (V13 to V15) deploy without a pre-release backup.)*

- [ ] **6a Backups (#62 workflow and scripts; #154 setup script, restore and guide).** *(Owner, 2026-11-01:
      after the last regular feature and before the AI assistant; Neon Free's own restore window is only
      ~6 hours, which is why this is needed; cost under a cent a month. GitHub artifacts are out: the repo
      is public. Amended 2026-11-03 after the owner interview; was: weekly tier, 12-month retention, an
      in-workflow freshness check and a manual restore drill. The changes are marked below.)* Design:
  - **Read-only role.** A Neon role `mm_backup` with `CONNECT`, `USAGE` and `SELECT`, plus default
    privileges, so Flyway tables added later are readable too. Its password is in Secret Manager as
    `backup-db-password`.
  - **Dump.** `pg_dump --format=custom --no-owner --no-privileges`, with the PostgreSQL 17 client from
    a digest-pinned image, against Neon's direct endpoint. The custom format is compressed and lets
    `pg_restore` pick single tables. *(Amended 2026-11-03: `--exclude-table-data='spring_session*'`;
    sessions are throw-away and restoring them would sign old browsers back in.)*
  - **Test restore in the job.** Restore into a `pgvector/pgvector:pg17` service container, then check
    that the Flyway history reaches the latest version and that row counts match the source (sessions
    excluded). A failed restore fails the job: a dump that cannot be restored is no backup.
  - **No encryption.** *(Amended 2026-11-04, owner; was: encrypted with `age` to a public key in the
    variable `BACKUP_AGE_RECIPIENT`, with the private key in the owner's password manager.)* The dump is
    stored as a plain `*.dump` file. The data is household emails, display names, meals and rating
    comments; the bucket is private with public access prevention, the CI writer can only create
    objects, and Google encrypts at rest. The owner did not want a key to store or lose, which would
    have made the backups useless in exactly the disaster they are for. The trade-off: whoever can
    read the bucket (the owner's Google account, project owners) can read a dump, and a dump
    downloaded for a restore is plain text on that machine. Encryption can be added back later if the
    data becomes more sensitive.
  - **Upload.** The existing WIF pool and provider, and a new service account `mm-backup-writer` with
    `roles/storage.objectCreator` on the bucket only, plus `secretAccessor` on `backup-db-password` only,
    so it cannot read or delete backups. Object names carry the timestamp, so nothing is overwritten.
  - **Bucket.** `meal-magician-backups` in `europe-west3`, uniform access, public access prevention
    enforced. *(Amended 2026-11-03; was `daily/`, `weekly/`, `monthly/` with 7 days, 5 weeks and 12
    months.)* Prefixes `daily/` (deleted after 7 days), `monthly/` (a copy on the 1st, deleted after ~190
    days) and `release/` (taken before each deploy, deleted after 14 days). Google's service agent runs
    the deletions, not the writer.
  - **Schedule and release.** A nightly cron (02:17 UTC), and a `workflow_call` from `release.yml` as a job
    the release job `needs`, so a failed backup stops a release before its Flyway migrations run.
  - **Failures.** *(Amended 2026-11-03.)* GitHub's email, plus the job opens, or comments on, one open issue
    titled `Backup failed`, and closes it on the next green run, so a failure stays visible on the board.
  - **Silent stop.** *(Amended 2026-11-03; was a "newest object older than two days" check inside the
    workflow, which cannot run once GitHub disables the schedule.)* A Cloud Monitoring alert emails the
    owner when no new object has landed in the bucket for two days. It lives outside GitHub. A Google Cloud
    change, so the owner approves it.
  - **Restore.** `task db:restore -- <file-or-gs://object>` runs `pg_restore` into a target chosen by
    flag: local Postgres (compose), a new Neon branch or a new Neon project. It is started by a person, never
    by CI: it overwrites a database, and the CI writer is deliberately unable to read backups. It needs only
    the owner's own Google login to fetch a `gs://` object. The wiki page "Backup and Restore" is the
    guide. *(Amended 2026-11-03; was a restore drill by the owner, logged on a wiki page. The owner
    declined a recurring manual task: the nightly test restore is the continuing proof, and the guide has
    an optional 15-minute check. Amended 2026-11-04: with no encryption there is no key that could fail to
    match, so the nightly check now covers what a restore needs, except that the app boots on the data.)*
  - **Pitfalls.** GitHub disables scheduled workflows in a public repo after 60 days without repo
    activity, which the alert above catches. Each nightly dump wakes Neon for a few minutes; Neon Free
    gives 100 CU-hours a month, so check the first month against it (§6).
  - **Owner's manual steps** *(amended 2026-11-03; amended 2026-11-04: the `age` key step is gone)*:
    1. In Neon, create the read-only role `mm_backup` (the setup script prints the exact SQL) and store
       its password in Secret Manager as `backup-db-password`.
    2. Approve each step as an agent runs `scripts/backup-gcp-setup.sh` (bucket and lifecycle rules,
       `mm-backup-writer`, its WIF binding, the Monitoring alert), and set the GitHub variable
       `BACKUP_BUCKET` to `meal-magician-backups`. Nothing else is created by hand.
    3. Check that the budget alert covers the bucket (expect cents a month).
- [ ] **6b-0 Ingredients on meals (#155 backend with Flyway V12; #156 picker)** *(added 2026-11-03, owner
      request: the assistant should suggest seasonal meals and ingredients; was in "Later ideas")*. See D31.
      Done before the assistant, because the assistant reads ingredients.
- [ ] **6b AI assistant:** Phase 7 below (#157–#162), built after 6b-0 *(amended 2026-11-04; was: after 6a and 6b-0)*.
- [ ] **6c `repo-audit`:** run the skill on the whole repo and reconcile its findings with the issues and
      the board. Known gaps for it to check: the rating history limit (accepted by the owner, check it is
      documented), the `share.limit` message that hard-codes "10", the tag race in B2 (two people creating
      the same new tag at once), and the per-IP limiter that is still to come. **Owner:** approve filing
      issues outside this plan (`CLAUDE.md`).
- [ ] **6d GDPR basics:** export my data as a file, delete my account, and a privacy note page.
      **Decisions for the owner:** what deleting a member does to shared content (meals stay, while the
      person's name, email and ratings go, as they already do for former members). A deleted account
      stays in the backups for up to 6 months *(amended 2026-11-03; was 12: the monthly copies are kept
      190 days)*, which the privacy note must say, together with the fact that the dumps are stored
      unencrypted in a private bucket *(added 2026-11-04)*. **Owner:** approve the
      wording of the note.
- [ ] **6d Security hardening:** review the security headers and the CSP; rate limit on invite and join,
      per IP, using the address Cloud Run appends rather than the first one the client sends (D28); a
      request size limit.
- [ ] **6d Cost and cold starts (D17):** after a month of nightly backups and the assistant running, read
      the bill against the $5 budget and the cold-start times, then record whether to stay on the JVM or
      move to native. **Owner:** read the bill.

### Phase 7 — AI assistant (`M3 – Assistant`, epic #8)
*Built as stage 6b, before the backups (#62) and the repo audit (amended 2026-11-03; amended 2026-11-04,
owner: the backups come after the assistant, not before it).*
- [x] **Before any code** *(amended 2026-11-03; the spike and the owner's Vertex steps are done, 2026-11-04, #157)*: Spring AI 2.0 GA (2026-06-12) targets Boot 4.0/4.1 per
      its release notes; #157 proves it with a real call (the spike). **Owner:** provider and tier are
      decided (D19, D32): Gemini on Vertex AI, no API key. Left for the owner: enable the Vertex AI API and
      grant the runtime account `roles/aiplatform.user` (both Google Cloud changes, approved step by step).
- [ ] `assistant` module (#157–#162, in this order; #157 also lists the owner's manual steps). Design:
  - **Module shape.** A public API `AssistantService`. `internal/` holds the tools, the prompt builder,
    the model gateway, the embedding index and the controllers. It depends on `planning`, `feedback`,
    `catalog`, `household` and **`identity`** *(amended 2026-11-03: the member's language and user id come
    from `SignedInUser`)* only. `planning` cannot depend on `assistant` (a cycle), so the plan page loads
    the assistant's buttons as htmx fragments (`GET /assistant/entry`), which answer 204 when it is off.
  - **Provider** *(amended 2026-11-03; was a key in Secret Manager as `MM_AI_API_KEY`)*. Gemini on Vertex
    AI through Spring AI's Google GenAI starter, authenticated by the Cloud Run runtime service account
    (Application Default Credentials). There is no API key and no `ai-api-key` secret. Spring AI's
    Anthropic module cannot use Vertex, which is why Claude is not the default. The model id, the EU
    region and the prices come from properties and are fixed by #157's spike. `mm.ai.enabled=false` is
    the default: the assistant is hidden, not an error, and also off on H2. Prompts and answers are never
    logged.
  - **Embeddings** *(amended 2026-11-03; was in-process ONNX)*. Vertex embeddings, 768 dimensions, set in
    the migration after the spike. No 90 MB model in the image, no extra memory and no longer cold start.
  - **Index** *(amended 2026-11-03; was `PgVectorStore` re-indexed on `MealChanged`, `RatingGiven` and
    `RatingWithdrawn`)*. A table `meal_embedding` (Flyway V14, `postgresql/`) with `household_id` on every
    row and a composite foreign key to `meal`. The index is brought up to date lazily at the start of an
    assistant request: a SHA-256 of each meal's document (name, tags, ingredients, notes, occasion types;
    ratings are not embedded) is compared with the stored one, and only changed meals are embedded. Why:
    Cloud Run throttles CPU after the response, so background re-indexing would not run, and no event
    listener exists in the code. Every search filters by household, and a Testcontainers test asserts it.
  - **Tools** *(amended 2026-11-03)*. `recentWeeks`, `searchMeals`, `mealDetails`, `householdPreferences`,
    `upcomingOccasions`, `currentDate`. All read-only: no tool writes. The household and user are bound
    when the tools object is built, never taken from the model. The model returns a structured draft that
    the server validates against the catalog.
  - **Drafts** *(V15)*. `assistant_draft` and `assistant_draft_item`: date, slot type and either a library
    meal id or an "idea" (name, reason, tags, ingredients). Unknown ids are dropped, with one automatic
    retry; at most 5 ideas. An idea reaches the plan only after "Add to library". Accepting copies the
    library items into the plan through `PlanningService.applyDraft` (D26 merge or replace), which
    publishes `SlotPlanned`. Drafts older than 7 days are purged when a new one is created.
  - **Surfaces** *(owner, 2026-11-03)*. "Suggest my week" (draft, follow-ups, accept or discard), "suggest
    one slot" in the meal-pick sheet, a free chat page limited to **food and the plan** (the limit is a
    prompt file and the property `mm.ai.chat-scope`, so widening it later is not a code change) and menu
    help on an occasion. Every member may use it. *(Amended 2026-11-03: the look of these surfaces is
    `plan-ui-redesign.md` UI-D12 and Appendix D. The assistant is called "Varázsló / Wizard" in the
    UI, the `wand` icon marks it, and the nav item uses `IconName.WAND`.)* *(Amended 2026-11-04,
    Appendix D: a week request is at most 1 000 characters and may carry up to four quick-request
    hints, which the controller expands into translated text (so the stored request is up to 1 200
    characters); a chat message is also at most 1 000
    characters; Cancel while a draft is being made only aborts the browser's request, the server call
    may finish and its draft is purged with the others; and the Plan page's entry fragment also says
    whether the week is empty, so the page can show a hint. Where the briefs of #160 and #161 differ
    from Appendix D, Appendix D wins.)*
  - **Seasons.** The prompt carries today's date and "Hungary, Central Europe"; the model decides what is
    in season. No seasonal table is shipped. The eval (#162) has a few checks.
  - **Prompt injection.** Meal names, notes, ingredients and rating comments are typed by members. They
    are quoted as data, the tools cannot write, and the server re-validates every id.
  - **Privacy.** Prompts carry meal names, tags, ingredients, slot types, dates and ratings, **including
    rating comments**; members appear as "member 1", "member 2", never by name or email. A comment can
    still contain a name a member typed; the privacy note says so. Vertex does not train on customer data
    by default (#157 quotes the current terms). **No dietary rules or allergies are stored** (owner's
    choice); the prompt forbids claiming a meal is safe for an allergy. *(Amended 2026-11-04, owner: the
    UI no longer says so; was "the UI says so". The prompt rule stays: it is invisible and costs nothing.)*
    The chat is
    kept in the session only and discarded afterwards. Drafts keep the request text until purged.
  - **Spend cap** *(amended 2026-11-03; was a cap set by the owner later)*. $5 a month per household,
    kept in `assistant_usage` (V13), checked before every call and added to atomically after it; over the
    cap the member gets a message and no call is made. It equals the whole project's budget alert, so it
    is a ceiling: real use should be cents. Vertex has no hard billing cap, so this check is the control.
  - **Owner's manual steps:**
    1. Enable the Vertex AI API and grant `mm-runtime` the role `roles/aiplatform.user` (an agent runs the
       commands, you approve).
    2. Set the GitHub variables the release workflow reads (`AI_ENABLED`, `AI_LOCATION`, `AI_CHAT_MODEL`,
       `AI_EMBEDDING_MODEL`, `AI_PRICE_IN`, `AI_PRICE_OUT`) and approve the `release.yml` change.
    3. Confirm the monthly cap per household (default $5).
    4. Review the eval report (#162) and sign off the model (D33).
    5. Try the drafts on your phone and judge them, as with R9. *(Amended 2026-11-04: also judge the
       chat page, which shows two gold marks on a phone, Appendix D.1. Appendix D says R9 judges it,
       but R9 may be closed by the time the page exists, so the check is in this step too.)*
- [ ] Preference profile per member: computed on demand from `RatingService.recent`, with no table
      (#159). *(Amended 2026-11-03; was recomputed on `RatingGiven` and `RatingWithdrawn`.)*
- [ ] Embeddings and index (#158); tools and drafts (#159); week and slot UI (#160); chat page and
      occasion help (#161).
- [ ] A small eval set (12–16 cases in `src/test/resources/eval/`) to pick the model (#162). Checks: only
      real meal ids, no meal twice in a row, "less meat" respected, occasions respected, language,
      ideas capped, a few seasonal checks. It runs by hand through `task ai:eval`, since it costs money
      and varies from run to run, so not in CI. Record the choice as D33.

### Later ideas (`Mx – Parked`)
A generated shopping list (needs amounts on ingredients, which D31 leaves out), meal photos, leftovers and "eat again soon" hints,
an ICS calendar feed, a custom domain, email notifications.

---

## 4. Architecture

### 4.1 Modules (Spring Modulith, `hu.lasterbergamot.mealmagician.<module>`)

| Module | Owns | Publishes / listens |
|---|---|---|
| `identity` | Google OIDC sign-in, `AllowedEmail`, `AppUser`, the instance-admin page, locale preference | publishes `UserSignedIn` |
| `household` | `Household`, `Membership(role)`, `Invite`, the current-household resolver | listens for nothing; its API is used by every module below |
| `catalog` | `Meal`, `Tag`, `Ingredient` *(Amended 2026-11-03, D31)* | publishes `MealChanged` |
| `planning` | `MealSlot` (per date), `PlanTemplate(scope DAY|WEEK)`, `Occasion`, `OccasionTemplate` | publishes `SlotPlanned` |
| `feedback` | `Rating` (member × slot) | publishes `RatingGiven` and `RatingWithdrawn` *(Amended 2026-11-03, D27; was `RatingGiven` only)* |
| `sharing` | print view, `ShareLink` | uses `planning.SharedWeekReader` (an unchecked, minimal read) and `catalog.MealNameLookup` *(Amended 2026-11-03, D28; was `planning`'s API)* |
| `assistant` *(Phase 7)* | tools, drafts, the embedding index, spend ledger, chat | no listeners *(Amended 2026-11-03, D32; was: listens for `MealChanged`, `RatingGiven` and `RatingWithdrawn`. The index is synced lazily per request, so no events are needed)*; uses `planning`, `feedback`, `catalog`, `household` and `identity` |
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
- `meal_slot(id, household_id, date, slot_type, position, meal_id, note, version)`. *(Amended 2026-11-01;
  was: `meal_id NULL, free_text NULL`. B3 (#80) shipped `meal_id NOT NULL` and no `free_text`: Phase 3
  adds a meal inline through the library, so free text is not needed yet.)*
  The plan is stored **per date**. A "week" is just a query over its seven dates, which keeps
  copying a day or a week trivial.
- `plan_template(id, household_id, name, scope DAY|WEEK, created_at, version)`, `plan_template_item(id, template_id, household_id, day_offset 0–6, slot_type, position, meal_id, note)`
- `occasion(id, household_id, name, date, occasion_type, notes, created_at, version)`, `occasion_item(id, occasion_id, household_id, position, meal_id)`,
  and `occasion_template(id, household_id, name, occasion_type, notes, created_at, version)` with `occasion_template_item(id, template_id, household_id, position, meal_id)`.
  *(Amended 2026-11-02, D26; was: `occasion_item(occasion_id, position, meal_id, note)` and "an occasion template (the same shape, without a date)". Item tables carry their own id and the household for the composite keys; menu items have no note.)*
- `rating(id, household_id, slot_id, meal_id, slot_date, slot_type, user_id, thumb, stars, comment_text, created_at, updated_at)`, unique on `(slot_id, user_id)`, composite foreign key to `meal`, no foreign key to `meal_slot`
  *(Amended 2026-11-03, D27; was `rating(slot_id, user_id, score, comment, created_at)`)*
- `share_link(id, household_id, token_hash, week_start, created_by, created_at, expires_at, revoked_at)`
  *(Amended 2026-11-03, D28; was `share_link(id, household_id, token_hash, scope, expires_at, revoked_at)`)*
- `ingredient(id, household_id, name, name_key)`, `meal_ingredient(meal_id, ingredient_id, household_id)`, global `ingredient_seed(id, name, name_key)` (V12, D31)
- `assistant_usage(household_id, month, input_tokens, output_tokens, cost_micro_usd)` (V13)
- `meal_embedding(meal_id, household_id, content_hash, model, dims, embedding vector(768), updated_at)` (V14, Postgres only)
- `assistant_draft(id, household_id, created_by, week_start, status, request_text, conversation, summary, created_at, version)` and `assistant_draft_item(id, draft_id, household_id, day_date, slot_type, position, meal_id NULL, idea_*)` (V15)
- Spring Session tables (`spring_session*`). *(Amended 2026-11-03, D32; was: Phase 7 adds `vector_store` (pgvector).)*

### 4.3 Request flow (for the Newcomer Guide)

1. The browser requests a page or an htmx partial.
2. Spring Security checks the session (Google OIDC sign-in when there is none) and the allowlist.
3. The current-household resolver loads the household from the session and checks membership.
4. The controller (in the module's `internal/`) calls the module's service.
5. The JTE template renders the result: a full page, or a fragment when `HX-Request` is set.

### 4.4 Cross-cutting

- **Security:**
  - Security headers and a CSP with no inline scripts.
  - Request size limit and rate limits on invite and join. The public `GET /share/*` route has one
    global in-memory throttle (D28); the full per-IP limiter for invite and join is Phase 6.
  - **Fail-closed production config:** the app won't start under the `prod` profile without
    the Google client id/secret, `MM_ADMIN_EMAILS`, a Postgres URL and
    `server.forward-headers-strategy=framework` behind Cloud Run's proxy.
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

## 6. Hosting and cost (Cloud Run + Neon, D11)

- **Services:** the Cloud Run service `meal-magician` (`europe-west3`, image from Artifact
  Registry, runtime service account `mm-runtime`) and a Neon project `meal-magician`
  (`aws-eu-central-1`, Postgres 17, database `mealmagician`, TLS required).
- **Variables:**
  - `SPRING_PROFILES_ACTIVE=prod`
  - `SPRING_DATASOURCE_URL`/`USERNAME` (GitHub variables) and `_PASSWORD` (Secret Manager
    `db-password`), Neon's direct (non-pooled) endpoint
  - `GOOGLE_CLIENT_ID` (GitHub variable), `GOOGLE_CLIENT_SECRET` (Secret Manager
    `google-client-secret`)
  - `MM_ADMIN_EMAILS`
  - `MM_AI_ENABLED` and the Vertex settings (project, location, models, prices) only when the assistant is on; no key (D32)
  - `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 …`
- **Scale to zero:** Cloud Run min instances 0, max 1; Neon autosuspend. Verify both really go
  idle; DB keepalives or outbound telemetry would keep Neon awake and burn its compute hours.
- **Deploy (D13):** `release.yml` authenticates with Workload Identity Federation (pool
  `github`, provider `github-repo`, service account `mm-deployer`), pushes the image to Artifact
  Registry and runs `gcloud run deploy` with the env vars and `--set-secrets`. Setup and the
  GitHub variables it reads: `documentation/Hosting-Setup.md`.
- **Watch:** the first month's bill (budget alert at $5). If it is clearly above zero, look at
  native (D17), memory limits and Neon compute hours first.

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
- **Spring AI and Boot 4:** Spring AI 2.0 GA (2026-06-12) targets Boot 4.0 and 4.1 per its release
  notes *(checked 2026-11-03 from the docs only)*; #157's spike confirms it with a real call and pins the
  version. **Vertex data location:** whether Gemini runs in `europe-west3` or needs another EU region is
  also fixed by that spike; if no EU region works, the owner decides.
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
- **Phase 0:** sign in on a phone against Cloud Run, check that someone not on the allowlist gets
  403, check that the app and the database scale to zero and wake up.
- **Phase 1–5:** the scenario from §1 on a real phone and a laptop, with two Google accounts in
  one household and a third account that isn't a member (it must get 404s). **Phase 5 adds:** a
  revoked or expired share link gives an anonymous browser a 404, the rating and share-management
  URLs of another household give 404, Lighthouse on a phone, and the PWA installed on a real phone.
- **Phase 7:** the eval report is read and the model signed off (D33); suggestions are only drafts; the spend cap is enforced; the assistant is hidden when off; another household's data never appears in a prompt.

---

## 9. Start here (next agent)

1. Read `CLAUDE.md`, then this file, then `.claude/context/workflow.md` and `pr-checklist.md`.
2. The repo, labels, milestones, epics (#1–#8) and Phase 0 issues (#9–#18, with blocked-by
   links) exist. Agents push, open and merge PRs for planned issues once checks are green
   (D22); still **ask the owner before** anything in Google Cloud or Neon, repo settings,
   or work outside the plan.
3. #9–#13 are done. **Order for the rest (amended 2026-10-31, to remove manual verification
   early):** #16 CI first, so "green" means verified; then #15 (Dockerfile, stub OIDC provider,
   Playwright e2e), so agents verify sign-in and screens without a real Google account; then
   #14; then #17/#18. *(Amended 2026-10-31; was #14/#15 → #16 → #18.)* #17's console steps (the
   owner's one setup session, `documentation/Hosting-Setup.md`) are **done**; nothing from the
   owner is needed until the first deploy (#18).
4. Then work on Phase 0 issue by issue: a branch per issue, PRs through `create-pr`.
5. Before scaffolding, look up the current latest versions (Spring Boot 4.x, Spring Modulith,
   JTE, the Gradle plugins, Testcontainers, Playwright) and pin them in the version catalog.
6. Before building a page of Phase 2–5, check `plan-ui-redesign.md`: the shell, tokens and
   components it defines come first, so a page is not built twice.
7. **Next up (2026-11-01; amended the same day for parallel subagents, D24; was: R1 first, #59
   before or beside it):** three tracks run at once. Each row is a wave; a wave starts when the
   one above it in the same column has merged. The coordinator files a wave's sub-issues (UI ones
   under #57, B2 under #3, B3 under #4) before starting its subagents.

   | Wave | UI track (`plan-ui-redesign.md` §7) | Backend track | Owner (no agent can do these) |
   |---|---|---|---|
   | 1 (now) | R1 tokens → R2a icons/button/`/dev/ui` (merged after R1) | #59 live sessions; B2 catalog backend (V6) | #16 create the wiki's first page; #18 sign in on a phone and confirm Neon suspends |
   | 2 | R2b, R2c, R3 | B3 planning backend (V7, after B2) | — |
   | 3 | R4 shell (alone in the UI) | B3 if not done; #58 smoke test once #18 is closed (#18 closed 2026-11-02; #58 is unblocked and still open) | — |
   | 4 | R5a + R5c (one agent), R5b, R6 | Phase 2 meal pages (uses R4 and B2) | UI-D8 (theme cookie) confirmed 2026-11-02 |
   | 5 | R5d, R8 | Phase 3 week view (uses R4, B2, B3) | R9 walk-through on a phone and a laptop |

   *Wave 5 as planned and filed 2026-11-02 (D25):* 5a R5d #96 (`haiku` high) beside week view 1/2
   #97 (`sonnet` high); 5b week view 2/2 #98 (`sonnet` high, alone, blocked by #97); then R8 #99
   (`haiku` high, docs only, blocked by #96–#98, because it rewrites the files the others edit).
   Merge order #96, #97, #98, #99. No Flyway version is needed (V9 stays free).

   Never more than five subagents at once. Flyway versions are reserved so parallel branches do not
   collide: V6 = B2, V7 = B3; anything else takes the next free number after rebasing (Flyway
   refuses two files with one version, so the tests catch a clash). #13 (PWA install on a real
   phone) and #17 are still open on GitHub only for owner checks; the phone install is part of R9.
   #27 stays parked. Backups (#62) come after Phase 5 and before Phase 7 (owner's choice): until
   then only Neon's ~6-hour restore window protects the data.

8. **Next up (2026-11-02, after Wave 5; D24/D25 apply):**
   - **Owner, no agent can do these:** R9 (a walk-through on a phone and a laptop in both themes;
     the gold on the Plan and Meals screens is judged live; Lighthouse accessibility and PWA; the
     PWA installed on a real phone, which is also #13). Then the owner may close the finished
     epics #2, #3 and #4 and the redesign epic #57 (agents do not close epics).
   - **Phase 4, reuse and occasions (epic #5)**, planned by Opus on 2026-11-02 (D25, D26) as five issues:

     | Wave | Items (model) | Merge order |
     |---|---|---|
     | 6a | #109 backend, Flyway V9 (`sonnet` high) | — |
     | 6b | #110 plan page reuse (`sonnet` high), #111 plan templates page (`haiku` high), #112 occasions pages + Occasions tab (`haiku` high) | #111, #112, #110 |
     | 6c | #113 occasion menus + templates + Phase 4 docs close-out (`sonnet` high) | — |

     #109 (Wave 6a) landed with V9; no other Phase 4 issue adds a migration (the next free number after it is V10).
     The Occasions tab appears with #112.
   - **Beside Phase 4, small and independent:** ~~#58, the post-deploy smoke test in `release.yml`~~
     *(Done 2026-11-02, PR #108.)*
   - **Then** Phase 5 (ratings, sharing, PWA polish; planned in item 9) → **v1.0.0** (done 2026-11-03), then
     Phase 6 in the order set in §3 *(Amended 2026-11-03; was: backups, Phase 6 hardening and Phase 7 in no
     fixed order; amended 2026-11-04: the assistant now comes before the backups)*: the AI assistant (Phase 7),
     backups (#62, #154), the repo audit, then GDPR basics, security hardening and the cost review. #27
     stays parked.

9. **Next up (2026-11-03, Phase 5; D24/D25 apply).** Planned into eleven issues under epic #6,
   with the briefs in the issues. A Sonnet planner wrote them because Opus's budget was used up
   (D25 says Opus). Blocked-by links are on GitHub.

   | Wave | Items (model) | Merge order |
   |------|---------------|-------------|
   | 7a | #124 ratings backend, V10 (`sonnet` high), #125 sharing backend, V11 (`sonnet` high), #126 PWA polish (`sonnet` medium), #127 lunch first (`sonnet` high) | #124, #125, #126, #127 |
   | 7b | #128 rating pages (`sonnet` high), #129 public share route (`sonnet` high), #130 Baloo 2 (`sonnet` medium) | #128 and #129 in either order; #130 after #126 |
   | 7c | #131 print view and share-link management (`sonnet` high), #132 meal history (`haiku` high) | #131, #132 |
   | 7d | #133 docs close-out (`haiku` high), then #134 the owner's checks and **v1.0.0** | #133 |

   Flyway: V10 is #124 and V11 is #125; V10 must merge, and release, before V11 (Flyway has no
   `outOfOrder`, and every code merge releases). #127 changes the templates that #128 and #131 also
   edit, so it merges first; #130 and #126 both touch `base.jte` and `sw.js`. At most four
   subagents run at once. Workers do not edit `PLAN.md`; the coordinator ticks the Phase 5 items and
   adds the log rows. The per-IP rate limit is deferred to Phase 6 (D28).

   *Status (2026-11-03):* waves 7a, 7b and 7c are merged (7a: #126 PR #136, #127 PR #137, #124 PR #139,
   #125 PR #138; 7b: #128 PR #142, #130 PR #141, #129 PR #143; 7c: #131 PR #145, #132 PR #144; 7d:
   #133 PR #147; the cap-race fix PR #150). Phase 5 is done: the owner's checks passed and **v1.0.0
   was released on 2026-11-03**. The coordinator does not trigger a release without the owner's
   go-ahead (D29).

10. **Next up (2026-11-03, Phase 6; the order is amended in §3).** Stages, with the owner's manual steps for
    each. Issues are filed when a stage starts, and each stage is planned by Opus (D25) when filed.
    *(Amended 2026-11-03: the issues for 6a, 6b-0 and 6b are all filed already, #62 and #154 to #162, by a
    Sonnet planner at the owner's request. Order of work: #62 and #154 beside #155 and #157; then #156 and
    #158; then #159; then #160; then #161; then #162. Merge order follows the blocked-by links. Flyway:
    V12 is #155, V13 is #157, V14 is #158, V15 is #159, and each must merge before the next, as in
    Phase 5.)*

    | Stage | Items | Owner's manual steps |
    |---|---|---|
    | 6a | #62 workflow and scripts, #154 setup, restore and guide (no migration) | Steps 1–3 under 6a in §3: the Neon read-only role and its secret, approving each `gcloud` step (bucket, writer, alert) and setting `BACKUP_BUCKET`, the budget check *(amended 2026-11-04; the `age` key step is gone)* |
    | 6b-0 | #155 ingredients backend (V12), then #156 the picker | None; look at the picker on a phone |
    | 6b | #157 foundation (V13), #158 embeddings (V14), #159 tools and drafts (V15), #160 week and slot UI, #161 chat and occasions, #162 eval | The Vertex API and role, the GitHub variables, the cap, the model sign-off (D33), the phone check of the drafts |
    | 6c | `repo-audit` | Approve filing issues outside this plan |
    | 6d | GDPR basics, security hardening, cost review | Privacy note wording, the deletion rules, reading the bill |

---

## 10. Progress log

| Date | What | Refs |
|---|---|---|
| 2026-10-31 | Base package `hu.lasterbergamot.mealmagician` (D20), Temurin 25 through the wrapper and toolchain (D21). Repo created on GitHub as public (free-plan branch protection and wiki). Labels, milestones, board #7, epics and Phase 0 issues filed. | #1–#18 |
| 2026-10-31 | Plan agreed with the owner (interview: hosting, auth, UI, AI, workflow). Repo initialised locally, ai-common `workflow` v0.1.1 installed through APM, docs skeleton written. | — |
| 2026-10-31 | Gradle skeleton: wrapper 9.8.2, Boot 4.1.2, Modulith 2.1.2, JTE 3.2.4, Temurin 25 toolchain, Spotless, Checkstyle (Javadoc), JaCoCo 100 % gate, empty module packages, `ModularityTests`. Gates proven to fail on untested/undocumented code. | #9, #20 |
| 2026-10-31 | `Taskfile.yml` with the workflow.md tasks (later ones stop with a message naming their issue), `scripts/doctor.sh`, checksum-pinned Tailwind CLI download, `task test:fast` via the `integration` JUnit tag. | #10, #21 |
| 2026-10-31 | `compose.yml` (pgvector Postgres 17, digest-pinned), `dev` profile on in-memory H2 and `postgres` profile, Flyway `common/` + `postgresql/` baseline (V1, V2 pgvector), Hikari `minimum-idle: 0`, shared Testcontainers base class, `task serve:pg` and `task migrate-check` made real. | #11, #22 |
| 2026-10-31 | Base UI: JTE layout + hello and error pages (full page / htmx fragment), Tailwind v4 standalone CLI (watch in `task serve`, minified in the Gradle build), vendored htmx 2.0.11 + Alpine CSP 3.17.5, `ctx.t("key")` i18n (hu default, cookie language switch), CSP/security-header filter, PWA manifest + icons + static-only service worker, guard tests (bundles, template literals, CSP-unsafe markup). | #13, #23 |
| 2026-10-31 | Identity: Google OIDC sign-in with a DB allowlist (`MM_ADMIN_EMAILS` + `allowed_email`), `app_user` keyed by Google `sub`, Spring Session JDBC (30 days, cleanup at sign-in), CSRF on, htmx 401 + `HX-Redirect`, fail-closed `prod` profile, login page, CSRF meta tags. | #12, #24 |
| 2026-10-31 | Decided D22 (agents merge planned PRs when checks are green) and D23 (one Google OAuth client); Phase 0 order changed to #16 → #15 → #14 → #17/#18 so agents can verify without the owner; owner console steps batched into one setup session. | — |
| 2026-11-01 | Hosting moved from Railway to Google Cloud Run + Neon (D11, D13, D23 amended): Railway sign-ups were closed. `Hosting-Setup.md` replaces `Railway-Setup.md` as the owner's one-session setup guide. | — |
| 2026-11-01 | Owner setup session done (Google Cloud project, billing + budget, OAuth client, Artifact Registry, service accounts, WIF, secrets, Neon, GitHub variables, auto-merge, local `gcloud` login). Real-Google sign-in checks passed; #12 closed. Cloud bootstrap script + skill filed as #27 (parked). | #12, #27 |
| 2026-11-01 | CI: `ci.yml` (Lint & test, Migrations, E2E placeholder + gated Trivy), `docs-check.yml` with `check_docs_touched.sh`, `wiki-sync.yml` with the page map and `sync-wiki.sh`, `dependabot.yml`. Scripts tested against throw-away repos; workflows pass actionlint. | #16, #29 |
| 2026-11-01 | Owner OK'd required checks: `Lint & test`, `Migrations (Postgres)`, `E2E`, `Docs updated` are now required on `main`. Wiki sync failed its first run (no wiki git repo yet); owner will create the first page later. | #16 |
| 2026-11-01 | `Dockerfile` (digest-pinned Temurin 25, uid 10001, JDK 25 AOT cache from a training run, bash `HEALTHCHECK`), `compose.e2e.yml` with `mock-oauth2-server` as the stub OIDC provider, `src/e2eTest` with one Playwright sign-in test at phone and desktop size, `task build` and `task test-e2e` made real, the E2E and Trivy steps in `ci.yml` made unconditional. | #15, #32 |
| 2026-11-01 | Guard tests: `EveryEndpointNeedsSignInTests` (calls every mapped endpoint anonymously) and `DocsToolingTests` (`CLAUDE.md` ≤ 100 lines, router paths, rule globs with a `NOT_YET` list that fails when stale); each also runs on a broken example. `task docs:check` made real. | #14, #33 |
| 2026-11-01 | `release.yml` written (manual, main only: version + CI-green check, version bump on a detached commit, image to Artifact Registry, `gcloud run deploy` through Workload Identity Federation, health poll, tag last). Not run yet; the v0.1.0 run is the rest of #18. | #17, #18, #34 |
| 2026-11-01 | First `release.yml` run (0.1.0) failed at the deploy step, before any tag: the env-var delimiter `@` clashed with the emails in `MM_ADMIN_EMAILS`; changed to `\|`. WIF login and the image push worked. | #18, #35 |
| 2026-11-01 | **v0.1.0 deployed** to Cloud Run (`europe-west3`, max 1, no minimum) against Neon: `release.yml` run 2 passed (run 1 failed on the `@` delimiter, PR #35). Health 200; Flyway applied V1–V3; first start 6.7 s. Sign-in on a phone and scale to zero still to confirm. | #18, #36 |
| 2026-11-01 | CI speed: BuildKit `type=gha` layer cache for the E2E image, Chromium and Tailwind caches, `task test-e2e:run`, Trivy scans the e2e image. E2E 3m19s → 2m16s warm (4m40s on the cold first run). | #37, #38 |
| 2026-11-01 | CI: the `E2E` job skips its image build, Playwright and Trivy steps on PRs that change only documentation (`scripts/e2e_needed.sh`); the job and its check name stay, so branch protection is untouched. | #37, #39 |
| 2026-11-01 | CD (#51, owner's request): `release.yml` runs after CI succeeds on `main`; `scripts/next_release.sh` picks the version and skips docs-only merges (tested by `ReleasePlanScriptTest`); manual "Run workflow" kept. D13 amended. Members (#43) merged as PR #50. | #43, #50, #51, #52 |
| 2026-11-01 | Members (#43): `MemberService` (list, remove, leave, hand over ownership; the household row is locked while the owner changes), `identity.UserDirectory` for names and addresses, the `/households/members` page with confirm dialogs, a link to it on `/households`. | #43, #50 |
| 2026-11-01 | Invites (#42): `invite` table (V5, hash only), `InviteService` (create, list, revoke, preview, join; single use by a guarded `UPDATE`), `identity.AllowlistEditor` so an invited address may sign in, the owner's invite section on `/households` and the `/join/<code>` page. v0.1.1 (households) released to Cloud Run the same day. | #18, #42, #49 |
| 2026-11-01 | Phase 1 started: sub-issues #41–#45 filed under epic #2. Households (#41): `household`/`membership` tables (V4), `HouseholdService` with the 404/403 rules, the session-based `CurrentHouseholdResolver` and `CurrentHousehold` controller parameter, the `/households` page (create, rename, switch), the current household in the header. Also confirmed with Cloud Monitoring that the Cloud Run service had scaled to zero ~15 min after the v0.1.0 deploy (Neon suspend still the owner's to confirm). | #18, #41, #46 |
| 2026-11-01 | Tenant guard (#44): `TenantIsolationTests` + `TenantEndpoints` registry + `TenantWorld` fixture (two households, four people); unregistered and stale registrations fail, and the decision logic is tested on leaky stubs. `tests.md` explains how to register an endpoint. | #44, #53 |
| 2026-11-01 | UI redesign planned: a design system agreed with the owner (storybook look: violet, gold, outlines, sparkles; Moonlight and Midnight themes; bottom tab bar on phone, sidebar on desktop) and written down as `plan-ui-redesign.md` with phases R0–R9. No code changed yet. | #55 |
| 2026-11-01 | Allowlist admin page (#45): `AllowlistAdminController`/`AllowlistAdminService` in `identity/internal`, `/admin/**` restricted to `ROLE_ADMIN`, bootstrap admins shown but not removable, `ViewContext.admin` for the header link. Phase 1 is complete. | #45, #56 |
| 2026-11-01 | Filed three issues at the owner's request: the **UI redesign** epic #57 (made next on the list, before Phase 2), the post-deploy smoke test #58 (runs once per release in `release.yml`, before the tag; owner agreed with the placement) and #59 (end live sessions when an address leaves the allowlist, child of #2). Docs only. | #57, #58, #59 |
| 2026-11-01 | App icons: a cartoon wizard favicon (`favicon.svg`, `favicon.ico`) and the PWA icon set replace the placeholder plate, drawn by `scripts/generate-icons.py` (replaces the ImageMagick script); the `<link>` for the SVG in the layout. Part of the UI redesign (R7), landed early. | #57, #61 |
| 2026-11-01 | Backups planned (#62, child of #7): nightly encrypted `pg_dump` to a Cloud Storage bucket in Frankfurt through GitHub Actions + WIF, tested restore into Neon, after Phase 5 and before Phase 7. Owner chose the wizard as the in-app brand mark too (UI-D11). Docs only. | #62 |
| 2026-11-01 | Planned the work for parallel subagents at the owner's request (D24): the redesign split into five waves with a file-ownership table (`plan-ui-redesign.md` §7), Phase 2 and 3 each split into a backend part that starts now (B2, B3; Flyway V6/V7 reserved) and pages that wait for R4, and a three-track schedule in §9. Docs only. | #57 |
| 2026-11-01 | Wave 1 started (D24): sub-issues #64 (R1), #65 (R2a, blocked by #64) and #66 (B2 catalog backend, under #3) filed; one worktree subagent each, plus #59. #59 landed: removing an address from the allowlist now ends that person's live sessions. | #57, #59, #64, #65, #66, #67 |
| 2026-11-01 | Catalog backend (#66, B2): the `catalog` module with Flyway V6, `MealService` and the `MealChanged` event, tenant-scoped, 100 % covered, no controllers or templates. Occasion types on a meal are not in V6 (open decision). Known gap: two people creating the same new tag at the same instant can get a constraint error on one save. | #3, #66, #68 |
| 2026-11-01 | Wave 3 started (D24): sub-issues #79 (R4 shell, under #57) and #80 (B3 planning backend, under #4) filed. **B3 landed** (#80, PR #81): the `planning` module with Flyway V7 `meal_slot`, `PlanningService`, the 409 conflict exception and `SlotPlanned`, 100 % covered, no controllers or templates. `meal_slot` has no `free_text` and `meal_id` is `NOT NULL` (§4.2 amended). | #4, #79, #80, #81 |
| 2026-11-02 | Wave 3 landed: the app shell (R4, #79, PR #82) with sign-out, the household switcher and the stored language, beside the planning backend (B3, #80, PR #81). Open questions for Wave 4 are in `plan-ui-redesign.md`'s log. | #57, #79, #82 |
| 2026-11-02 | **R4 follow-ups** (#83): the owner decided both open questions. The shell's top-bar avatar uses a new `soft` tone of `ui/_avatar` (`brand-soft` with `brand-ink`), so a phone screen has one gold mark, the current tab; and the language switch is now `POST /language` (CSRF-protected, public, redirects to the same-origin page it came from) instead of a `?lang=` GET, which replaces `LanguageSwitchInterceptor`. Wave 3's open items (1) and (3) are done. | #57, #83 |
| 2026-11-02 | Owner checks done: #16 (first wiki page created, docs sync) and #18 (sign-in on a phone works) closed. Owner confirmed UI-D8 (theme cookie) and the occasion-type decision (enum in `catalog`, V8). **Wave 4 started** (D24): #85 (R5a + R5c, one agent), #86 (R5b), #87 (R6) under #57, and #88 (Phase 2 meal pages) under #3, one worktree subagent each. Backups (#62) stay after Phase 5. | #16, #18, #57, #3, #85, #86, #87, #88 |
| 2026-11-02 | Wave 4 resumed after the 5-hour usage limit stopped the first session: #85 (PR #90) and #87 (PR #91) had merged, #86 was in PR #92 (checks green), #88 was a pushed WIP branch. Owner decided **D25**: plans by Opus (high) with a full `## Execution` brief per issue, a Haiku (high) coordinator, Haiku/Sonnet subagents chosen per issue, latest releases by alias. Docs only for the rule (`workflow.md`, `issues.md`, `CLAUDE.md`, `plan-ui-redesign.md` §7.1). | #85, #86, #87, #88, #90, #91, #92 |
| 2026-11-02 | **Wave 4 landed.** #85 (PR #90), #87 (PR #91) and #86 (PR #92) merged; the meal pages (#88, PR #95) finish Phase 2: the `/meals` list with search, tag filter, recently-used-first and an archived switch, the form with 422 and 409 states, quick add (phone sheet, desktop bar), archive and restore, `OccasionType` owned by `catalog` (V8), `RecentMealsProvider` implemented by `planning`. Checked at 390 and 1280 px in light and dark. Open for the owner: the phone Meals list shows the gold add button and the gold current tab together, which the spec itself prescribes (the "Add" row of its navigation table and the FAB row of its component table), so it is left for the R9 walk-through. Wave 5 (R5d, R8, then R9) and the Phase 3 week view are next. | #57, #3, #85, #86, #87, #88, #90, #91, #92, #95 |
| 2026-11-02 | **Wave 5 planned and half-built.** An Opus planner wrote the briefs; issues #96 (R5d), #97 (week view 1/2), #98 (week view 2/2) and #99 (R8) were filed with parents and blockers. **R5d landed** (#96, PR #100): the old `brand-50…800` scale is gone and `UiGuardTest` now fails on `brand-<digit>`, `stone-` or a raw Tailwind colour in a template or `app.js`, and on the old scale in `app.css`. **Week view 1/2 landed** (#97, PR #101): `GET /plan?week=YYYY-Www` (read-only), the Plan tab on `/plan`, `htmx.config.refreshOnHistoryMiss`. **Owner decisions:** the planner's five week-view deviations from the design are accepted (table from 1024 px, no swipe, one shared sheet, slot chips as row headers, remove-confirm over the slot sheet); the gold stays exactly as designed and is judged live at R9; the hello page may be removed once `/` can redirect (to be filed after #98). | #57, #4, #96, #97, #98, #99, #100, #101 |
| 2026-11-02 | **Week view 2/2 landed** (#98, PR #104): `PlanningService.slot` and `quickAddAndPlan`, eight routes under `/plan/slots`, the pick sheet (search, recent meals, quick add with a 422 state), the slot sheet (note, move, reorder, remove behind the confirm dialog) and the 409 conflict banner; tenant-guard registrations and `PlanEditingE2ETest` at 390 and 1280 px. **Phase 3 is done.** One find: `Htmx.redirect` to the page the browser is already on only jumps to the anchor, so the slot controller also sends `HX-Refresh` for the same page. **Hello page retired** (#103, PR #105): `/` now redirects to `/plan` (or `/households` for a person with no household), a household switch lands on `/plan`, and the hello controller, templates, message keys and test are gone; the slice tests that used `/` as a sample page use `/error`, `/meals` or `/households`, and the e2e sign-in helpers accept either landing page. R8 (#99) is next. | #4, #98, #103, #104 |
| 2026-11-02 | **Wave 5 landed.** R8 (#99, PR #106): `web-ui.md` and `Current-State-Frontend.md` reordered into one story, `Roadmap-Frontend.md`, `Newcomer-Guide.md` (new glossary entries and a request walk-through for `GET /plan`), `README.md`, `Home.md`, `References.md` (six rows) and the `CLAUDE.md` status brought up to date; `Setup.md`'s status line fixed afterwards. With the week view (#97, #98), R5d (#96) and the retired hello page (#103) the UI redesign is built; only R9, the owner's walk-through on a phone and a laptop, is left. Next: Phase 4. | #57, #99, #106 |
| 2026-11-02 | **Post-deploy smoke test landed** (#58, PR #108): `scripts/smoke.sh` and `task smoke -- <url>` check, anonymously, health, the login page, a protected path redirecting to sign-in, the security headers (HSTS only over https), the static assets and no 5xx; `release.yml` runs it after the health poll and before the tag push, so a failure leaves no tag. Tested against the built image with a bad case per check except HSTS, which first runs against the live https URL. | #58, #108 |
| 2026-11-02 | **Phase 4 planned** (D25): an Opus planner wrote five briefs. They were filed as #109–#113 under epic #5, with blockers: the backend slice #109 (V9) first; then #110, #111 and #112 side by side (merge order #111, #112, #110); then #113. Decided D26 (merge/replace, nothing owner-only); amended §4.2 (item tables, no note on occasion items). The owner confirmed the four defaults (nothing owner-only; replace empties the whole target; no occasion menu onto the plan; no occasion-template rename). Docs only. | #5, #109–#113 |
| 2026-11-02 | **Phase 4 backend landed** (Wave 6a, #109, PR #115, one `sonnet` high subagent): Flyway V9 with the six tables (composite foreign keys, so the database refuses a link across households), `PlanningService.copyDay`/`copyWeek` with `CopyMode` MERGE/REPLACE, `PlanTemplateService`, `OccasionService`, `OccasionTemplateService` and the shared `planning.internal.SlotWriter`; four new `TenantWorld` fixtures. No controllers or templates. Wave 6b (#110, #111, #112) is next. | #5, #109, #115 |
| 2026-11-02 | **Wave 6b started** (D24, D25): #110 (`sonnet` high), #111 and #112 (`haiku` high), one worktree subagent each. **Plan templates page landed** (#111, PR #116): `/plan/templates` (list, grouped by day and week), `/plan/templates/{id}` (the frozen items by day), rename with a 422 and a 409 state, and delete behind a confirm; four tenant-guard registrations. Covered by MockMvc tests only; not yet looked at in a browser. Known gap: after a 409 the rename form carries the current version, so sending it again without reloading overwrites the other person's rename (same as the household rename). The link to the page comes with #110. | #5, #110, #111, #112, #116 |
| 2026-11-02 | **Occasion pages landed** (#112, PR #117): `/occasions` (the next occasion, the rest of the upcoming ones, past ones), the create and edit form with 422 and 409 states, the detail page with a read-only menu, delete behind a confirm, and the **Occasions tab** in the bottom bar and the sidebar (`NavDestination.OCCASIONS`); seven tenant-guard registrations and `OccasionPagesE2ETest`. Covered by MockMvc tests and CI's E2E; not yet looked at on a phone. The occasion strip on the week is #110; menu editing and occasion templates are #113. | #5, #112, #117 |
| 2026-11-02 | **Plan page reuse landed** (#110, PR #118, one `sonnet` high subagent; Wave 6b is complete with #111 and #112): the week view gets a reuse sheet to copy a day or a week onto another date or week (merge, or replace behind a confirm), save it as a template or apply a template, a link to `/plan/templates`, and a strip of the week's occasions; six tenant-guard registrations and `PlanReuseE2ETest`. `PlanResponses` now holds the redirect helpers that `PlanSlotController` had. Not yet looked at on a phone; `PlanReuseE2ETest` writes screenshots to `build/e2e-screenshots/plan-reuse-*` for that. Wave 6c (#113) is next. | #5, #110, #118 |
| 2026-11-02 | **Occasion menus and the Phase 4 close-out landed** (#113, PR #119, one `sonnet` high subagent; Wave 6c, Phase 4 is complete): an occasion's menu can be edited on `/occasions/{id}` (a page-level pick sheet that copies the plan's, with library search and create-and-add; move up and down; remove behind a confirm), a menu can be saved as an occasion template, `/occasions/templates/{id}` shows a template and plans it again on a date or deletes it (the occasions made from it stay), and `/occasions` lists the templates. Nine new tenant-guard rows, `OccasionMenuE2ETest` and `PlanTemplatesE2ETest` (the first browser test of the #111 pages). The docs now say Phase 4 is built (README, `CLAUDE.md`, Current State, Roadmap, glossary, `plan-ui-redesign.md` amendments). The slot-sheet E2E flake on the phone viewport is still being investigated separately. Not yet checked on a real phone (the owner's R9 walk-through). | #5, #113, #119 |
| 2026-11-03 | **Phase 5 planned** (D25 says Opus; its budget was used up, so a Sonnet planner wrote the briefs). The owner confirmed the defaults and the security design, and filed as #124–#134 under epic #6 with blockers and board fields: ratings backend V10 (#124), sharing backend V11 (#125), PWA polish (#126), lunch first (#127), rating pages (#128), public share route (#129), Baloo 2 (#130), print view and link management (#131), meal history (#132), docs close-out (#133), the owner's checks and v1.0.0 (#134). Decided D27 (ratings), D28 (share links; per-IP limiting deferred to Phase 6; the share URL in Cloud Run's request log is accepted), D29 (print and release) and D30 (lunch first, design A with a desktop checkbox). Amended §4.1, §4.2, §4.4, §8 and §9. The slot-sheet E2E flake mentioned in the #113 row was fixed by #120 and #121 (PRs #122, #123). Docs only. | #6, #124–#134 |
| 2026-11-03 | **The owner chose Baloo 2 for headings** after comparing Fredoka + Nunito, Baloo 2, Quicksand, Varela Round, Comfortaa and Nunito 800 on a specimen page: Fredoka has no ő or ű (Google's own latin-ext file for it has 22 glyphs and none of them), and Baloo 2 covers them in two hosted files like Nunito. Filed as #130, which amends UI-D4 in `plan-ui-redesign.md` when it lands. Docs only. | #130, #71 |
| 2026-11-03 | **Phase 5 wave 7a landed** (D24, D25): four `sonnet` subagents in their own worktrees. Merged in this order: PWA polish #126 (PR #136, `base.jte`, `sw.js`, install hint), lunch first #127 (PR #137, D30, `DayPlan`/`WeekPage` and the week templates), ratings backend #124 (PR #139, Flyway V10, `feedback` module, `RatingService`, `RatingGiven`/`RatingWithdrawn`), sharing backend #125 (PR #138, Flyway V11, `share_link`, `SharedWeekReader`, `TokenCodes`). V10 merged and released before V11, as D27/D28 require. #124 replaces the duplicate-rating insert with an upsert and one retry, because a failed Postgres insert aborts the transaction. #125 has a known gap: the 10-link cap per household counts and then inserts without a lock, so two simultaneous creates can briefly exceed it (D28 says "at most 10"; the owner decides whether a lock is worth it). Docs only in this row. | #124, #125, #126, #127, #136, #137, #138, #139 |
| 2026-11-03 | **Phase 5 waves 7b and 7c landed** (D24, D25; `sonnet` and `haiku` subagents, one worktree each, merged by the coordinator after a rebase on `main` when `main` had moved). 7b: rating pages #128 (PR #142: the how-did-it-go prompt, the rating form on the plan's past slots, `RatingE2ETest`), Baloo 2 #130 (PR #141: heading font, latin and latin-ext woff2, `sw.js` cache v4; squashed to one commit on the rebase), public share route #129 (PR #143: `GET /share/{token}` as a single-segment GET matcher, one identical 404, the D28 headers, the global throttle, `robots.txt`; the rebase combined its `WebSliceTest` exclusions with #128's). 7c: print view and share-link management #131 (PR #145: `/plan/print`, a `ShareLinkE2ETest`; the share-link creator is shown in the list; the Print and Share links sit inside `#plan-week`), meal history #132 (PR #144: the history on the meal page, `MealHistoryPagesTest`). **Known gaps for the owner:** `RatingService.forMeal` applies its limit of 10 before it drops former members' ratings, so the history can show fewer than 10 (a fix belongs in #124's service); `share.limit` hard-codes "10" in its message; the share-link cap race from wave 7a (see above). Docs only in this row. | #128, #129, #130, #131, #132, #141, #142, #143, #144, #145 |
| 2026-11-03 | **Phase 5 close-out** (D24, D25): wave 7d. The docs close-out #133 (PR #147: README, `CLAUDE.md`, the Current State and Roadmap pages, the Newcomer Guide, References, the web UI rule and `plan-ui-redesign.md` amendments) is merged; a Javadoc fix the close-out found, that the sharing package still said its routes were "to come" (PR #148), is merged too. Phase 5 code and docs are done. Left for the owner (#134): Lighthouse and the PWA install on a phone, the share-link walk-through, the rating error state on a phone (the E2E screenshot is cropped below it), the Baloo 2 heading weight, and the two known gaps (the rating history limit, the share-link cap race). Then v1.0.0 (D29). Docs only. | #133, #134, #147, #148 |
| 2026-11-03 | **Owner's checks passed; the cap race is fixed** (#134 part 1). The owner did the phone checks and said everything looks good. The owner accepted the rating history rule as it stands: the limit of 10 is applied before former members' ratings are dropped, so the history can show fewer than 10 (no change to #124). The share-link cap race is fixed (PR #150): `HouseholdService.requireForUpdate` takes the household row lock that the ownership changes already use, so the count and the insert of a new link run one after the other; a concurrency test with 40 rounds fails without the lock. Still open: the `share.limit` message hard-codes "10" (cosmetic). Then v1.0.0, once the owner gives the go-ahead (D29). Docs only. | #134, #150 |
| 2026-11-03 | **v1.0.0 released** (D29, #134). The owner's go-ahead came after the phone checks. The first manual `release.yml` run (`version=1.0.0`) stopped at its CI gate, before any push or deploy: CI on the merge commit `491c7ef` was still running, and the gate needs `Lint & test`, `Migrations (Postgres)` and `E2E` green on the exact commit. The second run, after that CI passed, built the image, deployed it to Cloud Run, polled `/actuator/health`, smoke-tested the live service (all checks passed), and pushed tag `v1.0.0` and the GitHub release. Phase 5 is done. Next: backups (#62), then Phase 6 (hardening) and Phase 7 (the assistant). Docs only. | #134, #151 |
| 2026-11-03 | **Phase 6 re-ordered, with the backup and assistant designs written up** (owner's request). Order: backups (#62), the AI assistant (Phase 7), the repo audit, then GDPR basics, security hardening and the cost review. Each stage lists the owner's manual steps. Amended §3 (Phase 6, Phase 7), §4.1 (`assistant` also listens for `RatingWithdrawn`) and §9 item 10. `Roadmap-Everything-Else.md` follows the new order. Docs only. | #7, #62 |
| 2026-11-03 | **Backups and the assistant planned** (owner interview, then a Sonnet planner; the briefs are in the issues). *Backups* (#62, #154): retention daily 7 days and monthly ~190 days with a `release/` prefix (no weekly tier); session rows excluded; one `age` key; failures open a `Backup failed` issue; a Cloud Monitoring alert replaces the in-workflow freshness check; no recurring restore drill, a guide instead; an agent runs the `gcloud` setup script with the owner approving each step. *Ingredients* (#155, #156, D31): per household, names only, a ~150-name Hungarian starter list, a searchable type-to-add combobox, accent-folding keys, and a race-safe writer that also fixes the tag race. *Assistant* (#157 to #162, D32): Gemini on Vertex AI with no API key, Vertex embeddings, a lazily synced `meal_embedding` table, read-only tools and structured drafts, library meals plus ideas, week and slot suggestions, a food-and-plan chat, occasion help, a $5 monthly cap per household, no stored dietary rules. D19 amended; D31 to D33 added (D33, the model, is pending the eval); §4.1, §4.2, §6, §7, §8 and §9 item 10 amended. Open for the spike (#157): Gemini in an EU region, the embedding size, tool calling together with JSON output. Docs only. | #62, #154–#162 |
| 2026-11-03 | **Ingredients backend** (#155, PR #164): ingredients on meals with Flyway V12, a Hungarian seed list, accent-free keys, `ingredientSuggestions`, and the `NameWriter` that fixes the tag race (`ON CONFLICT DO NOTHING`). No pages yet (#156). | #155, #164 |
| 2026-11-03 | **Backup setup and restore** (#154, PR #165): the owner-approved GCP setup script, `task db:restore`, and the "Backup and Restore" guide. Nothing has run against Google Cloud yet. | #154, #165 |
| 2026-11-03 | **Assistant UI designed** (owner): `plan-ui-redesign.md` UI-D12 and Appendix D give #160 and #161 their look, states and copy. The assistant is "Varázsló / Wizard" in the UI, marked by the `wand` icon, never gold; ideas are dashed cards; the chat page is a plain-text thread. Phase 7's Surfaces item points there. Docs only. | #160, #161 |
| 2026-11-04 | **Assistant design brought into the plan** (the owner's design, PR #167). Phase 7's Surfaces item now carries the rules from Appendix D that change the backend (the 1 000-character limits, the quick-request hints, what Cancel does, the empty-week hint), and the owner's phone check in step 5 includes the chat page's two gold marks. The briefs of #159 to #161 were checked against Appendix D and amended: #160 now says where the quick-request hints are expanded (the controller, four known values only), adds the empty-week hint and a delay setting for the fake model; #159 widens `assistant_draft.request_text` to 1 200 characters (typed text 1 000 plus up to four hint phrases) and refuses longer requests; #161's brief had the `SPARKLES` nav icon and lacked `app.js` for the chat composer. One open design question for the owner: Appendix D.1 says every place a member types a request shows the allergy and Gemini notice, but the refine form (D.3) has neither. Docs only. | #159, #160, #161, #167 |
| 2026-11-04 | **Backups are no longer encrypted** (owner). The `age` layer is dropped from the design: the data is low-sensitivity, the bucket is private with a create-only writer, and the owner does not want a key to store or lose. Dumps are plain `*.dump` files; a restore needs only the owner's Google login. Amended §3 (6a: the Encryption, Restore and owner-steps items; 6d: the privacy note says the dumps are unencrypted) and §9 item 10. The setup list loses its first step (the key pair and `BACKUP_AGE_RECIPIENT`). Code and docs follow in PRs: the restore script, test and guide on `main`, and the workflow on #166. | #62, #154, #166 |
| 2026-11-04 | **The allergy and diet notice is dropped from the assistant's UI** (owner: "not needed"). It answers the open design question in the row above. `plan-ui-redesign.md` Appendix D.1, D.3, D.7 lose the `assistant.notice.allergy` alert and key and the second sentence of `assistant.chat.footnote`; the Gemini notice stays on the request step and the chat page, and the refine form (same sheet) does not repeat it. D32 and Phase 7's Privacy item are amended. The prompt rule that forbids claiming a meal is safe for an allergy stays (it is invisible). Issue #160 was updated to match (its Proposal line, its key list and its Amendment). Docs only. | #160, #161 |
| 2026-11-04 | **Backup setup postponed until after the assistant** (owner). Stage 6a now follows 6b. What is merged stays: the restore script and the guide (#154, PR #165, #169). The backup workflow (#62, PR #166) is rebased on `main`, green and open, and is **not merged**, because it makes every release wait for a backup that cannot succeed before the owner's setup (the Neon role and secret, the Google Cloud script, `BACKUP_BUCKET`). The step-by-step is in `documentation/Backup-and-Restore.md`. Risk accepted by the owner: until then only Neon's ~6-hour restore window protects the data, and the assistant's migrations (V13 to V15) deploy without a pre-release backup. Amended the Status line, §3 (Phase 6 order, 6b, Phase 7) and §9 item 10. Docs only. | #62, #154, #166 |
| 2026-11-04 | **Assistant foundation** (#157, Part A and the live spike). The owner enabled the Vertex AI API and granted `mm-runtime` `roles/aiplatform.user`. The module `assistant` has the on/off switch (`mm.ai.enabled=false`, Spring AI's auto-configuration is off, so no credentials are needed to start), the spend cap (`UsageLedger`, Flyway V13) and `ModelGateway` (the only class that calls the model). The spike made real calls: Spring AI 2.0.2 works with Boot 4.1.2; tool calling and JSON output work together; the usage fields are read. The startup cost with the assistant off is about 1.3 s on the plain JVM (owner accepted it). D32 amended with the chosen region and prices. | #157 |
