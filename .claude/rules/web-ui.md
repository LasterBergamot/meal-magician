---
paths:
  - "src/main/jte/**"
  - "src/main/resources/static/**"
  - "src/main/resources/messages*.properties"
  - "src/main/css/**"
---

# Web UI: JTE, htmx, Alpine, Tailwind, i18n, PWA

> A redesign (tokens, `ui/` components, app shell, themes, fonts) is planned in
> `plan-ui-redesign.md`. Until its phases land, the rules below apply as written; the plan's R8
> rewrites this file to match what was built.

- **Mobile-first.** Design for a ~375 px phone first, then enhance for tablet/desktop
  (`md:`/`lg:`). The week view is a list of days on phone and a grid on desktop. Touch targets ≥ 44 px.
- **JTE** templates are precompiled; layouts in `src/main/jte/layout/`, one folder per module,
  fragments named `_*.jte`. Pass typed view-model records, never entities.
- **htmx** for interactivity: forms post with `hx-post`, the server returns the updated fragment;
  use `hx-target`/`hx-swap` explicitly. Show conflicts (optimistic locking) and validation
  errors inside the fragment. No JSON APIs for the UI.
- **Alpine.js (CSP build)** only for local UI state (menus, dialogs, toggles). No inline
  `<script>` and no inline event handlers — the CSP forbids them.
- **Tailwind v4** via the standalone CLI, configured in CSS (`src/main/css/app.css`: `@theme`,
  `@source`; no `tailwind.config.js`). `task serve` watches; the Gradle build (`tailwindBuild`)
  minifies. The output `static/css/app.css` is generated and ignored by Git: don't edit it.
  Class names are only found in `src/main/jte` and `static/js/app.js`, so write them out in
  full (no `"bg-" + color`).
- **Colours and fonts (R1, #64):** use the design tokens (`bg-surface`, `text-ink`,
  `border-outline`, `bg-brand`, `text-slot-ink`, `shadow-sticker-2`), never a raw palette class
  such as `stone-*` or `green-*` in new code. The `--mm-*` values live in `app.css`, per theme,
  and `ContrastTokensTest` checks every Appendix C pair in both themes, so a new token pair
  needs a row there. Fonts are self-hosted in `static/fonts/` (see its `FONTS.md`); a new font
  file needs its licence text and hash there, and a `@font-face` in `app.css`. Headings use
  `--font-display` = Fredoka, then Nunito (Fredoka lacks ő/ű, Nunito draws them), then the system
  faces; never name a web font there that is not hosted (`FontFallbackTest` checks). Neither font
  has `tnum`, so do not rely on `tabular-nums`. The old
  `brand-*` scale is kept until R5d, so `brand-700` still works for now.
- **Templates and `ViewContext`:** every template declares `@param ViewContext ctx` (translations,
  CSRF token; `ViewContextAdvice` adds it to every model) and wraps pages in
  `@template.layout.base(ctx = ctx, title = ..., content = @`...`)`. A URL that serves a page and
  a fragment returns `Htmx.view(request, response, "x/_fragment", "x/page")` (adds `Vary`).
- **CSP (`SecurityHeadersFilter`):** no inline `<script>`, no `onclick=`-style handlers, no
  `style=` attributes, no CDN. Alpine's CSP build only understands names, so write
  `x-data="menu"` / `x-on:click="toggle"` and register `Alpine.data("menu", ...)` in
  `static/js/app.js`. `UiGuardTest` checks the templates. Vendored scripts and their SHA-256 are
  listed in `static/js/VENDORED.md`.
- **Form posts:** success ends with `Htmx.redirect(request, response, url)` (`HX-Redirect` for htmx,
  `303` otherwise; never a plain 302, which htmx would swap into the form). A rejected form returns
  its own fragment with 422 (409 for an `@Version` conflict).
- **htmx errors:** error responses are swapped (`htmx-config` meta in the layout); the server
  sends `HX-Retarget: #error-slot`. A 401/403 to htmx sends the whole page to `/` (`app.js`).
- **i18n (D14):** every visible string uses `ctx.t("key")` (`shared.i18n.Messages`) from
  `messages_hu.properties` (default) and `messages_en.properties`; both files must have the same
  keys (a test checks it, and another fails on hard-coded text in templates). Dates via the
  locale's formatter; weeks start Monday. The language is the cookie `mm_lang`, set by
  `POST /language` (`lang=hu|en`, CSRF-protected, public; per user in the DB too, #79 and #83), never
  a GET link; the browser's `Accept-Language` is ignored on purpose. Brand names and `HU`/`EN` come from the bundles too.
- **PWA (D5):** `manifest.webmanifest`, icons (`favicon.svg`, 192/512, maskable; `scripts/generate-icons.py` draws them), `sw.js` that only caches
  static assets — no offline data, never cache HTML pages with user data. The manifest is served
  by `ManifestController` (Tomcat does not know `.webmanifest`).
- **Design-system components (`ui/`, R2a):** `ui/_icon.jte` draws the icons, and its names are
  the enum `shared.web.IconName` (a typo is a compile error; add an icon there and as an
  `@elseif` branch). `ui/_button.jte` takes `variant` (primary, secondary, ghost, danger),
  `size` (sm, md, lg, icon), `busy` (spinner, `aria-busy`, disabled) and `attrs`, a `Map` of
  extra attributes such as `hx-post`; pass `Map.of()` for none. JTE allows no `@for` or `@if` in
  attribute names, so the map goes through `HtmlAttributes.render` (escapes values, refuses odd
  names). Do not pass raw attribute text in a content block: `UiGuardTest` reads it as visible
  text. An icon-only button keeps an `sr-only` label from `ctx.t`. `ui/_fab.jte` is the add
  button (`floating` pins it above the phone's tab bar). Classes are written out in full in
  these fragments. The style guide `/dev/ui` (`shared.web.DevUiController`, dev profile only, not
  in `PUBLIC_PATHS`, so it needs a sign-in) shows every icon and button state; a new component
  adds its section to `dev/ui.jte` or to a `dev/_<section>.jte` fragment, never a second page.
- **Form components (`ui/`, R2c):** `ui/_field.jte` (the label is always visible; `hint` is a
  help sentence; `error` is an already translated message that sets `aria-invalid`, the red
  border and an alert icon), `ui/_select.jte` (a native `<select>` whose `options` are
  `FormOption` records), `ui/_search.jte` (a pill with a visually hidden label and no error
  state) and `ui/_segmented.jte` (a `role="tablist"` of `SegmentOption` links). Never write
  `aria-describedby` or `hx-get` by hand: `HtmlAttributes.describedBy` and
  `HtmlAttributes.htmxFetch` build them, and the fragments pass the result through
  `$unsafe{HtmlAttributes.render(...)}`. An inline conditional inside `$unsafe{...}` in a tag
  did not compile in JTE 3.2 (the parser rejected it), so put the condition in a helper. A rejected form returns the same fragment with 422,
  the message in `error` and the typed value (or the chosen option) passed back. Callers pass
  translated text from `ctx.t`; the components carry no text of their own. The style guide
  `dev/_forms.jte` shows every state.
- **Content and status components (`ui/`, R2b):** `ui/_badge.jte` (`tone`: neutral, brand, accent,
  success, warning, danger, info; a span, never a control), `ui/_chip.jte` (a filter button with
  `aria-pressed`; selected is a fill plus a check), `ui/_slot_chip.jte` and `ui/_slot_dot.jte`
  (`slot` = `BREAKFAST`, `LUNCH`, `DINNER` or `SNACK`; the word comes from `ctx.t("slot.*")` and
  is passed in as `label`, and a chip always shows its icon). `ui/_card.jte` (`variant` default,
  accent or selected), `ui/_row.jte` (an `li` for a list in a `ul`), `ui/_avatar.jte` (initials
  passed in, decorative; `tone` gold, the default, or `soft` where gold is already spent, as in
  the phone top bar), `ui/_alert.jte` (`kind` success, info, warning or danger; warning and
  danger are `role="alert"`), `ui/_empty.jte` and `ui/_skeleton_rows.jte` (`aria-busy`, a
  screen-reader label). Every fragment takes its texts from the caller (`ctx.t` at the call site),
  so the templates need no `ViewContext`. Use the design tokens only: `ContentComponentsPageTest`
  fails on a raw palette class (`brand-700`, `stone-100`) in these fragments and in
  `dev/_content.jte`, and it reads each state from `/dev/ui`, so add a state to the test when you
  add one there.
- **Sheet and confirm dialog (R3, #75):** `ui/_sheet.jte` is a native `<dialog>` (a bottom sheet
  on phones, a centred dialog from `md`), `ui/_confirm.jte` is the destructive confirmation (always
  a centred `alertdialog`). Both take a `trigger` whose control has `x-on:click="show"` and close
  through `hide`; the Alpine `sheet` component in `app.js` traps Tab, restores focus to the
  opener and handles the phone's swipe-down. Use `_confirm` for anything destructive, never a
  sheet for it. Never open a sheet from a sheet, and keep an open confirm out of any htmx swap
  region. `dev/_overlays.jte` shows both; `SheetAndConfirmE2ETest` drives them from the keyboard.
- **App shell and navigation (R4, #79):** `base.jte` draws `layout/_shell.jte` when `ctx.user()`
  is set (signed-in pages) and the plain anonymous header otherwise. The shell is `_topbar` +
  `_tabbar` on a phone and `_sidebar` on a desktop, both listing `NavDestination.visible()` (a
  destination with no page yet is left out, not dead: flip its `available` flag when its phase
  lands). The current tab comes from `ctx.active()`. The household switcher
  (`layout/_household_switcher.jte`, one per shell) is a `ui/_sheet` of the person's own households;
  each other one posts to `/households/{id}/switch`. Sign-out is a POST form (`layout/_sign_out`)
  with the CSRF token as the hidden field `_csrf`. Links between destinations are plain links, not
  `hx-boost` (see Current State). Labels that collapse with the sidebar use
  `[.is-collapsed_&]:sr-only`; the sidebar state and the tab bar's keyboard hiding are the Alpine
  `shell` component, and the arrow keys of `ui/_segmented` are the Alpine `segmented` component.
  Rules for new pages: no own header or navigation, use the shell's `ctx`; an icon-only button
  needs an `aria-label` or sr-only text (`UiGuardTest.iconOnlyButtonsHaveAnAccessibleName`).
- **Theme preference (R6, #87):** the theme is the cookie `mm_theme` (`light` | `dark` | `system`,
  UI-D8), written only by the browser (the Alpine `theme` component in `app.js`: `Path=/`, a year,
  `SameSite=Lax`, not `HttpOnly`) and only read by the server (`ViewContextAdvice` into
  `ViewContext.theme()`, `shared.web.Theme`, unknown means `system`), so `base.jte` can write
  `<html data-theme>` on first paint with no flash. No server write, so no CSRF question. The
  control is `layout/_theme_switch.jte` (buttons with `data-theme-choice` and `aria-pressed`,
  styled with the `aria-pressed:` variant); it is in the phone avatar menu and the desktop
  sidebar, so the component updates `aria-pressed` on all copies. A new page needs nothing: it uses
  the tokens, which already switch with `data-theme`. The `theme-color` metas get their `media`
  from `Theme.themeColorMedia`. `ThemePreferenceE2ETest` checks the first paint with JS off.
- **Sign-in, error, hello and allowlist pages (R5a, R5c, #85):** built only from the `ui/`
  components and tokens, no `brand-\d`/`stone-` class (`RestyledPageTemplatesTest` pins the four
  folders until R5d). `login/page` and the standalone `error/_message` are one centred `ui/_card`
  outside the shell; the htmx error fragment is a `danger` `ui/_alert`. `ui/_button` renders a
  `<button>`, so a link that looks like a button (sign in, back home) is an `<a>` with the button's
  classes written out. The allowlist remove is a `ui/_confirm` (not `hx-confirm`) whose confirm
  button sends `hx-include="#<id>-email"`, a hidden input beside it, because an address may
  contain a quote and must not be pasted into JSON. The id and the badge tone come from
  `AllowlistRow.domId()` and `badgeTone()`: JTE's `loop` variable does not exist here, and
  `UiGuardTest` reads an inline `!{var ...}` in a template as visible text. The whole `#allowlist` section is the swap target, so a
  successful remove also closes the dialog.
- **Household pages (R5b, #86):** every `household/*` template is built from the `ui/` components
  and tokens, with no `brand-\d`/`stone-` class left. A form fragment (`_create`, `_rename`) keeps
  its `<form>` as the root (htmx swaps it, and tests read `<form id="create-form"`) with
  `class="contents"` and a `ui/_card` inside; do not `scrollIntoView` such a form in a browser test,
  it has no box (use its input). A destructive button (remove a member, revoke an invite, leave,
  hand over) is a `ui/_confirm` with its `hx-post` in `attrs`, never `hx-confirm`; the response swaps
  the section the dialog sits in, which closes it. Give each dialog a unique id
  (`"remove-" + row.userId()`). The 409 of a rename is a `warning` alert with a Reload button, the
  other 4xx are a field error or a `danger` alert. Avatars in a list inside the shell use the `soft`
  tone, because the phone tab bar's current tab is the screen's gold highlight. An empty list in a
  card is a dashed `border-border-strong` box; a whole empty page is `ui/_empty`.
- Accessibility: semantic HTML, labels on inputs, visible focus, sufficient contrast.
