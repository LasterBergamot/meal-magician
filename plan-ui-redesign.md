# Plan: Meal Magician UI redesign — a storybook look for the whole app

Status: **Wave 1 under way (R1 landed); split into waves for parallel subagents (§7)** · Written: 2026-11-01 · Owner: @LasterBergamot

This file is the working plan for giving Meal Magician a new look and a new way to move around:
a design system (colours, type, shapes, components), an app shell (a bottom tab bar on phones, a
sidebar on desktop) and a restyle of every page that exists today. It is **self-contained**: all
token values, icon paths and component rules an implementer needs are below, so no one has to open
a design tool. It sits beside [`PLAN.md`](PLAN.md), which stays the plan for features; this file
only changes how they look and feel. Update both as work lands: tick the box, add the issue and
PR numbers, and add a log row (§11).

> **Agent starting work:** read [§10 Start here](#10-start-here-next-agent) first.

---

## 1. Why, and what "done" looks like

The shell built in Phase 0 and 1 is a plain Tailwind skeleton: a green top bar, grey cards, one
hamburger menu on every screen size, no dark mode, no icons. The big screens are still ahead (meal
library, week planner, occasions, ratings; `PLAN.md` Phases 2–5), so this is the cheapest moment to
decide how the app looks, before those pages are built on top of it.

The owner asked for a look that is **modern, magical, colourful and a little like a cartoon or a
fairy tale** (the app is called Meal Magician), without the brown of the first draft, and a UX that
works well on a phone in the kitchen and on a laptop at the weekend.

**Done when:**

- every page that exists uses the design system (tokens, components, shell), in a light theme
  (**Moonlight**) and a dark theme (**Midnight**), with no leftover `brand-700`/`stone-*` classes;
- on a phone the app has a bottom tab bar (Plan, Meals, Occasions, Household) and a household
  switcher in the top bar; on desktop it has a collapsible sidebar;
- every token pair that carries text or a mark meets WCAG contrast in both themes, and a test says so;
- the Phase 2–5 pages in `PLAN.md` are built from the components here, not from raw Tailwind;
- the PWA theme colour, background colour and icons match the new palette.

**Not in scope:** new features, new data, a different JS stack. The redesign changes templates,
CSS, a little JavaScript (`app.js`), static assets and tests, and adds a theme preference. It
changes no domain logic.

---

## 2. Decisions

| # | Decision | Status |
|---|---|---|
| UI-D1 | **Style: storybook cartoon with a little magic.** A moonlit lilac stage, spell-violet actions, starlight-gold highlights, candy-bright meal colours; a 2 px twilight **outline** around everything touchable and **hard offset "sticker" shadows with no blur**; round shapes; four-point **sparkles** as the one decoration. No gradients (the faint star-dot page pattern is the only `radial-gradient`) and no soft blurred shadows. | Decided (owner, 2026-11-01) |
| UI-D2 | **Two full themes:** *Moonlight* (light) and *Midnight* (dark), not an inversion: lighter fills carry dark `on-*` text. The theme is `light`, `dark` or `system` (default: follow `prefers-color-scheme`). | Decided |
| UI-D3 | **Colour roles:** `brand` = spell violet (primary action, active navigation, links); `accent` = starlight gold, **a fill only, never text**, once per screen (add button, today, occasion); meal types own a trio each (sunrise yellow, mint, night sapphire, candy pink); `danger` is cherry red and never the accent. Meal type is always colour + icon + word. | Decided |
| UI-D4 | **Type:** headings in **Fredoka** (rounded, cartoon), body in **Nunito** (rounded, quiet), both open-licensed (SIL OFL), **self-hosted** as `woff2` in `static/fonts/` (the CSP is `font-src 'self'`; no CDN). Hungarian needs the **latin-ext** subset (ő, ű): Nunito has it, **Fredoka has no Latin Extended-A glyphs at all**, so the browser takes ő and ű in headings from the next family, **Nunito** (same weights, already hosted). Display stack: `"Fredoka", "Nunito", "Trebuchet MS", system-ui, sans-serif`. Neither font has the `tnum` feature, so there are no tabular numerals. Alternative faces the owner named as acceptable: Baloo 2, Sniglet (display), Fraunces (serif storybook); the owner chose *not* to switch to Baloo 2 (#71). *(Amended 2026-11-01; was Fredoka alone, with Baloo 2 named but not hosted)* | Decided (owner chose "other fonts could be good too"; Fredoka + Nunito proposed 2026-11-01; Nunito fallback for ő/ű decided by the owner 2026-11-01, #71) |
| UI-D5 | **Navigation:** phone = top bar (household switcher pill, avatar) + **bottom tab bar** with four destinations (Plan, Meals, Occasions, Household) + a floating add button on Plan and Meals; desktop (≥ 768 px) = **left sidebar** (household switcher, the same four destinations, language, sign-out; collapsible to icons). The household switcher moves from `/households` into both shells. | Decided |
| UI-D6 | **Editing in context:** a **bottom sheet** on phone and a **side panel or dialog** on desktop (one markup, CSS chooses by breakpoint, native `<dialog>` + an Alpine component). Destructive confirmations are always dialogs. | Decided |
| UI-D7 | **Implementation on the existing stack:** Tailwind v4 `@theme` in `src/main/css/app.css` with the colours as CSS variables per theme; JTE fragments under `src/main/jte/ui/` and `layout/`; no new JS libraries (Alpine CSP build + htmx stay); icons are inline SVG in one JTE fragment. Everything stays inside the CSP (no inline `style=`, no inline script, no CDN). | Decided |
| UI-D8 | **Theme preference** is a cookie `mm_theme` (`light`\|`dark`\|`system`), like `mm_lang` (D14), read by the server so `<html data-theme>` is right on first paint (no flash). Per user in the DB comes later, together with the per-user language. | Proposed — confirm with the owner when R6 starts |
| UI-D9 | **UI copy** stays Hungarian by default, English selectable (D14); every new string goes in both bundles. Tone: warm, short, a touch whimsical in empty states and success moments, never in errors or destructive confirmations. | Decided |
| UI-D10 | **Accessibility is a gate, not a nice-to-have:** the contrast pairs in Appendix C are checked by a test in both themes; touch targets ≥ 44 px (the `sm` button is the one pointer-only exception); status is never colour alone; visible 3 px focus ring; `prefers-reduced-motion` turns every transition and animation off. | Decided |
| UI-D11 | **Brand mark = the wizard.** The cartoon wizard on a gold tile (`favicon.svg`, PR #61) is both the app icon and the logo inside the app (sign-in, sidebar, top bar). | Decided (owner, 2026-11-01) *(was: a `wand` icon in a violet rounded tile)* |

The visual reference is a private design-system artifact in the owner's Claude account
(`https://claude.ai/artifact/XAHNAEX9ZGGhGpp2FwTMXU`, "Meal Magician Design System"; light/dark
switch on each preview card). Agents can usually not open it: **this file is the source of truth**.
If they differ, this file wins; tell the owner and update the artifact.

---

## 3. The design system

### 3.1 Principles

1. **Phone first, desktop better.** Design the 375 px screen, then add `md:` (768 px) and `lg:`
   (1024 px). Never hide a feature on the phone.
2. **One thumb, one task.** Primary actions live in the bottom third of the phone screen.
3. **Scannable at a glance.** Meal types differ by colour, icon and word.
4. **Bright, but one star per screen.** Lilac and white everywhere, meal colours where they carry
   meaning, one gold highlight.
5. **Playful, never noisy.** Sparkles only on hero moments (sign-in, empty states, today,
   occasions), at most two per screen; lists, forms and the week stay clean.
6. **Fast and forgiving.** Quick-add needs only a name; every error, conflict and empty state says
   what happened and what to do.

### 3.2 Colour tokens

Light = **Moonlight**, dark = **Midnight**. The "role" column says where each is used. Names are
the design-system names; the CSS variables carry the prefix `--mm-` (Appendix A).

| Token | Moonlight (light) | Midnight (dark) | Role |
|---|---|---|---|
| `surface` | `#f8f5ff` | `#130c2e` | Page background ('the stage'): a pale moonlit lilac in light, midnight in dark. Text on it: `ink`, `ink-muted`, `brand`. |
| `surface-raised` | `#ffffff` | `#1d1442` | Cards, sheets, dialogs, inputs and the sidebar. Text on it: `ink`, `ink-muted`. |
| `surface-sunken` | `#ece5fb` | `#0d0820` | Wells behind cards, skeletons, segmented-control track, empty cells. Text on it: `ink`, `ink-muted`. |
| `border` | `#d9cff2` | `#34296b` | Decorative hairlines and the faint star-dot pattern. Never the only cue for a control. |
| `border-strong` | `#7a6bb0` | `#8f86c8` | Borders of inputs, selects and unchecked checkboxes (3:1 on every surface). |
| `outline` | `#24174d` | `#9b8cf0` | The cartoon outline: 2px around buttons, cards, chips, slot tiles and device frames. Also the colour of the hard sticker shadow in light theme. |
| `ink` | `#24174d` | `#f4efff` | Body and heading text on `surface`, `surface-raised`, `surface-sunken`. Twilight indigo, not black. |
| `ink-muted` | `#5b4f85` | `#b9b0e0` | Secondary text, captions, placeholders on `surface`, `surface-raised`, `surface-sunken`. |
| `brand` | `#6d3fe0` | `#a98bff` | Spell violet. Primary buttons, active navigation, links, selected states. Fill with `on-brand`; as text on `surface` and `surface-raised`. |
| `brand-hover` | `#5a2dc4` | `#c1a9ff` | Hover and pressed fill of `brand` controls. Text on it: `on-brand`. |
| `on-brand` | `#ffffff` | `#1a0b4d` | Text and icons placed on `brand` and `brand-hover`. Dark in the dark theme because the fill is light there. |
| `brand-soft` | `#e8defc` | `#2b1d66` | Tinted background for selected rows, the current household, active tab pill. Text on it: `brand-ink`. |
| `brand-ink` | `#4a22a8` | `#d4c6ff` | Text and icons on `brand-soft`. |
| `accent` | `#ffc83d` | `#ffd04d` | Starlight gold. The single highlight per screen: the add button, today, occasions, stars and sparkles. Always a FILL with `on-accent` on it; never text. |
| `on-accent` | `#24174d` | `#24174d` | Text and icons on `accent`. Indigo on gold in both themes. |
| `accent-soft` | `#fff0c2` | `#4a3a0f` | Tinted background for occasion cards and the 'today' day header. Text on it: `accent-ink`. |
| `accent-ink` | `#6b4300` | `#ffe08a` | Text and icons on `accent-soft`. |
| `slot-breakfast` | `#b86e00` | `#ffc247` | Breakfast dot and icon (sunrise yellow). A mark, not text (3:1 on `surface-raised`). |
| `slot-breakfast-soft` | `#ffe9a8` | `#4a3710` | Breakfast chip and cell background. Text on it: `slot-breakfast-ink`. |
| `slot-breakfast-ink` | `#6b4300` | `#ffe3a0` | Text and icons on `slot-breakfast-soft`. |
| `slot-lunch` | `#0a8a67` | `#4fe0b3` | Lunch dot and icon (mint). A mark, not text. |
| `slot-lunch-soft` | `#c9f5e4` | `#0f4a3a` | Lunch chip and cell background. Text on it: `slot-lunch-ink`. |
| `slot-lunch-ink` | `#0b5a44` | `#b5f5dc` | Text and icons on `slot-lunch-soft`. |
| `slot-dinner` | `#2f64e6` | `#7aa8ff` | Dinner dot and icon (night sapphire). A mark, not text. |
| `slot-dinner-soft` | `#d5e4ff` | `#1a2f6b` | Dinner chip and cell background. Text on it: `slot-dinner-ink`. |
| `slot-dinner-ink` | `#17398f` | `#c3d6ff` | Text and icons on `slot-dinner-soft`. |
| `slot-snack` | `#d12a8f` | `#ff7cc4` | Snack dot and icon (candy pink). A mark, not text. |
| `slot-snack-soft` | `#ffd6ee` | `#5a1a44` | Snack chip and cell background. Text on it: `slot-snack-ink`. |
| `slot-snack-ink` | `#8a1360` | `#ffc2e3` | Text and icons on `slot-snack-soft`. |
| `success-soft` | `#c9f5e4` | `#0f4a3a` | Success alert background. Text on it: `success-ink`. Always paired with a check icon and a word. |
| `success-ink` | `#0b5a44` | `#b5f5dc` | Text and icons on `success-soft`. |
| `warning-soft` | `#fff0c2` | `#4a3710` | Warning alert background, e.g. the 'someone else changed this' conflict banner. Text on it: `warning-ink`. |
| `warning-ink` | `#6b4300` | `#ffe3a0` | Text and icons on `warning-soft`. |
| `danger` | `#c01a42` | `#ff7d96` | Destructive buttons and error text on `surface` and `surface-raised` (cherry red). Fill with `on-danger`. |
| `on-danger` | `#ffffff` | `#3a0612` | Text and icons on `danger`. |
| `danger-soft` | `#ffd9e0` | `#591527` | Error alert background. Text on it: `danger-ink`. Paired with an alert icon and a word. |
| `danger-ink` | `#8a0f2e` | `#ffc2cd` | Text and icons on `danger-soft`. |
| `info-soft` | `#d5e4ff` | `#1a2f6b` | Info alert background. Text on it: `info-ink`. |
| `info-ink` | `#17398f` | `#c3d6ff` | Text and icons on `info-soft`. |
| `focus-ring` | `#0b5bd3` | `#ffd04d` | Solid 3px keyboard focus outline with a 2px offset, on every surface (blue in light, gold in dark). |
| `scrim` | `#24174dcc` | `#05020fb3` | Backdrop behind sheets and dialogs. |

### 3.3 Shape, space, shadow, type, layout, motion

| Group | Tokens |
|---|---|
| **Spacing** (4 px grid; equals Tailwind's default scale, so `p-4` = `space-4` = 16 px) | `space-1` 4 · `space-2` 8 · `space-3` 12 · `space-4` 16 · `space-5` 20 · `space-6` 24 · `space-8` 32 · `space-10` 40 · `space-12` 48 · `space-16` 64. Page gutter `space-4` on phone, `space-8` from `md`. Card padding `space-4` phone, `space-6` desktop. |
| **Radius** (round and bouncy; nothing under 10 px) | `radius-sm` 10 (chips, small buttons) · `radius-md` 14 (buttons, inputs, rows) · `radius-lg` 20 (cards, week cells) · `radius-xl` 28 (sheets, dialogs) · pill = `rounded-full` (badges, chips, segmented control, add button). |
| **Sticker shadow** (hard, offset, no blur; always with the 2 px `outline`) | `shadow-1` `0 2px 0` (buttons, chips, rows, slot rows) · `shadow-2` `3px 3px 0` (cards, week tiles) · `shadow-3` `5px 5px 0` (add button, sheets, dialogs). Colour `#24174d` in Moonlight, `#05020f` in Midnight. Pressed = no shadow and moved 1–2 px down. |
| **Type families** | display `"Fredoka", "Nunito", "Trebuchet MS", system-ui, sans-serif` (Nunito draws the ő/ű that Fredoka lacks, UI-D4) *(Amended 2026-11-01; was Fredoka alone, with Baloo 2 named but not hosted)* · sans `"Nunito", system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif` |
| **Type scale** | `display` Fredoka 700 40/44 (32/36 on phone; sign-in and empty-state hero only) · `h1` Fredoka 600 30/36 (one per page) · `h2` Fredoka 600 22/28 (sections, day names) · `h3` Nunito 800 18/24 (row titles, meal names) · `body` Nunito 500 16/24 (16 px stops iOS zooming into inputs) · `body-sm` 500 14/20 · `label` 800 14/20 (buttons, tabs, chips, field labels) · `caption` 700 12/16 +0.02em (timestamps, slot names, badges). No tabular numerals: neither font has the `tnum` feature, so digits are proportional *(Amended 2026-11-01; was "Tabular numerals for dates and counts")*. |
| **Layout** | `touch` 44 px (min size of anything tappable) · `layout-bottom-bar` 64 px (+ safe-area inset) · `layout-sidebar` 248 px · `layout-sidebar-collapsed` 72 px · `layout-content` 1200 px max · `layout-reading` 720 px max (forms, single-column pages). |
| **Motion** | `duration-fast` 120 ms (hover, press) · `duration-base` 200 ms (sheet, dialog) · `duration-slow` 320 ms (week change). Under `prefers-reduced-motion: reduce` all are 0 and animations are off. A small star pop when a meal is saved is allowed, but not under reduced motion. |

### 3.4 The look, as rules

- **Outline:** buttons, chips, badges, cards, list containers, rows, slot tiles, the sidebar edge,
  the tab-bar top edge, the avatar and the device frames get `border-2 border-outline`. Inputs use
  `border-2 border-border-strong` (3:1). Empty slots and cells use a dashed `border-border-strong`.
- **Press feel:** hover `translate(-1px,-1px)` and `shadow-2`; active `translate(1px,2px)` and no
  shadow; `ghost` buttons do not move.
- **Page background:** `surface` with a faint dot pattern in `border`:
  `radial-gradient(var(--mm-border) 1.5px, transparent 1.5px)` at 28 px, written once in
  `app.css`. Sheets, dialogs and inputs sit on `surface-raised`, without the pattern.
- **Selected / current:** `brand` fill (chips, segmented control, sidebar item) or a gold outlined
  pill behind the tab icon on phone. The word "Current" and a check icon always accompany it.
- **Today:** a gold `accent` fill (column header on desktop, number disc on the phone week strip)
  plus a "Today" badge. Gold is **never text**.
- **Sparkles:** the filled, outlined `sparkle` icon (`text-accent` with a 1.5 px `outline` stroke),
  on the sign-in page, empty states, the "Today" badge and occasion cards. The **brand mark** is
  the cartoon wizard on a gold tile, the same artwork as the app icon (UI-D11): on the sign-in page,
  in the sidebar header and in the phone top bar, as `<img src="/icons/favicon.svg" alt="">` next
  to the wordmark (it is decorative there, the wordmark carries the name). The `wand` icon stays in
  the icon set for actions, not as the logo.
- **No emoji** as decoration or UI, no blurred shadows, no gradients, no coloured left borders on
  cards.

### 3.5 Meal types

| Type | Icon | Colour family | Tokens |
|---|---|---|---|
| Breakfast | `breakfast` (cup) | sunrise yellow | `slot-breakfast`, `-soft`, `-ink` |
| Lunch | `lunch` (sun) | mint | `slot-lunch`, `-soft`, `-ink` |
| Dinner | `dinner` (moon) | night sapphire | `slot-dinner`, `-soft`, `-ink` |
| Snack | `snack` (apple) | candy pink | `slot-snack`, `-soft`, `-ink` |

Order is always breakfast, lunch, dinner, snack. A CSS class per type sets three variables
(`--slot`, `--slot-soft`, `--slot-ink`) so one set of utilities (`bg-slot-soft text-slot-ink`)
serves all four: `.slot-lunch { --slot: var(--mm-slot-lunch); … }` in `@layer components`.

### 3.6 Icons

One outline set on a 24 px grid, 2 px round stroke, `currentColor`, 30 icons (Appendix B). Sizes:
20 px inline, 24 px in navigation, 32–36 px in slot dots and empty-state art. Always
`aria-hidden="true"` with a text label next to it, or an `sr-only` label on icon-only buttons. The
`sparkle` is the only filled icon.

### 3.7 Voice

Warm, short, second person, sentence case; verbs on buttons ("Save", "Invite"); destructive
confirmations name the thing ("Remove Anna from Kovács household?"). A touch of whimsy in empty
states and success moments ("Ta-da! Week copied."), none in errors. Hungarian first.

---

## 4. App shell and navigation

| | Phone (< 768 px) | Desktop (≥ 768 px) |
|---|---|---|
| Top | Top bar: household switcher pill (opens a bottom sheet of households) and the user avatar (menu: language, theme, sign out) | None; the sidebar carries it |
| Primary nav | **Bottom tab bar**, 64 px + safe-area inset: Plan, Meals, Occasions, Household; the current tab has a gold outlined pill behind its icon and a bolder label; hides under the on-screen keyboard | **Left sidebar**, 248 px: household switcher on top, the four destinations (current = violet outlined row), a "Planner" section label, then language (HU/EN), theme, collapse and sign out at the bottom |
| Collapse | — | A button shrinks it to 72 px (icons only, each with a tooltip and `aria-label`). The choice is kept in `localStorage` (a per-viewer convenience, so wrap it in try/catch). Starts collapsed below 1024 px. |
| Add | Floating gold **add button** above the tab bar on Plan and Meals | A normal violet `primary` button in the page header |
| Content width | full, gutter `space-4` | `layout-content` max, gutter `space-8`; forms and single-column pages `layout-reading` |
| Week | A list of days with a week strip; swipe changes week | A 7 × 4 grid with a side panel |

Rules shared by both:

- The **household switcher** is always visible and replaces today's link to `/households`.
  Switching reloads the current destination.
- Links use `hx-boost`/`hx-target="#content"` so only the content swaps and the shell does not
  re-render; the URL is pushed (`hx-push-url`) so reload and Back work. A 401/403 still sends the
  whole page to `/` (existing `app.js` behaviour).
- htmx error fragments still land in `#error-slot` as a `danger` alert at the top of the content.
- Landmarks: skip link, `<header>`, `<nav aria-label>`, `<main id="main">`. Focus stays visible.
- Anonymous pages (sign-in, error) use the base layout without the shell.

Destinations and their routes today: Plan (new, Phase 3; until it exists the tab points to the
hello page), Meals (Phase 2), Occasions (Phase 4), Household (`/households`). Tabs whose page does
not exist yet are **hidden**, not dead, and appear as their phase lands (see R4).

---

## 5. Component inventory

Each component gets a JTE fragment under `src/main/jte/ui/` (or `layout/`/`plan/`/…) with typed
params, and a tiny Tailwind class set written out in full (no `"bg-" + color`). The names are the
design-system names. The behaviour column is the contract.

| Component | JTE fragment(s) | Behaviour and rules |
|---|---|---|
| **Button** | `ui/_button.jte` (`variant`, `size`, `label`, `icon`, attrs) | `primary` (violet; one per view), `secondary` (white), `ghost` (quiet, in context), `danger` (cherry; always behind a confirm); sizes `md` 44 px, `lg` 54 px, `sm` 36 px (dense desktop tables only), `icon` 44×44. htmx adds `htmx-request` → spinner and no double submits (`aria-busy`). Label is a verb from `ctx.t`. |
| **Add button (FAB)** | `ui/_fab.jte` | Gold, outlined, `shadow-3`, 58 px, above the tab bar on phone, `bottom-[calc(var(--mm-bottom-bar)+16px)]`; on desktop it becomes a `primary` button in the header. |
| **Field** | `ui/_field.jte`, `ui/_select.jte`, `ui/_search.jte` | Visible `<label>` (never placeholder-as-label), 48 px high, 16 px text, 2 px `border-strong`; error = `aria-invalid` + red border + icon-and-sentence line linked by `aria-describedby`. A rejected form returns the same fragment with 422 and keeps the typed value. Search is a pill with a leading icon. |
| **Chip / badge / slot chip / slot dot** | `ui/_chip.jte`, `ui/_badge.jte`, `ui/_slot_chip.jte`, `ui/_slot_dot.jte` | Filter chips use `aria-pressed` (selected = violet fill + check). Badges label state (Owner, Member, Today, Current), never interactive. Slot chips and dots take `slot` = `BREAKFAST…SNACK` and always show icon + word (or `aria-label` on a lone dot). |
| **Card / list row / avatar** | `ui/_card.jte`, `ui/_row.jte`, `ui/_avatar.jte` | Card = white, 2 px outline, `shadow-2`, `rounded-lg`; accent card (`accent-soft`) for occasions only; selected = `brand-soft`. A list is a card of rows ≥ 60 px with dashed separators. One stretched link per card, no nested links. Avatar = gold disc with initials. |
| **Segmented control + week stepper** | `ui/_segmented.jte`, `plan/_week_stepper.jte` | `role="tablist"`, 2–4 options, active = violet pill; swaps an htmx fragment with `hx-push-url`. Stepper = previous / title (`h2` + range) / next, 44 px icon buttons, `←`/`→` keys on desktop. |
| **Alert** | `ui/_alert.jte` (`kind`, `title`, `message`, action) | Icon + bold title + one sentence; `success|info|warning|danger`; `role="alert"` for warning and danger. The **409 conflict** is a `warning` with a Reload button. No floating toasts in v1. |
| **Sheet / dialog / confirm** | `ui/_sheet.jte`, `ui/_confirm.jte`; Alpine `sheet` | Native `<dialog>`; phone = bottom sheet (grabber, `rounded-t-xl`, scrim), desktop ≥ `md` = centred dialog or right side panel. Focus moves in, is trapped, returns to the trigger; Escape closes; swipe-down closes on phone. Content loads with htmx. Never a sheet from a sheet. |
| **Empty state / skeleton** | `ui/_empty.jte`, `ui/_skeleton_rows.jte` | Gold outlined round icon with two sparkles, `h2`, one sentence, one primary action (+ at most one ghost). Skeleton mimics the coming layout, region has `aria-busy="true"`; use for htmx loads > ~300 ms (`hx-indicator`). |
| **Rating** | `feedback/_rating.jte`, `feedback/_stars.jte` | Thumbs up/down (`aria-pressed`; up violet, down cherry; the icon differs), then optional 1–5 stars (gold when pressed) and a comment. Phase 5. |
| **Icon** | `ui/_icon.jte` (`name`, `size`) | Switch over the names in Appendix B; renders the inline `<svg>`. One place to add icons. |
| **Shell** | `layout/_shell.jte`, `layout/_tabbar.jte`, `layout/_sidebar.jte`, `layout/_household_switcher.jte`, `layout/_topbar.jte`; Alpine `shell`, `theme` | §4. `base.jte` keeps the `<head>` (CSP-safe metas, htmx config) and wraps `_shell` for signed-in pages. |
| **Week (desktop)** | `plan/week.jte`, `plan/_grid.jte`, `plan/_cell.jte`, `plan/_panel.jte` | 7 × 4 grid built row by row (one grid row = one slot across the seven days, so rows line up), today column gold, occasion strip above, legend below, side panel for the meal picker. Phase 3. |
| **Week (phone)** | `plan/_day.jte`, `plan/_slot_row.jte`, `plan/_strip.jte`, `plan/_pick_sheet.jte` | Week strip (7 numbers, today = gold disc), day sections with four slot rows (≥ 56 px; empty = dashed "Add lunch"), pick sheet with search, recent meals and **Quick add** by name. Phase 3. |
| **Meals** | `catalog/meals.jte`, `_list.jte`, `_card.jte`, `_quick_add.jte`, `_detail.jte` | Search + tag chips + meal cards; quick-add bar pinned above the tab bar on phone; two-column (three from `xl`) grid on desktop. Phase 2. |
| **Occasions** | `planning/occasions.jte`, `_occasion_card.jte`, `occasion.jte`, `feedback/_prompt.jte` | Next occasion as a gold-soft card, templates as rows, occasion detail with a menu and "Save as template"; the "How did it go?" card on Plan. Phase 4–5. |

---

## 6. Technical approach

### 6.1 Tokens in Tailwind v4

- `src/main/css/app.css` keeps `@import "tailwindcss" source(none)` and the two `@source` lines.
  Appendix A is the block to add: the raw variables (`--mm-surface`, …) per theme, then
  `@theme inline { --color-surface: var(--mm-surface); … }` so utilities exist (`bg-surface`,
  `text-ink`, `border-outline`, `bg-brand`, `text-slot-ink`, `shadow-sticker-2`). The `--mm-`
  prefix avoids clashing with Tailwind's own `--color-*`/`--shadow-*` namespaces.
- Theme selection: `:root` = Moonlight; `[data-theme="dark"]` = Midnight; and for `system`, an
  `@media (prefers-color-scheme: dark)` block under `:root:not([data-theme="light"]):not([data-theme="dark"])`.
- `@theme { --radius-sm: 10px; --radius-md: 14px; --radius-lg: 20px; --radius-xl: 28px; --font-display: …; --font-sans: …; }`
  (not themed). Spacing needs nothing: Tailwind's scale already matches. Durations: small
  `@utility` helpers (`motion-fast`, `motion-base`, `motion-slow`) reading `--mm-duration-*`, which
  become `0ms` under `prefers-reduced-motion`.
- Keep the old `brand-50/100/600/700/800` scale **only until R5 finishes**, then delete it (and the
  `stone-*` classes) so nothing half-migrated survives.
- The generated `static/css/app.css` stays ignored; never edit it.

### 6.2 Fonts and PWA assets

- Add `static/fonts/fredoka-*.woff2` and `nunito-*.woff2` (Nunito latin + latin-ext; Fredoka latin
  only, it has no latin-ext glyphs; the weights used: Fredoka 600/700, Nunito 500/700/800), `@font-face` in `app.css` with `font-display: swap`, a
  `<link rel="preload" as="font" type="font/woff2" crossorigin>` for the two used above the fold.
  Record each file's source URL, licence (SIL OFL 1.1) and SHA-256 in `static/fonts/FONTS.md`
  (like `static/js/VENDORED.md`) and add the licence texts.
- `static/sw.js`: add `fonts` to `STATIC_PATHS` and bump `CACHE` to `mm-static-v2` (in R1, with
  the fonts).
- `manifest.webmanifest`: `theme_color` → `#6d3fe0`, `background_color` → `#f8f5ff`; `<meta name="theme-color">`
  in `base.jte` gets a light and a dark value (`media="(prefers-color-scheme: …)"`) (both in R1).
  The icons are done: a cartoon wizard on a gold outlined tile (`favicon.svg`, `favicon.ico`, 192,
  512, maskable 512, apple-touch 180), drawn by `scripts/generate-icons.py` (PR #61). *(Amended
  2026-11-01; was: the `wand` mark on a violet outlined tile.)* `/favicon.ico` lies outside
  `STATIC_PATHS`, which is fine: browsers cache it themselves.
- `documentation/References.md`: add rows for Fredoka, Nunito and the OFL.

### 6.3 Templates, JS and CSP

- All rules in `.claude/rules/web-ui.md` still hold: typed view-model records, `ctx.t("key")`,
  `Htmx.view(...)` for page-or-fragment URLs, `Htmx.redirect(...)` after a successful post, 422/409
  fragments, no inline `<script>`/handlers/`style=`, no CDN. `UiGuardTest` keeps checking.
- New Alpine components (CSP build, names only, registered in `app.js`): `shell` (sidebar
  collapse), `sheet` (open/close, focus trap, swipe), `theme` (set the cookie, flip `data-theme`),
  `menu` stays. Keep `app.js` small; split per component if it passes ~200 lines.
- `ViewContext` gains what the shell needs (the current user's display name/initials, the nav
  destinations that exist, the theme). Add them there once, so pages do not change.
- New message keys (both bundles). Navigation: `nav.plan` Terv/Plan, `nav.meals` Ételek/Meals,
  `nav.occasions` Alkalmak/Occasions, `nav.household` Háztartás/Household, `nav.collapse`, `nav.expand`,
  `theme.label`, `theme.light` Moonlight, `theme.dark` Midnight, `theme.system`; plus empty-state,
  alert and button strings per page as they are restyled. The bundle-parity test must stay green.

### 6.4 Tests and gates

- `task lint`, `task test` (100 % line + branch), `task test-e2e` for every UI PR (it touches
  `src/main/jte/**` and `static/**`); docs-only PRs skip the E2E steps automatically.
- **Contrast test:** a unit test parses the variables from `src/main/css/app.css` and asserts every
  pair in Appendix C in both themes (WCAG 2 ratios). It must fail when a token is changed to a
  failing value (test the checker on a bad example too, as `UiGuardTest` does).
- **Guard tests:** extend `UiGuardTest` to fail on a leftover `brand-\d`/`stone-` class after R5, and
  on a missing `aria-label` for an icon-only `<button>`.
- **E2E (Playwright):** the sign-in flow and the households page at phone (375×812) and desktop
  (1280×800) in both themes; assert the tab bar (phone) and sidebar (desktop) exist, the household
  switcher works, and a keyboard user can reach and operate the sheet and the dialog. Save
  screenshots as CI artifacts for review. Optionally run axe-core through Playwright (vendored, no CDN).
- Manual before closing the redesign: Lighthouse accessibility ≥ 95 on a phone, install the PWA on a
  real phone, check Midnight after sunset.

---

## 7. Phases and issues

The epic **UI redesign** is issue **#57** (milestone `M1 – Core planner`, filed 2026-11-01 at the
owner's request and made the next item in `PLAN.md`). Its sub-issues, one per item below, are
filed with the `new-issue` skill as each item starts (R1 first), linked to #57 as GitHub
sub-issues, and the `R#` ids below are replaced with the real numbers then. Branch as
`feat/<issue>-…` and write commit subjects as `#N - <summary>`. Each item is one PR through `create-pr`; D22 lets an
agent merge its own PR for planned work once every required check is green, **once the owner has
approved this plan** (the owner approved it by asking for it, 2026-11-01).

The items are grouped into **waves** so that several subagents can work at once (§7.1). Items in
one wave touch different files and run in parallel, each in its own git worktree and on its own
branch; a wave starts when the waves before it have been merged. *(Amended 2026-11-01; was: one
list R0–R9 with R2 ∥ R3 and R5/R6/R7 as the only parallel steps. R2 and R5 are now split by file,
R7's rest moved into R1, and each Alpine component and shared template has one owner per wave.)*

- [x] **R0 Plan and design agreed.** This file, the design-system artifact, the pointers in
      `PLAN.md`, `CLAUDE.md`, `README.md`, `documentation/Roadmap-Frontend.md`. *(PR #55; epic #57
      and "next in `PLAN.md`" in PR #60.)*

**Wave 1: start now.**

- [x] **R1 Tokens, fonts and base CSS** *(everything depends on it; PR #69).* Appendix A into `app.css`;
      fonts and `@font-face` (§6.2); the page background pattern; `[data-theme]` selection; the
      `@source` lines unchanged. In `layout/base.jte` only the `<head>` and the `<html>` tag change:
      `data-theme`, the light and dark `theme-color` metas and the font preloads. `sw.js`: `fonts`
      in `STATIC_PATHS` and `mm-static-v2` (moved here from R7, because R1 adds the fonts). The
      `manifest.webmanifest` colours (`theme_color` `#6d3fe0`, `background_color` `#f8f5ff`) and
      the matching `PwaAssetsTest` expectation (moved here from R7, the rest of which landed early).
      The
      `References.md` rows for Fredoka, Nunito and the OFL. Add the contrast test (§6.4).
      *Accept:* the app still renders; `task test` passes with the new test; both themes switch
      by editing `data-theme` in dev tools.
- [x] **R2a Icons, button and the `/dev/ui` page** *(written beside R1, merged after it; PR #70).*
      `ui/_icon.jte` with every Appendix B path, `ui/_button.jte` (all variants and sizes) and
      `ui/_fab.jte`; the `/dev/ui` page (dev profile only, not in the endpoint-auth list), a living
      style guide for agents and e2e. Its template `dev/ui.jte` includes one section fragment per
      later item, created here as empty stubs: `dev/_content.jte` (R2b), `dev/_forms.jte` (R2c),
      `dev/_overlays.jte` (R3), so Wave 2 fills its own files and never edits the same template.
      The button is here, not in R2b, because the sheet, the confirm dialog, the alert and the
      empty state all contain buttons. The utilities it uses (`bg-brand`, `shadow-sticker-1`…) only
      exist once R1 is merged: rebase on R1 and check the page in both themes before merging.
      *Accept:* `UiGuardTest` green; `/dev/ui` shows every icon and button state in both themes.
- [x] **R7 App icons** *(landed early).* The wizard favicon and the PWA icon set, with
      `scripts/generate-icons.py` and the `<link rel="icon">` for the SVG. *(PR #61. The manifest
      colours, the `theme-color` metas and `sw.js` v2 moved to R1, so all colour changes are in one
      PR; the Lighthouse PWA check and "the icons look right on a real phone" moved to R9.)*

**Wave 2: after R1 and R2a are merged.**

- [ ] **R2b Content and status components.** `ui/_badge`, `_chip`, `_slot_chip`, `_slot_dot`,
      `_card`, `_row`, `_avatar`, `_alert`, `_empty`, `_skeleton_rows`; fills `dev/_content.jte`.
      *Accept:* every component and state on `/dev/ui` in both themes; `UiGuardTest` green.
- [ ] **R2c Form components.** `ui/_field`, `_select`, `_search`, `_segmented`, including the 422
      error state; fills `dev/_forms.jte`. *Accept:* as R2b.
- [ ] **R3 Sheet and confirm dialog.** `ui/_sheet.jte`, `ui/_confirm.jte`, the Alpine `sheet`
      component (the only `app.js` change in this wave); focus trap, Escape, swipe-down on phone;
      fills `dev/_overlays.jte`. The `shell` and `theme` Alpine components moved to R4 and R6, so
      `app.js` has one writer per wave. *Accept:* a keyboard e2e test on `/dev/ui` opens, traps,
      closes and restores focus.

**Wave 3: after Wave 2. One UI item only.**

- [ ] **R4 Shell and navigation.** `layout/_shell`, `_tabbar`, `_sidebar`, `_topbar`,
      `_household_switcher`; the Alpine `shell` component; `ViewContext` additions; `base.jte`'s
      `<body>` wraps the shell for signed-in pages; hide tabs whose page does not exist yet; the
      household switcher in both shells; sign-out button and the stored per-user language hook (the
      Roadmap-Frontend "Phase 0 rest" items, if the owner wants them in this PR). Nothing else in the
      UI runs beside it: every page renders inside the shell, and it uses the components of R2a–R3.
      The backend track in `PLAN.md` §9 keeps going. *Accept:* phone and desktop e2e show the right
      navigation; `/households` no longer needs its own switch buttons.

**Wave 4: after R4. Four UI items plus the Phase 2 pages (`PLAN.md`); R5a and R5c are small, so
one subagent takes both and the wave has four UI subagents.**

- [ ] **R5a Restyle sign-in, error and hello.** `login/page`, `error/page` + `_message`, `hello/*`.
- [ ] **R5b Restyle the household pages.** Every `household/*` template (`_content`, `_create`,
      `_rename`, `_invites`, `_members`, `join`, `members`, `page`); remove the switch buttons.
- [ ] **R5c Restyle the admin allowlist.** `admin/allowlist` + `_allowlist` (added by #45 after this
      plan was written; same list-and-card pattern, §8).
- *R5a–R5c:* behaviour and routes do not change, so `HouseholdPagesTest`, `AllowlistAdminPagesTest`
  and the others stay green. *Accept:* screenshots at both sizes and both themes on the PR; no
  `brand-\d`/`stone-` class left in the item's own templates.
- [ ] **R6 Theme preference.** Cookie `mm_theme` (UI-D8) read in `ViewContextAdvice`, the
      `data-theme` value in `base.jte`'s `<html>` tag, a theme control in `layout/_topbar` (avatar
      menu) and `layout/_sidebar`, the Alpine `theme` component; confirm the cookie-versus-DB choice
      with the owner first. *Accept:* no flash of the wrong theme on reload; the choice survives a
      restart of the app.

**Wave 5: after Wave 4.**

- [ ] **R5d Remove the old styles.** Delete the `brand-50/100/600/700/800` scale from `app.css` and
      extend `UiGuardTest` to fail on a leftover `brand-\d`/`stone-` class. Last, because it would
      fail every page that is not restyled yet, including Phase 2 pages still in review.
- [ ] **R8 Docs and rules: final pass.** Every item updates its own part of
      `.claude/rules/web-ui.md` and `documentation/Current-State-Frontend.md` in its PR (the docs gate
      wants that anyway); R8 reads them as a whole, then updates `Roadmap-Frontend.md`,
      `Newcomer-Guide.md` (where it describes templates) and `README.md`, and moves the "Phase 0
      rest" and "switcher in the header" items from the roadmap to current state.
- [ ] **R9 Close-out** *(owner, after R5d and R8).* Lighthouse accessibility and PWA run on a phone,
      the PWA installed on a real phone, an owner walk-through on a phone and a laptop in both
      themes, a `repo-audit`-style check for leftovers, final log row.

Critical path: R1 → R2a → (R2b ∥ R2c ∥ R3) → R4 → (R5a ∥ R5b ∥ R5c ∥ R6) → R5d → R9: six merge
steps where the single list had nine, and R8 and the whole backend track (`PLAN.md` §9) run
beside them.

### 7.1 Working in parallel with subagents

**Who does what.** The session the owner talks to is the **coordinator**. For each wave it:

1. files the wave's sub-issues with the `new-issue` skill as children of #57, before starting
   anyone, so the numbers exist and two agents never file or edit the board at the same time;
2. starts one subagent per item with `isolation: "worktree"` (its own checkout and branch
   `feat/<issue>-…`), and gives it the issue number, the item text from this section, the list of
   files it owns (table below) and the files to read first: `CLAUDE.md`, this file,
   `.claude/rules/web-ui.md`, `.claude/rules/tests.md`, `.claude/context/workflow.md` and
   `pr-checklist.md`;
3. lets each subagent run the gates and open its PR through `create-pr`, but **merges them itself,
   one at a time, in the order the wave gives** (R1 before R2a). This is still D22 (an agent
   merges planned work when the checks are green); one merger avoids two PRs landing on top of
   each other unchecked (see "Merging" below);
4. ticks the items, adds the log rows and starts the next wave.

**One writer per file per wave.** A subagent edits only the files its item owns. If it needs a
change in a file another item owns (say R2b needs a new icon), it reports that and the coordinator
adds it to the owner's PR or to the next wave. Ownership:

| File or area | W1 | W2 | W3 | W4 | W5 |
|---|---|---|---|---|---|
| `src/main/css/app.css` | R1 | — | R4 (shell classes, if any) | — | R5d |
| `layout/base.jte` | R1 (`<head>`, `<html>`) | — | R4 (`<body>`) | R6 (`data-theme` value) | — |
| `layout/_*` (shell) | — | — | R4 | R6 (theme control only) | — |
| `static/js/app.js` | — | R3 (`sheet`) | R4 (`shell`) | R6 (`theme`) | — |
| `ui/_icon`, `_button`, `_fab` | R2a | — | — | — | — |
| `ui/` other components | — | R2b / R2c / R3, as listed | — | — | — |
| `dev/ui.jte` / `dev/_<section>.jte` | R2a / stubs | each its own section | R4 (`dev/_shell.jte`, if needed) | — | — |
| `ViewContext`, `ViewContextAdvice` | — | — | R4 | R6 | — |
| `static/fonts/`, `sw.js` | R1 | — | — | — | — |
| `manifest.webmanifest`, `PwaAssetsTest` | R1 | — | — | — | — |
| `static/icons/`, `scripts/generate-icons.py` | done (PR #61); re-run the script only if the artwork changes | — | — | — | — |
| page templates (`login`, `error`, `hello`, `household`, `admin`) | — | — | — | R5a / R5b / R5c | — |
| `UiGuardTest` | R2a (if needed) | — | R4 (`aria-label` check) | — | R5d |

**Files everyone touches** are the message bundles, `PLAN.md`, this file and the docs pages. They
are add-only for workers, which keeps conflicts small:

- **Message bundles:** keys are grouped by prefix with a blank line between groups. Add new keys to
  the group of their prefix, or a new group for a new prefix (`ui.*`, `sheet.*`, `nav.*`,
  `theme.*`), never at the end of the file. A conflict is resolved by keeping both sides; the
  bundle-parity test catches mistakes.
- **Checklists and progress logs:** the coordinator ticks items and adds log rows, in the PR it is
  about to merge (a commit on the subagent's branch), so two PRs never append to the same table.
- **Docs pages:** each item writes its own paragraph in `web-ui.md` and
  `Current-State-Frontend.md`; on a conflict keep both and let R8 tidy.

**Local checks.** Each subagent runs `task lint` and `task test` in its worktree (Testcontainers
picks free ports, so these run side by side). `task test-e2e` does not: `compose.e2e.yml` binds
the fixed ports 18080 and 18081, so only one worktree at a time can run it. Subagents leave E2E to
the `E2E` job of their PR's CI, or the coordinator runs them one after the other. For screenshots
with `task serve`, give each worktree its own port: `SERVER_PORT=8081 task serve`.

**Merging.** Branch protection does not require a branch to be up to date with `main` before it
merges (`strict: false`), so a PR that was green against an older `main` could merge on top of a
change it never ran with (a removed icon, a renamed key). Before each merge the coordinator
rebases the branch on `origin/main` if `main` moved since its CI ran, pushes with
`--force-with-lease` and waits for green again. Every code merge also releases (#51), so a wave of
four merges is four patch releases; that is expected.

**How many at once.** At most five subagents at once, counting the backend track of `PLAN.md` §9
(Wave 1 has four: R1, R2a, #59 and B2). More would mostly
add rebases and CI queueing, not speed.

**Adoption rule for `PLAN.md` Phases 2–5:** every new page uses the `ui/` fragments, the shell and
the tokens, adds a section to the `/dev/ui` page for any new component, and follows the phone and
desktop behaviour in §4–§5; a new component starts in the design-system artifact first (owner
decides), then here. Do not start Phase 3's week view before R1–R4 land, or it will be built twice.
The Phase 2 and 3 *backend* (module, migrations, services, tests; no controllers or templates) does
not depend on the redesign and runs beside it (`PLAN.md` §9).

---

## 8. Screens (the target for each page)

- **Sign-in:** full-height calm page, the wizard brand mark (UI-D11) with two sparkles, the
  wordmark in `display`, one tagline, one large primary "Sign in with Google" button, a quiet line
  that only invited people can enter; language switch top right. A refused account gets the same page
  with a `danger` alert ("This account is not on the invite list. Ask the household owner.").
- **Households (restyled, today's pages):** list rows with name, role badge, *Current* badge + check,
  Members link or Switch button; cards for **Invite someone** (email, expiry, Create invite) and
  **Rename household**; members page as a row list with avatars; confirmations are dialogs; the join
  page is a centred card. On phone the blocks stack and the top-bar switcher replaces the Switch
  buttons.
- **Plan (Phase 3):** §5 "Week (desktop)" and "Week (phone)". Fixed slot order; today in gold;
  conflict banner; Quick add; copy day/week from the page header (Phase 4).
- **Meals (Phase 2):** search, tag chips, cards with slot chip, name, tags, use count; quick-add bar;
  detail in a side panel (desktop) or a page (phone); empty library = EmptyState.
- **Occasions (Phase 4) and rating (Phase 5):** §5 "Occasions" and "Rating"; at most one rating
  prompt at a time, dismissible for a day.
- **Admin allowlist (Phase 1, #45):** same list-and-card pattern, instance admins only.

---

## 9. Verification (per phase)

- **Every UI PR:** `task lint`, `task test`, `task test-e2e`; screenshots at 375×812 and 1280×800 in
  Moonlight and Midnight attached to the PR; `PLAN.md`-style docs diff (rule file, Current-State page).
- **R1:** the contrast test passes and fails on a bad token; both themes switch.
- **R2a–R3:** the `/dev/ui` page shows every component and state; keyboard e2e for sheet and dialog.
- **R4/R5a–R5d:** phone and desktop e2e on sign-in and households; no `brand-\d`/`stone-` left
  (R5d's guard); the existing household and admin tests untouched and green.
- **R6:** reload and restart keep the theme, no flash.
- **R9:** Lighthouse (phone) accessibility ≥ 95 and installable; owner walk-through on a real phone
  and a laptop in both themes.

---

## 10. Start here (next agent)

1. Read `CLAUDE.md`, `PLAN.md` §2 and §9, `.claude/rules/web-ui.md`, `.claude/context/workflow.md`
   and `pr-checklist.md`, then this file (§2, §3, §6, §7).
2. The epic is **#57**. Work goes in **waves** (§7): the coordinator files the wave's sub-issues
   with the `new-issue` skill as children of #57, then starts one worktree subagent per item
   (§7.1). Working alone instead of with subagents is fine too: take the items in wave order.
3. **Wave 1 now:** R1 and R2a (merged after R1), beside the backend items in `PLAN.md` §9.
   Then Wave 2 (R2b, R2c, R3), Wave 3 (R4 alone), Wave 4 (R5a, R5b, R5c, R6 and the Phase 2
   pages), Wave 5 (R5d, R8, then R9). Two writers never touch the same file (§7.1 table).
4. Every UI PR: tokens and components from this file, copy through `messages_hu/en.properties`, no
   inline styles or scripts, tests (100 % coverage), screenshots, docs diff.
5. Do not change `PLAN.md` decisions here. A UI-D change is an amendment in §2
   (`*(Amended YYYY-MM-DD; was …)*`).

---

## 11. Progress log

| Date | What | Refs |
|---|---|---|
| 2026-11-01 | Design agreed with the owner in a design-system artifact (two drafts: the first, warm cream/green, was rejected as "brownish"; the second is the violet/gold storybook look). This plan written; no code changed. | — |
| 2026-11-01 | Epic #57 filed and made the next item in `PLAN.md` (before Phase 2); sub-issues are filed as each item starts. | #57 |
| 2026-11-01 | UI-D11: the wizard is the brand mark inside the app too (owner); §3.4 and §8 updated. | #57, #61 |
| 2026-11-01 | §7 reorganised into five waves for parallel subagents at the owner's request: R2 split into R2a/R2b/R2c and R5 into R5a–R5d (the admin page from #45 added as R5c), R7 ticked (icons landed in PR #61) and its rest (manifest colours, `theme-color` metas, `sw.js` v2) moved to R1, §6.2 amended for the wizard icon, the `shell`/`theme` Alpine components moved from R3 to R4/R6, a file-ownership table and the coordinator rules (§7.1). R0 ticked. Docs only. | #57 |
| 2026-11-01 | Wave 1 started (D24): sub-issues #64 (R1) and #65 (R2a) filed under #57. **R1 landed** (#64, PR #69): Appendix A tokens in `app.css`, `data-theme` and the light/dark `theme-color` metas, self-hosted Fredoka and Nunito, `sw.js` v2, the manifest colours and `ContrastTokensTest` (all 53 Appendix C pairs in both themes, and it fails on a bad token). **The owner decided** what to do about two font findings: Fredoka has no Latin Extended-A glyphs (no ő/ű), so headings take those letters from **Nunito** (Fredoka stays the display font, not Baloo 2; fixed in #71); neither font has `tnum`, so there are no tabular numerals (UI-D4 and §3.3 amended). | #57, #64, #69, #71 |
| 2026-11-01 | **R2a landed** (#65, PR #70): `ui/_icon` (30 icons, `IconName` enum), `ui/_button` (4 variants, 4 sizes, busy/disabled), `ui/_fab`, the dev-only `/dev/ui` page with the empty `_content`, `_forms` and `_overlays` stubs, and `HtmlAttributes` for extra attributes (JTE has no `@for` in attribute-name position). R1's E2E failure (the login page's font preloads were saved as the post-sign-in redirect because `/fonts` was not in `PUBLIC_PATHS`) was fixed in PR #69, with a test that requests every static file anonymously. | #57, #65, #69, #70 |

---

## Appendix A — CSS to add to `src/main/css/app.css`

Raw per-theme variables first, then the Tailwind mapping. Generated from the design-system tokens;
if you change a value, change the contrast test's expectations (Appendix C) with it.

```css
/* --- Meal Magician design tokens (plan-ui-redesign.md) -------------------------------------- */

/* Moonlight (light) is the default. */
:root {
  --mm-surface: #f8f5ff;
  --mm-surface-raised: #ffffff;
  --mm-surface-sunken: #ece5fb;
  --mm-border: #d9cff2;
  --mm-border-strong: #7a6bb0;
  --mm-outline: #24174d;
  --mm-ink: #24174d;
  --mm-ink-muted: #5b4f85;
  --mm-brand: #6d3fe0;
  --mm-brand-hover: #5a2dc4;
  --mm-on-brand: #ffffff;
  --mm-brand-soft: #e8defc;
  --mm-brand-ink: #4a22a8;
  --mm-accent: #ffc83d;
  --mm-on-accent: #24174d;
  --mm-accent-soft: #fff0c2;
  --mm-accent-ink: #6b4300;
  --mm-slot-breakfast: #b86e00;
  --mm-slot-breakfast-soft: #ffe9a8;
  --mm-slot-breakfast-ink: #6b4300;
  --mm-slot-lunch: #0a8a67;
  --mm-slot-lunch-soft: #c9f5e4;
  --mm-slot-lunch-ink: #0b5a44;
  --mm-slot-dinner: #2f64e6;
  --mm-slot-dinner-soft: #d5e4ff;
  --mm-slot-dinner-ink: #17398f;
  --mm-slot-snack: #d12a8f;
  --mm-slot-snack-soft: #ffd6ee;
  --mm-slot-snack-ink: #8a1360;
  --mm-success-soft: #c9f5e4;
  --mm-success-ink: #0b5a44;
  --mm-warning-soft: #fff0c2;
  --mm-warning-ink: #6b4300;
  --mm-danger: #c01a42;
  --mm-on-danger: #ffffff;
  --mm-danger-soft: #ffd9e0;
  --mm-danger-ink: #8a0f2e;
  --mm-info-soft: #d5e4ff;
  --mm-info-ink: #17398f;
  --mm-focus-ring: #0b5bd3;
  --mm-scrim: #24174dcc;
  --mm-shadow-1: 0 2px 0 #24174d;
  --mm-shadow-2: 3px 3px 0 #24174d;
  --mm-shadow-3: 5px 5px 0 #24174d;
  --mm-touch: 44px;
  --mm-bottom-bar: 64px;
  --mm-sidebar: 248px;
  --mm-sidebar-collapsed: 72px;
  --mm-content: 1200px;
  --mm-reading: 720px;
  --mm-duration-fast: 120ms;
  --mm-duration-base: 200ms;
  --mm-duration-slow: 320ms;
}

/* Midnight (dark), chosen explicitly. */
:root[data-theme="dark"] {
  --mm-surface: #130c2e;
  --mm-surface-raised: #1d1442;
  --mm-surface-sunken: #0d0820;
  --mm-border: #34296b;
  --mm-border-strong: #8f86c8;
  --mm-outline: #9b8cf0;
  --mm-ink: #f4efff;
  --mm-ink-muted: #b9b0e0;
  --mm-brand: #a98bff;
  --mm-brand-hover: #c1a9ff;
  --mm-on-brand: #1a0b4d;
  --mm-brand-soft: #2b1d66;
  --mm-brand-ink: #d4c6ff;
  --mm-accent: #ffd04d;
  --mm-on-accent: #24174d;
  --mm-accent-soft: #4a3a0f;
  --mm-accent-ink: #ffe08a;
  --mm-slot-breakfast: #ffc247;
  --mm-slot-breakfast-soft: #4a3710;
  --mm-slot-breakfast-ink: #ffe3a0;
  --mm-slot-lunch: #4fe0b3;
  --mm-slot-lunch-soft: #0f4a3a;
  --mm-slot-lunch-ink: #b5f5dc;
  --mm-slot-dinner: #7aa8ff;
  --mm-slot-dinner-soft: #1a2f6b;
  --mm-slot-dinner-ink: #c3d6ff;
  --mm-slot-snack: #ff7cc4;
  --mm-slot-snack-soft: #5a1a44;
  --mm-slot-snack-ink: #ffc2e3;
  --mm-success-soft: #0f4a3a;
  --mm-success-ink: #b5f5dc;
  --mm-warning-soft: #4a3710;
  --mm-warning-ink: #ffe3a0;
  --mm-danger: #ff7d96;
  --mm-on-danger: #3a0612;
  --mm-danger-soft: #591527;
  --mm-danger-ink: #ffc2cd;
  --mm-info-soft: #1a2f6b;
  --mm-info-ink: #c3d6ff;
  --mm-focus-ring: #ffd04d;
  --mm-scrim: #05020fb3;
  --mm-shadow-1: 0 2px 0 #05020f;
  --mm-shadow-2: 3px 3px 0 #05020f;
  --mm-shadow-3: 5px 5px 0 #05020f;
}

/* Midnight when the user has not chosen (theme "system"). */
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]):not([data-theme="dark"]) {
    --mm-surface: #130c2e;
    --mm-surface-raised: #1d1442;
    --mm-surface-sunken: #0d0820;
    --mm-border: #34296b;
    --mm-border-strong: #8f86c8;
    --mm-outline: #9b8cf0;
    --mm-ink: #f4efff;
    --mm-ink-muted: #b9b0e0;
    --mm-brand: #a98bff;
    --mm-brand-hover: #c1a9ff;
    --mm-on-brand: #1a0b4d;
    --mm-brand-soft: #2b1d66;
    --mm-brand-ink: #d4c6ff;
    --mm-accent: #ffd04d;
    --mm-on-accent: #24174d;
    --mm-accent-soft: #4a3a0f;
    --mm-accent-ink: #ffe08a;
    --mm-slot-breakfast: #ffc247;
    --mm-slot-breakfast-soft: #4a3710;
    --mm-slot-breakfast-ink: #ffe3a0;
    --mm-slot-lunch: #4fe0b3;
    --mm-slot-lunch-soft: #0f4a3a;
    --mm-slot-lunch-ink: #b5f5dc;
    --mm-slot-dinner: #7aa8ff;
    --mm-slot-dinner-soft: #1a2f6b;
    --mm-slot-dinner-ink: #c3d6ff;
    --mm-slot-snack: #ff7cc4;
    --mm-slot-snack-soft: #5a1a44;
    --mm-slot-snack-ink: #ffc2e3;
    --mm-success-soft: #0f4a3a;
    --mm-success-ink: #b5f5dc;
    --mm-warning-soft: #4a3710;
    --mm-warning-ink: #ffe3a0;
    --mm-danger: #ff7d96;
    --mm-on-danger: #3a0612;
    --mm-danger-soft: #591527;
    --mm-danger-ink: #ffc2cd;
    --mm-info-soft: #1a2f6b;
    --mm-info-ink: #c3d6ff;
    --mm-focus-ring: #ffd04d;
    --mm-scrim: #05020fb3;
    --mm-shadow-1: 0 2px 0 #05020f;
    --mm-shadow-2: 3px 3px 0 #05020f;
    --mm-shadow-3: 5px 5px 0 #05020f;
  }
}

@media (prefers-reduced-motion: reduce) {
  :root {
    --mm-duration-fast: 0ms;
    --mm-duration-base: 0ms;
    --mm-duration-slow: 0ms;
  }
}

/* Tailwind utilities: bg-surface, text-ink, border-outline, bg-brand, text-slot-ink, shadow-sticker-2, ... */
@theme inline {
  --color-surface: var(--mm-surface);
  --color-surface-raised: var(--mm-surface-raised);
  --color-surface-sunken: var(--mm-surface-sunken);
  --color-border: var(--mm-border);
  --color-border-strong: var(--mm-border-strong);
  --color-outline: var(--mm-outline);
  --color-ink: var(--mm-ink);
  --color-ink-muted: var(--mm-ink-muted);
  --color-brand: var(--mm-brand);
  --color-brand-hover: var(--mm-brand-hover);
  --color-on-brand: var(--mm-on-brand);
  --color-brand-soft: var(--mm-brand-soft);
  --color-brand-ink: var(--mm-brand-ink);
  --color-accent: var(--mm-accent);
  --color-on-accent: var(--mm-on-accent);
  --color-accent-soft: var(--mm-accent-soft);
  --color-accent-ink: var(--mm-accent-ink);
  --color-slot-breakfast: var(--mm-slot-breakfast);
  --color-slot-breakfast-soft: var(--mm-slot-breakfast-soft);
  --color-slot-breakfast-ink: var(--mm-slot-breakfast-ink);
  --color-slot-lunch: var(--mm-slot-lunch);
  --color-slot-lunch-soft: var(--mm-slot-lunch-soft);
  --color-slot-lunch-ink: var(--mm-slot-lunch-ink);
  --color-slot-dinner: var(--mm-slot-dinner);
  --color-slot-dinner-soft: var(--mm-slot-dinner-soft);
  --color-slot-dinner-ink: var(--mm-slot-dinner-ink);
  --color-slot-snack: var(--mm-slot-snack);
  --color-slot-snack-soft: var(--mm-slot-snack-soft);
  --color-slot-snack-ink: var(--mm-slot-snack-ink);
  --color-success-soft: var(--mm-success-soft);
  --color-success-ink: var(--mm-success-ink);
  --color-warning-soft: var(--mm-warning-soft);
  --color-warning-ink: var(--mm-warning-ink);
  --color-danger: var(--mm-danger);
  --color-on-danger: var(--mm-on-danger);
  --color-danger-soft: var(--mm-danger-soft);
  --color-danger-ink: var(--mm-danger-ink);
  --color-info-soft: var(--mm-info-soft);
  --color-info-ink: var(--mm-info-ink);
  --color-focus-ring: var(--mm-focus-ring);
  --color-scrim: var(--mm-scrim);
  --shadow-sticker-1: var(--mm-shadow-1);
  --shadow-sticker-2: var(--mm-shadow-2);
  --shadow-sticker-3: var(--mm-shadow-3);
  --color-slot: var(--slot);
  --color-slot-soft: var(--slot-soft);
  --color-slot-ink: var(--slot-ink);
}

/* Not themed: shapes. Spacing needs nothing (Tailwind's 4 px scale matches). */
@theme {
  --radius-sm: 10px;
  --radius-md: 14px;
  --radius-lg: 20px;
  --radius-xl: 28px;
}

/* Not themed: fonts. `static` writes the variables out even when no utility uses them yet. */
@theme static {
  --font-display: "Fredoka", "Nunito", "Trebuchet MS", system-ui, sans-serif;
  --font-sans: "Nunito", system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
}

/* One set of utilities for the four meal types: bg-slot-soft text-slot-ink ... */
@layer components {
  .slot-breakfast { --slot: var(--mm-slot-breakfast); --slot-soft: var(--mm-slot-breakfast-soft); --slot-ink: var(--mm-slot-breakfast-ink); }
  .slot-lunch { --slot: var(--mm-slot-lunch); --slot-soft: var(--mm-slot-lunch-soft); --slot-ink: var(--mm-slot-lunch-ink); }
  .slot-dinner { --slot: var(--mm-slot-dinner); --slot-soft: var(--mm-slot-dinner-soft); --slot-ink: var(--mm-slot-dinner-ink); }
  .slot-snack { --slot: var(--mm-slot-snack); --slot-soft: var(--mm-slot-snack-soft); --slot-ink: var(--mm-slot-snack-ink); }
}

@layer base {
  /* Keep the existing [x-cloak] rule. The focus ring changes colour and is blue/gold per theme. */
  :focus-visible {
    outline: 3px solid var(--mm-focus-ring);
    outline-offset: 2px;
  }
  body {
    background-color: var(--mm-surface);
    background-image: radial-gradient(var(--mm-border) 1.5px, transparent 1.5px);
    background-size: 28px 28px;
    color: var(--mm-ink);
  }
}
```

## Appendix B — Icons

All on a `viewBox="0 0 24 24"`, `fill="none"`, `stroke="currentColor"`, `stroke-width="2"`,
`stroke-linecap="round"`, `stroke-linejoin="round"`. The element content for each name:

| Name | SVG content |
|---|---|
| `calendar` | `<rect x="3" y="5" width="18" height="16" rx="3"/><path d="M8 3v4M16 3v4M3 10h18"/>` |
| `meals` | `<path d="M6 3v7a3 3 0 0 0 6 0V3M9 3v18"/><path d="M17 21V3c-2 1-4 4-4 8 0 2 1 3 4 3"/>` |
| `gift` | `<rect x="3" y="8" width="18" height="4" rx="1"/><path d="M12 8v13M5 12v8a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-8M7.5 8a2.5 2.5 0 0 1 0-5C10 3 12 8 12 8s2-5 4.5-5a2.5 2.5 0 0 1 0 5"/>` |
| `home` | `<path d="M3 11l9-8 9 8M5 10v10h14V10"/>` |
| `plus` | `<path d="M12 5v14M5 12h14"/>` |
| `check` | `<path d="M5 12l5 5 9-10"/>` |
| `left` | `<path d="M15 6l-6 6 6 6"/>` |
| `right` | `<path d="M9 6l6 6-6 6"/>` |
| `down` | `<path d="M6 9l6 6 6-6"/>` |
| `breakfast` | `<path d="M4 9h13v5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5V9zM17 10h2a2 2 0 0 1 0 4h-2M8 3v3M12 3v3"/>` |
| `lunch` | `<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>` |
| `dinner` | `<path d="M21 13a9 9 0 1 1-10-10 7 7 0 0 0 10 10z"/>` |
| `snack` | `<path d="M12 7c-2-2-6-1-6 4 0 5 3 9 6 9s6-4 6-9c0-5-4-6-6-4zM12 7c0-2 1-3 3-4"/>` |
| `search` | `<circle cx="11" cy="11" r="7"/><path d="M20 20l-4-4"/>` |
| `x` | `<path d="M6 6l12 12M18 6L6 18"/>` |
| `alert` | `<path d="M12 3l10 18H2L12 3zM12 10v5M12 18v.01"/>` |
| `info` | `<circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8v.01"/>` |
| `up` | `<path d="M7 11v9H4v-9h3zM7 11l4-8c1.5 0 2.5 1 2.5 2.5V9H19a2 2 0 0 1 2 2.3l-1.3 7A2 2 0 0 1 17.7 20H7"/>` |
| `star` | `<path d="M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9L12 3z"/>` |
| `user` | `<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>` |
| `more` | `<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>` |
| `copy` | `<rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h8"/>` |
| `sidebar` | `<rect x="3" y="4" width="18" height="16" rx="3"/><path d="M9 4v16"/>` |
| `logout` | `<path d="M10 4H5a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h5M15 8l4 4-4 4M19 12H9"/>` |
| `clock` | `<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>` |
| `edit` | `<path d="M4 20l1-4L16 5l3 3L8 19l-4 1zM14 7l3 3"/>` |
| `trash` | `<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>` |
| `sparkle` | `<path d="M12 2l2.2 7.8L22 12l-7.8 2.2L12 22l-2.2-7.8L2 12l7.8-2.2L12 2z"/>` |
| `wand` | `<path d="M4 20L15 9M15 9l2-2 3 3-2 2-3-3zM6 4v3M4.5 5.5h3M19 15v3M17.5 16.5h3"/>` |
| `sparkles` | `<path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3z"/>` |

`sparkle` is also used **filled** (`fill="currentColor"`, with a 1.5 px outline-coloured stroke).
`up` rotated 180° is the thumbs-down.

## Appendix C — Contrast pairs the test must check (both themes)

`foreground on background ≥ ratio` (WCAG 2). Text pairs need 4.5, marks and borders 3. All pass today.

| Foreground on background | Min | Moonlight | Midnight |
|---|---|---|---|
| `ink` on `surface` | 4.5 | 14.94 | 16.64 |
| `ink-muted` on `surface` | 4.5 | 6.72 | 9.24 |
| `border-strong` on `surface` | 3 | 4.28 | 5.73 |
| `outline` on `surface` | 3 | 14.94 | 6.61 |
| `focus-ring` on `surface` | 3 | 5.65 | 12.84 |
| `danger` on `surface` | 4.5 | 5.62 | 7.70 |
| `ink` on `surface-raised` | 4.5 | 16.10 | 15.14 |
| `ink-muted` on `surface-raised` | 4.5 | 7.24 | 8.41 |
| `border-strong` on `surface-raised` | 3 | 4.61 | 5.21 |
| `outline` on `surface-raised` | 3 | 16.10 | 6.01 |
| `focus-ring` on `surface-raised` | 3 | 6.08 | 11.68 |
| `danger` on `surface-raised` | 4.5 | 6.06 | 7.01 |
| `ink` on `surface-sunken` | 4.5 | 13.16 | 17.37 |
| `ink-muted` on `surface-sunken` | 4.5 | 5.92 | 9.65 |
| `border-strong` on `surface-sunken` | 3 | 3.77 | 5.98 |
| `outline` on `surface-sunken` | 3 | 13.16 | 6.90 |
| `focus-ring` on `surface-sunken` | 3 | 4.97 | 13.41 |
| `danger` on `surface-sunken` | 4.5 | 4.95 | 8.04 |
| `brand` on `surface` | 4.5 | 5.67 | 6.99 |
| `brand` on `surface-raised` | 4.5 | 6.11 | 6.36 |
| `on-brand` on `brand` | 4.5 | 6.11 | 6.53 |
| `on-brand` on `brand-hover` | 4.5 | 8.05 | 8.66 |
| `brand-ink` on `brand-soft` | 4.5 | 7.81 | 9.06 |
| `on-accent` on `accent` | 4.5 | 10.41 | 11.02 |
| `accent-ink` on `accent-soft` | 4.5 | 7.62 | 8.55 |
| `on-danger` on `danger` | 4.5 | 6.06 | 7.10 |
| `danger-ink` on `danger-soft` | 4.5 | 7.43 | 8.87 |
| `success-ink` on `success-soft` | 4.5 | 6.90 | 8.28 |
| `warning-ink` on `warning-soft` | 4.5 | 7.62 | 9.08 |
| `info-ink` on `info-soft` | 4.5 | 8.11 | 8.65 |
| `ink` on `brand-soft` | 4.5 | 12.48 | 12.68 |
| `ink` on `accent-soft` | 4.5 | 14.18 | 9.79 |
| `ink-muted` on `brand-soft` | 4.5 | 5.61 | 7.04 |
| `slot-breakfast-ink` on `slot-breakfast-soft` | 4.5 | 7.20 | 9.08 |
| `slot-breakfast` on `surface-raised` | 3 | 3.99 | 10.61 |
| `slot-breakfast` on `slot-breakfast-soft` | 2.5 | 3.32 | 7.08 |
| `ink` on `slot-breakfast-soft` | 4.5 | 13.39 | 10.10 |
| `outline` on `slot-breakfast-soft` | 3 | 13.39 | 4.01 |
| `slot-lunch-ink` on `slot-lunch-soft` | 4.5 | 6.90 | 8.28 |
| `slot-lunch` on `surface-raised` | 3 | 4.34 | 10.27 |
| `slot-lunch` on `slot-lunch-soft` | 2.5 | 3.65 | 6.14 |
| `ink` on `slot-lunch-soft` | 4.5 | 13.54 | 9.04 |
| `outline` on `slot-lunch-soft` | 3 | 13.54 | 3.59 |
| `slot-dinner-ink` on `slot-dinner-soft` | 4.5 | 8.11 | 8.65 |
| `slot-dinner` on `surface-raised` | 3 | 5.14 | 7.21 |
| `slot-dinner` on `slot-dinner-soft` | 2.5 | 4.01 | 5.34 |
| `ink` on `slot-dinner-soft` | 4.5 | 12.55 | 11.21 |
| `outline` on `slot-dinner-soft` | 3 | 12.55 | 4.45 |
| `slot-snack-ink` on `slot-snack-soft` | 4.5 | 6.87 | 8.45 |
| `slot-snack` on `surface-raised` | 3 | 4.73 | 7.26 |
| `slot-snack` on `slot-snack-soft` | 2.5 | 3.63 | 5.36 |
| `ink` on `slot-snack-soft` | 4.5 | 12.34 | 11.19 |
| `outline` on `slot-snack-soft` | 3 | 12.34 | 4.44 |

The slot mark on its own soft tile (`slot-<type>` on `slot-<type>-soft`, minimum 2.5) is decoration next to a text label, so it is allowed below 3. `accent` is a fill only and has no text pair.
