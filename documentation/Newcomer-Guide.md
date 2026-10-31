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
| JTE + htmx + Alpine + Tailwind | server-rendered, type-safe templates; SPA-like feel without a JS build |
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

See `PLAN.md` §4.3; this section will walk through real code once Phase 0 exists.

## Your first change

Run `task doctor` once to check your tools and `task install` to fetch dependencies. Then pick an
issue, branch `feat/<issue>-…`, write tests first, run `task format`, `task lint` and `task test`
(`task test:fast` is the quick loop while you work), and open a PR with the template. Details: `.claude/context/workflow.md`.
