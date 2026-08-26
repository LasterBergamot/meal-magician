# Meal Magician — Whole-Application Development Plan

## Context

`meal-magician` is a greenfield project (empty repo, branch `feature/1-add-backend`, remote `LasterBergamot/meal-magician`). The goal is a **meal-planning app, web-first then mobile**, where a household plans **lunches for a week**, and previously-entered **meals, days, and weeks can be saved and recalled for reuse**. Recipes and ingredients are explicitly **out of scope now but must be easy to add later**. Households have an admin (creator) who can promote other members to admin; all meals/days/weeks belong to a household. A member is in **one household for now, but the model must extend to multiple**. Later phases add an **AI agent** for history-based and seasonal recommendations.

The whole system is designed around one principle: **simple now, extensible later** — every "later" feature (recipes, multi-household, more meal slots, AI) slots in additively without a rewrite.

## Decisions (confirmed with user)

| Area | Decision |
|---|---|
| Frontend | React + TypeScript **monorepo**: Next.js web now, Expo/React Native mobile later |
| Monorepo tooling | **pnpm workspaces + Turborepo** |
| Backend | **Supabase only** (Postgres + Auth + RLS + PostgREST + Edge Functions). No custom Python/Java API. |
| Hosting | **Vercel** (web) + **managed Supabase Cloud** (backend) |
| Dev environment | **WSL2 (Linux distro) on Windows 11** — develop on the same OS family the app runs on in production, avoiding Windows-only surprises |
| Containerization | **Podman + Podman Compose** — the web app ships as a Next.js **standalone** image; local dev and CI build the same image |
| Auth | **Magic link + Google OAuth + Apple sign-in** |
| Meal slots | **Lunch in UI now**, DB has a `meal_slot` enum so breakfast/dinner/snack are a config change |
| Reuse model | Meals **auto-collect into a household library**; days/weeks are **explicitly saved as templates** and applied to the calendar |

## Repository structure (monorepo)

```
meal-magician/
├─ apps/
│  ├─ web/            Next.js (App Router) + Tailwind + shadcn/ui
│  └─ mobile/         Expo / React Native            (LATER — Phase 6)
├─ packages/
│  └─ core/           Shared TS: DB types, Supabase client factory,
│                     data-access functions/hooks, Zod schemas, constants
├─ supabase/          Backend-as-code: migrations, RLS policies, RPC
│                     functions, Edge Functions, seed.sql
├─ .github/workflows/ CI (lint, typecheck, build, container image)
├─ compose.yaml       Podman Compose: build + run the app container(s)
│  apps/web/Containerfile   Multi-stage Next.js standalone image
├─ turbo.json, pnpm-workspace.yaml, package.json, tsconfig.base.json
├─ .gitignore, .gitattributes, .dockerignore, .env.example
```

**Key idea:** the running backend lives in Supabase Cloud, but its schema/policies/functions are version-controlled in `supabase/` and pushed via the Supabase CLI. `packages/core` holds the data layer written **once** and imported by both web and (later) mobile. **Share logic, not UI** initially — each platform keeps its own UI (web = shadcn/ui; mobile = RN components). Cross-platform UI (Tamagui/NativeWind) is a possible later optimization, not a starting constraint.

## Data model (Postgres, in `supabase/migrations/`)

Enums: `meal_slot` = `breakfast | lunch | dinner | snack` (default `lunch`); `member_role` = `admin | member`.

| Table | Purpose / key columns |
|---|---|
| `profiles` | 1:1 with `auth.users` (display_name, avatar_url). Populated by `handle_new_user` trigger. |
| `households` | `id, name, created_by, created_at, updated_at` |
| `household_members` | Join table: `household_id, user_id, role`. **No unique on `user_id`** — single-household enforced at app layer so multi-household is a later config change, not a migration. |
| `household_invites` | `household_id, email/token, role, invited_by, status, expires_at` — join flow. |
| `meals` | Reusable **library** per household: `household_id, name, description, notes, created_by`. Future: `recipe_id` FK (additive). |
| `day_templates` + `day_template_items` | Named reusable day (`slot, meal_id, position`). |
| `week_templates` + `week_template_items` | Named reusable 7-day plan (`day_of_week 1-7, slot, meal_id, position`). |
| `planned_meals` | The **live calendar**: `household_id, date, slot, meal_id, position`. A "week" is a date range over this table, not a stored row — keeps planning flexible. |

**RPC functions** (transactional, called via PostgREST `rpc()`):
- `create_household(name)` → creates household + inserts creator as `admin`.
- `apply_week_template(week_template_id, start_date)` / `apply_day_template(day_template_id, date)` → copy template items into `planned_meals`.
- `save_week_as_template(start_date, name)` / `save_day_as_template(date, name)` → read `planned_meals` range → create template + items.
- `accept_invite(token)` → add member (guards single-household rule here, relaxable later).

## Security (Row-Level Security)

- Enable RLS on **every** household-scoped table.
- Helper: `is_household_member(hid)` and `is_household_admin(hid)` as **`SECURITY DEFINER`** functions to check `household_members` — this **avoids the classic RLS-recursion pitfall** where a policy on `household_members` queries `household_members`.
- Read/write policies: row visible/editable if `auth.uid()` is a member of the row's `household_id`. Admin-only actions (rename household, manage members, delete) additionally require `is_household_admin`.
- `anon` key is safe in the browser because RLS enforces access. `service_role` key used **only** server-side (Edge Functions / Next server), never shipped to the client.

## Web app architecture (`apps/web`, Next.js App Router)

- **Auth session:** `@supabase/ssr` (cookie-based) so server components and middleware see the session; client uses the Supabase JS client.
- **Data:** TanStack Query for caching/mutations, wrapping the shared functions in `packages/core`. Optional Supabase **Realtime** subscription on `planned_meals` so household members see live updates.
- **UI:** Tailwind + shadcn/ui. Weekly planner = Mon–Sun grid, one lunch slot per day, add meal via searchable library autocomplete or quick-create; prev/next week navigation.
- **Forms/validation:** react-hook-form + **Zod schemas from `packages/core`** (reused client-side and, where useful, in Edge Functions).

## `packages/core` contents

- Generated DB types (`supabase gen types typescript`).
- Typed Supabase client factory (browser + server variants).
- Data-access functions/hooks: `getWeekPlan`, `upsertPlannedMeal`, `listMeals`, `createMeal`, template list/apply/save (via `rpc`), household + member management.
- Zod schemas, domain types, shared constants (slots, roles).

## Development environment & containerization

**Why:** the app runs on Linux in production (Vercel and/or a Podman host), so development happens on Linux too — via **WSL2 on Windows 11**. This keeps native modules (e.g. `sharp`), file-system casing, line endings, and shell tooling identical between dev, CI, and prod.

- **WSL2 + a Linux distro** (e.g. Ubuntu) is the dev shell. Node/pnpm and the Supabase CLI are installed *inside* the distro. For best file-watch performance the repo should live on the Linux filesystem (`~/…`), not `/mnt/c/…`. `.gitattributes` pins **LF** line endings so a Windows checkout still behaves in Linux containers.
- **Podman + Podman Compose** run the app containers. The web app builds to a Next.js **standalone** bundle (`output: "standalone"` with `outputFileTracingRoot` at the monorepo root) and is packaged by `apps/web/Containerfile` — a multi-stage build (pnpm deps → build → minimal non-root runner). `compose.yaml` at the root builds and runs it; the file is compatible with `docker compose` too.
- **Backend engine:** the Supabase CLI's local stack (Postgres, Auth, PostgREST, Studio…) is itself a set of containers. It runs on **Podman** by pointing the CLI at the Podman socket (`DOCKER_HOST`) — no Docker Desktop required. Production uses managed Supabase Cloud regardless.
- **Env vars & containers:** `NEXT_PUBLIC_*` are compiled into the browser bundle at **build time** (passed as `--build-arg`); `SUPABASE_SERVICE_ROLE_KEY` is a **run-time**, server-only secret. The container reaches a host-run Supabase stack via `host.containers.internal`.
- **Two dev loops:** fast inner loop = `pnpm dev` inside WSL (hot reload); parity/pre-deploy check = `podman compose up --build` to exercise the exact production image. CI builds the image on every push/PR so the Containerfile can't silently rot.

## Phased delivery roadmap (→ GitHub issues/milestones)

**Phase 0 — Scaffolding.** pnpm + Turborepo; `apps/web` (Next.js+TS+Tailwind+shadcn); `packages/core`; `supabase init`; ESLint/Prettier/tsconfig base; `.gitignore`, `.env.example`; GitHub Actions (lint/typecheck/build); WSL2 + Podman dev setup, `Containerfile` + `compose.yaml` + container-image CI job; create + link Supabase Cloud project.

**Phase 1 — Backend foundation (issue #1 `add-backend`).** All migrations (profiles+trigger, households, members, invites, meals, day/week templates+items, planned_meals, enums); RLS + helper functions (recursion-safe); RPC functions; `seed.sql`; generate types into `packages/core`.

**Phase 2 — Auth + households.** Configure providers in Supabase (magic link + Google first; Apple provider config can follow when mobile nears); auth UI; onboarding (create household → become admin, or accept invite); household settings (rename, list/promote/demote/remove members, invites); app-layer single-household guard.

**Phase 3 — Meal library + weekly lunch planner (core value).** Meal library CRUD (auto-collected); weekly lunch view with add-from-library/quick-create; optional Realtime.

**Phase 4 — Reuse / templates.** Save current day/week as template; browse & apply templates to calendar; manage templates.

**Phase 5 — Polish + deploy.** Vercel deploy, env wiring, per-PR preview deploys, empty/loading/error states.

**Phase 6 — Mobile (LATER).** `apps/mobile` Expo reusing `packages/core`; Apple sign-in; EAS build + store submission.

**Phase 7 — AI recommendations (LATER).** Supabase **Edge Function** calling the **Claude API** (use the latest Claude model; consult the `claude-api` reference at build time for model id/pricing). Inputs: household meal history + current date/season → suggested lunches with reasoning + seasonal picks. Anthropic key stored as a Supabase secret. Optional `pgvector` embeddings table for "similar to what you liked" — no schema disruption.

**Phase 8 — Extensibility (LATER).** Recipes/ingredients tables + `meals.recipe_id`; multi-household (household switcher + relax app-layer guard); expose more meal slots in UI.

## Extensibility hooks (why "later" won't hurt)

- **Recipes/ingredients:** add tables + FK on `meals`; RLS mirrors `meals`; UI additive.
- **Multi-household:** schema already supports it (join table, no `user_id` unique); just add an active-household switcher and relax the app-layer guard.
- **More slots:** `meal_slot` enum already present.
- **AI:** reads existing tables; optional embeddings table only.

## Verification

- **Local dev (WSL2):** `supabase start` (local stack on Podman) or link to cloud; `supabase db reset` applies migrations + seed; `pnpm dev` runs web; `pnpm typecheck` + `pnpm lint` across the monorepo (Turborepo).
- **Container parity:** `podman compose up --build` runs the production standalone image locally; CI (`podman build`) verifies the image builds on every push/PR.
- **Manual E2E happy path:** sign in (magic link) → create household → add meals → plan a week of lunches → save week as template → move to a new week → apply the template.
- **RLS checks:** with a second account **not** in the household, confirm zero visibility; after accepting an invite, confirm household data appears; verify a `member` cannot perform admin-only actions.
- **CI:** GitHub Actions runs lint/typecheck/build on PRs; Vercel posts a preview URL per PR.
- **Later:** Vitest unit tests in `packages/core`; Playwright web E2E; optional pgTAP/SQL tests for RLS policies.
