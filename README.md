# 🍽️ Meal Magician

Plan your household's lunches for the week, save the meals/days/weeks you like, and reuse them later. Web-first, with a mobile app to follow and AI-powered recommendations planned down the road.

> **Status:** early development. See [`plan.md`](./plan.md) for the full development plan and roadmap.

---

## Overview

Meal Magician helps a **household** plan meals — mainly **lunches** — across a week. The core loop:

1. Create (or join) a **household**. The creator is the admin and can promote other members to admin.
2. Add **meals** — every meal you enter is automatically collected into your household's reusable **meal library**.
3. **Plan the week** by assigning meals to each day's lunch slot.
4. **Save & reuse** — save any day or week you like as a named **template**, then apply it to a future date to re-plan in one click.

Recipes and ingredients are intentionally **out of scope for now** (kept simple), but the data model is built so they — along with multiple households per member, extra meal slots, and an AI recommendation agent — can be added later **without a rewrite**.

---

## What the app looks like

The web app is a focused, app-like tool. Main screens/flows:

- **Sign in** — passwordless **magic link** or **Google** (Apple sign-in arrives with the mobile app). No password to manage.
- **Onboarding** — after first sign-in you either **create a household** (you become its admin) or **accept an invite** to join one.
- **Weekly planner** (home) — a **Monday→Sunday grid**. Each day shows its **lunch** slot; click a day to add a meal from your library (searchable autocomplete) or quick-create a new one. Navigate to previous/next weeks.
- **Meal library** — browse, search, edit, and delete the meals your household has entered.
- **Templates** — save the current day or week as a reusable template; browse saved templates and **apply** one onto a chosen date/week.
- **Household settings** (admin) — rename the household, view members, promote/demote admins, remove members, and invite people.

> The UI focuses on **lunch** today, but the database already supports breakfast/dinner/snack — turning them on later is a config change, not a migration.

---

## Tech stack & repo layout

| Layer | Choice |
|---|---|
| Monorepo | pnpm workspaces + Turborepo |
| Web | Next.js (App Router) + TypeScript + Tailwind CSS + shadcn/ui |
| Mobile (later) | Expo / React Native |
| Shared code | `packages/core` — DB types, Supabase client, data-access hooks, Zod schemas |
| Backend | **Supabase** — Postgres, Auth, Row-Level Security, auto REST (PostgREST), Edge Functions |
| Hosting | **Vercel** (web) + **Supabase Cloud** (backend) |

```
meal-magician/
├─ apps/
│  ├─ web/            Next.js web app (built first)
│  └─ mobile/         Expo / React Native app (later)
├─ packages/
│  └─ core/           Shared TypeScript: types, Supabase client, data hooks, schemas
├─ supabase/          Backend-as-code: SQL migrations, RLS policies, RPC & Edge Functions, seed.sql
├─ .github/workflows/ CI: lint, typecheck, build
└─ (workspace + tooling config at root)
```

The **running backend lives in Supabase Cloud**, but its schema, security policies, and functions are version-controlled here under `supabase/` and pushed with the Supabase CLI.

---

## Prerequisites

Install these before setting up:

- **Node.js** (LTS) and **[pnpm](https://pnpm.io/installation)** — `npm install -g pnpm`
- **[Supabase CLI](https://supabase.com/docs/guides/cli)** — `npm install -g supabase` (or Scoop/Homebrew)
- **[Docker Desktop](https://www.docker.com/products/docker-desktop/)** — only if you want to run the full Supabase stack locally (`supabase start`)
- Accounts: **[Supabase](https://supabase.com)**, **[Vercel](https://vercel.com)**, a **GitHub** account (repo is already on GitHub)
- Later, for mobile/social auth: a **Google Cloud** project and an **Apple Developer** account

---

## Supabase setup (UI + CLI)

### 1. Create the project (Supabase UI)

1. Go to **[app.supabase.com](https://app.supabase.com)** → **New project**.
2. Pick an organization, a **name** (e.g. `meal-magician`), a strong **database password** (save it), and a **region** close to you.
3. Wait for provisioning to finish.

### 2. Grab your keys (Supabase UI → Project Settings → API)

Copy these — you'll put them in your local `.env.local` and in Vercel:

| Value | Where to find it | Used by |
|---|---|---|
| **Project URL** | Settings → API → Project URL | web + mobile (public) |
| **anon public key** | Settings → API → Project API keys → `anon` | web + mobile (public — safe, RLS protects data) |
| **service_role key** | Settings → API → Project API keys → `service_role` | **server-side only** — never ship to the browser |
| **Project ref** | the `<ref>` in `https://<ref>.supabase.co` | CLI linking, OAuth redirect URLs |

### 3. Link the CLI and push the database (terminal)

```bash
supabase login
supabase link --project-ref <your-project-ref>
supabase db push          # applies everything in supabase/migrations to the cloud DB
```

For local development instead of cloud, run `supabase start` (needs Docker) and `supabase db reset` to apply migrations + seed data locally.

### 4. Configure Auth providers (Supabase UI → Authentication)

**Email / Magic Link** (Authentication → Providers → Email)
- Enable **Email**; enable **magic link** sign-in. That's it for passwordless email.

**Google** (Authentication → Providers → Google)
1. In **Google Cloud Console** → APIs & Services → Credentials → **Create OAuth client ID** (type: Web application).
2. Add the authorized redirect URI:
   `https://<your-project-ref>.supabase.co/auth/v1/callback`
3. Copy the **Client ID** and **Client secret** into Supabase's Google provider settings and enable it.

**Apple** (Authentication → Providers → Apple) — *needed for the iOS app; can be configured when mobile work begins*
1. In the **Apple Developer** portal, create a **Services ID**, enable **Sign in with Apple**, and create a **Sign in with Apple key**.
2. Note the **Services ID**, **Team ID**, **Key ID**, and the **private key** (`.p8`).
3. Enter these into Supabase's Apple provider settings and enable it.

### 5. Set URLs (Supabase UI → Authentication → URL Configuration)

- **Site URL:** your primary web URL (locally `http://localhost:3000`; in prod your Vercel domain).
- **Redirect URLs:** add each environment you use, e.g. `http://localhost:3000/**` and `https://<your-vercel-domain>/**`.

### 6. Edge Function secrets (later — AI phase)

When the AI recommendation function is added:

```bash
supabase secrets set ANTHROPIC_API_KEY=<your-anthropic-key>
```

---

## Vercel setup

1. Go to **[vercel.com](https://vercel.com)** → **Add New… → Project** → import the `meal-magician` GitHub repo.
2. **Root Directory:** set to **`apps/web`** (this is a monorepo — Vercel must build only the web app).
3. **Framework preset:** Next.js (auto-detected).
4. **Environment variables** — add:

   | Name | Value | Notes |
   |---|---|---|
   | `NEXT_PUBLIC_SUPABASE_URL` | your Project URL | public |
   | `NEXT_PUBLIC_SUPABASE_ANON_KEY` | your anon key | public |
   | `SUPABASE_SERVICE_ROLE_KEY` | your service_role key | **server-only** — do not prefix with `NEXT_PUBLIC_` |

5. **Deploy.** Every push to a branch/PR gets its own **preview URL**; merges to the main branch update production.
6. Back in **Supabase → Authentication → URL Configuration**, add your Vercel production (and preview) domains to the **Redirect URLs** so OAuth/magic-link callbacks succeed.

---

## Local development

```bash
# 1. Install all workspace dependencies
pnpm install

# 2. Configure environment variables
cp .env.example .env.local        # then fill in the values from Supabase

# 3. Start the backend
#    Option A — full local stack (requires Docker):
supabase start
supabase db reset                 # applies migrations + seed.sql locally
#    Option B — use your cloud project (skip supabase start; just link + db push)

# 4. Run the web app
pnpm dev                          # Next.js dev server, usually http://localhost:3000
```

Useful monorepo scripts (run from the repo root; Turborepo fans them out):

```bash
pnpm lint          # lint all packages
pnpm typecheck     # TypeScript across the monorepo
pnpm build         # production build
```

---

## Environment variables reference

| Variable | Where used | Where to get it |
|---|---|---|
| `NEXT_PUBLIC_SUPABASE_URL` | web (browser + server) | Supabase → Settings → API → Project URL |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | web (browser + server) | Supabase → Settings → API → `anon` key |
| `SUPABASE_SERVICE_ROLE_KEY` | web **server only**, Edge Functions | Supabase → Settings → API → `service_role` key |
| `ANTHROPIC_API_KEY` | Supabase Edge Function (AI phase) | Anthropic Console → API Keys |

> **Never** expose the `service_role` key or `ANTHROPIC_API_KEY` to the browser. Only `NEXT_PUBLIC_*` variables are safe client-side; Row-Level Security is what protects data behind the public anon key.

---

## Roadmap (short version)

- **Phase 0** — Monorepo scaffolding + Supabase project
- **Phase 1** — Backend: schema, RLS, RPC functions *(current: `feature/1-add-backend`)*
- **Phase 2** — Auth + households
- **Phase 3** — Meal library + weekly lunch planner
- **Phase 4** — Save & reuse (day/week templates)
- **Phase 5** — Polish + deploy
- **Phase 6** — Mobile app (Expo)
- **Phase 7** — AI recommendations (history-based + seasonal)
- **Phase 8** — Extensibility: recipes/ingredients, multi-household, more meal slots

Full details in [`plan.md`](./plan.md).
