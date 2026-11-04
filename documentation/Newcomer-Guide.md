# Newcomer Guide

For a junior developer joining Meal Magician. **Status:** covers what exists today (households, the meal
library, the week planner, ratings, share links and the print view, and the optional AI assistant;
Phases 0 to 5 and 7). The build works; run `./gradlew build`.

## What the app does

A household plans meals per day (breakfast, lunch, dinner, snack) and for special occasions,
reuses meals, days and weeks, and rates what it ate. It can also send one week of its plan to someone
outside the household as a read-only link that needs no sign-in. An optional AI assistant, the
"Varázsló / Wizard" (off by default, see [[AI Assistant]]), can draft a week from the household's own
meals. Everyone signs in with Google; only
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
| PostgreSQL + pgvector, Flyway | one DB for app data and the AI meal-search vectors (`meal_embedding`); versioned schema |
| Spring AI + Gemini on Vertex AI | the optional assistant (module `assistant`). Spring AI is the Java library that talks to the model; Gemini is Google's model; Vertex AI is the Google Cloud service it runs on. The app calls it with the Cloud Run service account, so there is no API key. Only `ModelGateway` makes the calls |
| Testcontainers | starts a throw-away Postgres in Docker for the integration tests and for the assistant eval (`task ai:eval`) |
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
- **Ingredient:** a name on a meal, such as "tojás", with no amount. Each household has its own list,
  which starts from a built-in list of common Hungarian ingredients; "Tojás" and "tojas" are the same
  ingredient.
- **Combobox:** a text field with a list under it that narrows as you type, here the ingredient picker
  on the meal form (`ui/_combobox`). You pick a suggestion or type a new name and tap the "Add" row;
  what you picked becomes a removable chip. It is built on the WAI-ARIA combobox pattern, so it works
  with a keyboard and a screen reader.
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
  member has one rating per slot, so giving again changes it. The ratings are the history the AI
  assistant reads to learn what the household likes (PLAN.md D27).
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
- **Draft:** what the assistant ("Varázsló" / Wizard) proposes for a week or an occasion: a saved list
  of items per day and slot type that is *not* in the plan yet. The member can remove items, ask for
  changes, and then Accept (the library meals are written to the plan) or Discard. See `assistant/`.
- **Chat:** the Wizard's question-and-answer page (`/assistant`, #161). The conversation lives in the
  session only: it is gone after "New conversation", a sign-out or an expired session. The wizard
  answers about food, ingredients, the season in Hungary, menus and the household's own history, and
  politely declines anything else (the topic policy in `assistant/chat-policy-food.txt`).
- **Menu suggestion:** on an occasion's page, "Menüjavaslat" asks the Wizard for up to six library
  meals and three new ideas, based on the occasion's kind and the menus of earlier occasions of that
  kind. Nothing is added by itself: a library meal goes on the menu with its own button, and an idea
  goes into the library first.
- **Idea:** an item in a draft that is not a meal of the household's library yet (a name, a reason and
  some ingredients the model made up). It is drawn as a dashed card; "Add to meals" puts it in the
  library, and an accepted draft leaves out the ideas that were not added.
- **Chip:** a small rounded label or button. In the assistant, the quick-request chips ("less meat",
  "something quick") on the request form and the starter chips on an empty chat each add a ready-made
  phrase or send a ready-made message; in the meal form a chip is a removable tag or ingredient.
- **Spend cap:** the most a household may spend on the assistant in a calendar month ($5 by default,
  `mm.ai.monthly-cap-usd`). `ModelGateway` checks it before every call and `UsageLedger` adds the tokens
  and cost after it, so over the cap a member gets a message and no call is made. Vertex AI itself has no
  hard cap, so this check is the control.
- **Token:** a piece of a word; models are billed per million tokens, with output (including the
  model's "thinking") dearer than input.
- **Eval:** a fixed set of requests (`src/test/resources/eval/`) run against candidate models, with
  deterministic checks (real meal ids, no repeats, "less meat" respected, ...) instead of a person or
  another model judging. `task ai:eval` runs it by hand because it costs money and varies from run to
  run. The owner reads the report and picks the model (PLAN.md D33). The checks themselves are unit
  tested without any model.
- **Quick-request chip:** one of four ticks (less meat, quick meals, more vegetable stews, leftovers)
  next to the request box. The browser sends only its key; the server turns it into text in the
  member's language.
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

### How the assistant gets onto the plan page

The plan page belongs to `planning`, and `planning` may not depend on `assistant` (Spring Modulith
would fail the build). So the plan templates hold only empty placeholders, for example in
`plan/_week.jte`: `<div id="plan-assistant-entry" hx-get="/assistant/entry?week=…" hx-trigger="load">`.
When the page has loaded, htmx asks the assistant module for that fragment:

1. `GET /assistant/entry` first meets `AssistantGate`, an interceptor. If `mm.ai.enabled` is false
   (the default) it answers 204 with no body and the placeholder stays empty: the plan page looks as
   if the assistant did not exist. Every other `/assistant/**` route answers 404.
2. If it is on, `AssistantController.entry` reads the week through `PlanningService` (is anything
   planned?) and returns `assistant/_entry.jte`: the "Suggest my week" button, a hint line for an empty
   week and the assistant's own sheet. The fragment replaces the placeholder.
3. The button loads the request form into the sheet. Sending it posts to `/assistant/drafts`. The
   model call takes seconds, so the form shows a waiting state by CSS while htmx's request runs.
4. The draft comes back as a fragment, and nothing is written to the plan. Only Accept
   (`POST /assistant/drafts/{id}/accept`) calls the planning module, then redirects to the week, where
   a third placeholder, `#assistant-notice`, loads the "x meals added" note.

In tests the model is `ScriptedChatModel` (profile `fake-ai`), so no test calls Google.

### Asking the wizard: the chat (#161)

The chat is a plain page with htmx in front of it. Take one message, "Mi van most szezonban?" ("What is in season now?"):

1. `POST /assistant/chat` meets `AssistantGate` first, then `CurrentHousehold` (a person in no household
   gets 404). A blank message is answered with 204, so nothing happens.
2. `ChatController.send` reads the conversation from the session (`ChatSession`, at most 20 turns) and
   calls `AssistantService.chat`. `ChatService` checks the length (over 1 000 characters is a 422 and
   no model call), builds the prompt (the system prompt, the topic policy, the stored turns, the new
   message as a quoted string) and calls the one `ModelGateway`, which checks the monthly cap first.
3. The answer is stripped and returned. Only now does the controller add both turns to the session. A
   failed call leaves the session as it was, and the text stays in the field.
4. The reply is `assistant/_chat_reply.jte`: the new bubbles for the thread, a hidden empty state, and
   the composer again (cleared), all in one response. htmx appends and swaps as the attributes say.

In tests the model is `ScriptedChatModel`; `AssistantChatPagesTest` carries the session cookie from
request to request, the way a browser does.

### Judging a model: the eval (#162)

Tests never call Google, so they cannot tell whether a model is *good* at planning. The eval can. It is
a plain `main` in the test sources, `assistant/eval/EvalRunner`, started by `task ai:eval -- --models
<id,id>`. Follow one run:

1. `EvalOptions` parses the command line. No `--models` means exit code 2 and the usage text; `--help`
   works with no Google login and no Docker. Then the runner checks that Docker and the Google login
   exist and stops with a plain sentence if not.
2. For each model, `EvalHarness` starts the real application on a throw-away Postgres (the
   Testcontainers one from `AbstractPostgresIntegrationTest`) with that model, and builds an invented
   household from `household.json` through the real services (`MealService`, `PlanningService`,
   `RatingService`). A moving clock makes "today" the case's date.
3. Each case from `cases.json` is run `--runs` times (default 3, because answers vary) through
   `AssistantService`, exactly as a page would call it. Tokens and cost are the difference in the
   `assistant_usage` row, so they come from the ledger.
4. `Checks` judges every answer with plain Java: is each meal id real, is any meal on two days in a
   row, is the summary in the right language, and so on. `ChecksTest` feeds each check one good and one
   bad answer, so the judge is tested without a model.
5. `Report` prints a table and writes `build/eval/<timestamp>.md`. The owner reads it and decides.

`EvalHarnessTest` runs steps 2 to 5 with the scripted model, so the harness itself is tested without
spending anything. More in [[AI Assistant]].

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
