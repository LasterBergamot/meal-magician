# Newcomer Guide

For a junior developer joining Meal Magician. **Status:** skeleton — it fills in as Phase 0 lands.

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
| Gradle + Taskfile | build, and one command surface for people, agents and CI |
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

Pick an issue, branch `feat/<issue>-…`, write tests first, `task lint && task test`, open a PR
with the template. Details: `.claude/context/workflow.md`.
