# Newcomer Guide

For a junior developer joining Meal Magician. **Status:** covers what exists today (households, the meal
library, the week planner, ratings, share links and the print view; Phases 0 to 5). The build works; run `./gradlew build`.

## What the app does

A household plans meals per day (breakfast, lunch, dinner, snack) and for special occasions,
reuses meals, days and weeks, and rates what it ate. It can also send one week of its plan to someone
outside the household as a read-only link that needs no sign-in. Everyone signs in with Google; only
allowlisted emails get in; households are shared through email-bound invite links.

## Tools and why

| Tool | Why |
|---|---|
| Java 25 + Spring Boot 4 | LTS Java, batteries-included web, security and data |
| Spring Modulith | keeps the monolith split into modules with checked boundaries |
| JTE + htmx + Alpine + Tailwind | server-rendered, type-safe templates; SPA-like feel without a JS build. htmx swaps server-rendered fragments into the page; Alpine (its CSP build) only holds tiny local state such as an open menu; Tailwind's standalone CLI builds the CSS, so no Node is needed |
| Design tokens + `ui/` components | one look in two themes: pages use token classes such as `bg-surface` and `text-ink`, and the shared fragments live in `src/main/jte/ui/`. The style guide `/dev/ui` (in `task serve`) shows every one of them |
| Content-Security-Policy | a response header that makes the browser refuse inline scripts and foreign origins. It is why templates have no `onclick=`, `<script>` blocks or `style=` and why the JS libraries are copied into `static/js/` instead of loaded from a CDN. A test checks the templates |
| Service worker + manifest | what lets a phone install the app to the home screen. The worker only caches CSS/JS/icons, never pages |
| PostgreSQL + pgvector, Flyway | one DB for app data and later AI embeddings; versioned schema |
| H2 | fast local runs and tests without Docker |
| Gradle + Taskfile | build, and one command surface for people, agents and CI: type `task --list` to see every command, `task doctor` to check your machine. The `gradlew` wrapper pins the Gradle version; a *toolchain* downloads Java 25 itself, so your machine's JDK doesn't matter |
| GitHub Actions | CI: on every PR it runs the same `task` commands you run locally (lint, tests with the 100 % gate, migrations, docs-updated check), so you can reproduce any red check on your machine. After a merge it also copies `documentation/` into the wiki |
| Spotless, Checkstyle, JaCoCo | the quality gates: formatting, "every public method has Javadoc", and 100 % test coverage. `./gradlew build` runs all three |
| Lombok | removes getter/builder boilerplate from JPA entities; its output is marked so coverage ignores it |
| Cloud Run + Neon | hosting with no server to manage: the app (Cloud Run) and the database (Neon) both scale to zero when idle |

## Glossary

- **Household:** the shared workspace; members are OWNER or MEMBER. It always has an owner: the
  last one cannot leave, they hand the household over first.
- **Meal:** a reusable library entry (name, tags, notes, recipe link).
- **Slot:** one planned meal on a date for a slot type (breakfast, lunch, …).
- **Template:** a saved day or week of the plan (`plan_template`), applied with merge or replace.
- **Merge / replace:** the two ways to copy or apply a day or week onto a target that already has
  meals. Merge adds the copy after what is there; replace empties the target first and always asks
  for a confirm (PLAN.md D26).
- **Occasion:** a dated event (birthday, Christmas) with its own menu of library meals, which
  members add, reorder and remove.
- **Occasion template:** a saved occasion menu (with its kind and notes), used to plan it again on
  a new date, for example next Christmas.
- **Rating:** a member's answer about a meal that was planned on a past or current day: a required
  thumb (up or down), optional stars (1 to 5) and an optional comment of up to 500 characters. Each
  member has one rating per slot, so giving again changes it. The ratings are the history the later
  AI assistant will learn from (PLAN.md D27).
- **Snooze ("Not now"):** pressing "Not now" on the "How did it go?" card hides it for the rest of
  the day. The only thing stored is a cookie with a date (`mm_rating_snooze`); the server keeps nothing.
- **Share link:** a read-only link to one ISO week of the plan, valid for 1, 7 (the default) or 30
  days, that opens without signing in. Only the SHA-256 hash of its token is stored, and the full
  link is shown once, when it is made. A household may have 10 active links at a time (PLAN.md D28).
- **Print page:** `/plan/print`, the week laid out for paper: names, notes and occasions. Its Print
  and Back buttons are hidden when printing (`print:hidden`). Members only, because the notes are
  private; the public share page has no notes.
- **Unchecked reader:** a service method that reads household data without checking who is asking.
  The rule is that every public method checks membership, so this is allowed only for the public
  share link, where nobody is signed in. It returns a narrow record type and an ArchUnit test
  (`UncheckedReadersTest`) fails if another module uses it. The two are `SharedWeekReader` and
  `MealNameLookup`.
- **Allowlist:** emails allowed to sign in at all; invites add to it.
- **Invite:** an owner's single-use, expiring `/join/<code>` link for one e-mail address; only the
  code's hash is stored.
- **Design token:** a named colour or shape value, a `--mm-*` CSS variable in `app.css` mapped to a
  class such as `bg-surface`. Pages use the token and never a raw palette colour, so both themes
  stay consistent.
- **Component (`ui/`):** a reusable JTE fragment in `src/main/jte/ui/`, such as a button, a card or a
  sheet. The caller passes the text in, so a component has no translations of its own.
- **App shell:** the frame around every signed-in page (`layout/_shell.jte`): the phone's top bar and
  tab bar, or the desktop sidebar.
- **Theme:** the colour mode, Moonlight (light), Midnight (dark) or system (follow the device), kept in
  the `mm_theme` cookie.
- **ISO week:** the week the planner shows, Monday to Sunday, written `2026-W45` in the URL.

## One request through the layers

The plan is in `PLAN.md` §4.3. What exists today, for the week plan (`GET /plan`), which is where
signing in lands: the address `/` shows nothing itself and `household.internal.HomeController`
redirects there (or to `/households` for a person who has no household yet). Here is one request,
`GET /plan?week=2026-W45`:

1. The browser asks for `/plan?week=2026-W45`. `SecurityHeadersFilter` adds the security headers,
   then the Spring Security chain checks the session; an anonymous visitor is redirected to `/login`.
2. The `mm_lang` cookie (default Hungarian) sets the locale, read by `I18nConfig`. The cookie is set
   by `LanguageController`, which answers `POST /language` from the language buttons.
3. `PlanController` (in `planning.internal`) declares a `CurrentHousehold` parameter.
   `CurrentHouseholdArgumentResolver` fills it, and `CurrentHouseholdResolver` checks the membership
   on every request, so a removed member loses access at once. A person with no household gets a 404.
4. `PlanController` parses the `week` value into an `IsoWeek` (a missing or invalid one means this
   week) and asks `WeekPageFactory` for the page model. That is one `PlanningService.week` call and
   one `MealService.search`, which gives the names of all the planned meals.
5. `ViewContextAdvice` has already put the `ctx` object (translations, CSRF token, the shell's
   household and person, the current tab) into the model.
6. `Htmx.view(...)` picks the full page `plan/week`, or the fragment `plan/_week` when htmx sent the
   request (header `HX-Request`). That is how the week stepper swaps only the week, when the previous
   or next arrow asks for `/plan?week=…` again.
7. The JTE template calls `ctx.t("plan.week.title", …)` and wraps itself in `layout/base.jte`, which
   draws the app shell for a signed-in person: top bar and tab bar on a phone, sidebar on a desktop.
   The page loads `/css/app.css`, htmx and Alpine from our own server.

A change takes the same road. `POST /plan/slots/{id}/note` (`PlanSlotController`) saves a note with
the `version` the editor showed, which is the slot's `@Version` column. Success ends with
`Htmx.redirect` back to the week. A stale `version` (another tab changed the slot first) answers 409
with nothing written, and htmx retargets the warning into `#plan-conflict`, so the second save never
overwrites the first.

Errors take a similar path: an unknown URL ends up in `AppErrorController`, which renders the
404 page (or a fragment for htmx).

### A request with no sign-in: `GET /share/{token}`

The share link is the one business page that needs no session, so it is the place to see how the
rules bend and where they still hold. Here is one visitor opening a link, `GET /share/<token>`:

1. `SecurityHeadersFilter` adds the security headers. Spring Security lets the request through
   because `SecurityConfig.PUBLIC_GET_PATHS` matches exactly one path segment of token characters,
   for GET only. `/share/x/y`, `/share` or a POST still go to the sign-in page.
2. `PublicShareController` (in `sharing.internal`) sets its privacy headers first, on every answer:
   no caching, no referrer and no indexing. Then the one global throttle, `RateLimiter`, is asked
   for a slot (120 requests a minute for all visitors together). Over the limit the answer is 429
   with `Retry-After`, before any database work.
3. The token is checked for shape with `TokenCodes.isWellFormed` (43 base64url characters). A
   malformed token is a 404 here, without a database call.
4. `ShareLinkService.open(token)` hashes the token with SHA-256 (`TokenCodes.hash`). The `share_link`
   table holds only that hash, so the lookup compares hashes and the token itself is never stored.
5. The query `findActiveByTokenHash` returns the link only while `revoked_at` is empty and
   `expires_at` is later than now. An unknown, expired or revoked token gives nothing, and the
   controller answers all of those with the same 404 page, so a guesser learns nothing about which
   tokens exist.
6. For an active link, `SharedWeekReader.read(householdId, weekStart)` (the unchecked reader) loads
   the seven days from the link's Monday, with the meal names and the occasions. It does not check
   membership, because nobody is signed in here. Its result, `SharedWeek`, has no ids, notes or
   member names, so the page cannot show them even by mistake.
7. The controller puts a `SharedWeekPage` in the model and returns the template `sharing/week`,
   drawn on `layout/bare.jte`: no shell, no navigation, no form and no CSRF token. Nothing is
   written and no session is created, so the visit leaves no trace in the database.

## Your first change

Run `task doctor` once to check your tools and `task install` to fetch dependencies (and the
Tailwind CLI, which every build needs). `task serve` then shows the app on
<http://localhost:8080> and rebuilds the CSS when you edit a template. Then pick an
issue, branch `feat/<issue>-…`, write tests first, run `task format`, `task lint` and `task test`
(`task test:fast` is the quick loop while you work), and open a PR with the template. Details: `.claude/context/workflow.md`.

Before you write a fragment, look at `/dev/ui`: every component is there in every state, so reuse one
rather than copy markup. A new household-scoped route must be registered in `TenantEndpoints` (see
`.claude/rules/tests.md`); `TenantIsolationTests` fails until you do.
