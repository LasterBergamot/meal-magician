# Newcomer Guide

For a junior developer joining Meal Magician. **Status:** skeleton — it fills in as Phase 0 lands. The build works today; run `./gradlew build`.

## What the app does

A household plans meals per day (breakfast, lunch, dinner, snack) and for special occasions,
reuses meals, days and weeks, and rates what it ate. Everyone signs in with Google; only
allowlisted emails get in; households are shared through email-bound invite links.

## Tools and why

| Tool | Why |
|---|---|
| Java 25 + Spring Boot 4 | LTS Java, batteries-included web, security and data |
| Spring Modulith | keeps the monolith split into modules with checked boundaries |
| JTE + htmx + Alpine + Tailwind | server-rendered, type-safe templates; SPA-like feel without a JS build. htmx swaps server-rendered fragments into the page; Alpine (its CSP build) only holds tiny local state such as an open menu; Tailwind's standalone CLI builds the CSS, so no Node is needed |
| Content-Security-Policy | a response header that makes the browser refuse inline scripts and foreign origins. It is why templates have no `onclick=`, `<script>` blocks or `style=` and why the JS libraries are copied into `static/js/` instead of loaded from a CDN. A test checks the templates |
| Service worker + manifest | what lets a phone install the app to the home screen. The worker only caches CSS/JS/icons, never pages |
| PostgreSQL + pgvector, Flyway | one DB for app data and later AI embeddings; versioned schema |
| H2 | fast local runs and tests without Docker |
| Gradle + Taskfile | build, and one command surface for people, agents and CI: type `task --list` to see every command, `task doctor` to check your machine. The `gradlew` wrapper pins the Gradle version; a *toolchain* downloads Java 25 itself, so your machine's JDK doesn't matter |
| Spotless, Checkstyle, JaCoCo | the quality gates: formatting, "every public method has Javadoc", and 100 % test coverage. `./gradlew build` runs all three |
| Lombok | removes getter/builder boilerplate from JPA entities (later); its output is marked so coverage ignores it |
| Railway | hosting with no server to manage; app sleeps when idle |

## Glossary

- **Household:** the shared workspace; members are OWNER or MEMBER.
- **Meal:** a reusable library entry (name, tags, notes, recipe link).
- **Slot:** one planned meal on a date for a slot type (breakfast, lunch, …).
- **Template:** a saved day or week of slots that can be applied to new dates.
- **Occasion:** a dated event (birthday, Christmas) with its own menu.
- **Allowlist:** emails allowed to sign in at all; invites add to it.

## One request through the layers

The plan is in `PLAN.md` §4.3. What exists today, for the hello page (`GET /`):

1. The browser asks for `/`. `SecurityHeadersFilter` adds the security headers to the response,
   then the Spring Security chain checks the session; an anonymous visitor is redirected to `/login`.
2. `LanguageSwitchInterceptor` looks for `?lang=`; otherwise the `mm_lang` cookie (default
   Hungarian) decides the language.
3. `HelloController` (in `shared.web`) builds a small view record and picks a view with
   `Htmx.view(...)`: the full page `hello/page`, or the fragment `hello/_greeting` when htmx
   sent the request (header `HX-Request`).
4. `ViewContextAdvice` has already put the `ctx` object (translations, CSRF token) into the
   model; the JTE template calls `ctx.t("hello.title")` and wraps itself in `layout/base.jte`.
5. The page loads `/css/app.css`, htmx and Alpine from our own server. Clicking "Refresh" makes
   htmx ask `/` again and swap just the fragment in. Add the module code, security and database
   steps from the plan as they land in #12 and Phase 1.

Errors take a similar path: an unknown URL ends up in `AppErrorController`, which renders the
404 page (or a fragment for htmx).

## Your first change

Run `task doctor` once to check your tools and `task install` to fetch dependencies (and the
Tailwind CLI, which every build needs). `task serve` then shows the app on
<http://localhost:8080> and rebuilds the CSS when you edit a template. Then pick an
issue, branch `feat/<issue>-…`, write tests first, run `task format`, `task lint` and `task test`
(`task test:fast` is the quick loop while you work), and open a PR with the template. Details: `.claude/context/workflow.md`.
