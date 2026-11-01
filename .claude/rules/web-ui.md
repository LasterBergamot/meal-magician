---
paths:
  - "src/main/jte/**"
  - "src/main/resources/static/**"
  - "src/main/resources/messages*.properties"
  - "src/main/css/**"
---

# Web UI: JTE, htmx, Alpine, Tailwind, i18n, PWA

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
  `?lang=hu|en` (per user in the DB from #12); the browser's `Accept-Language` is ignored on
  purpose. Brand names and `HU`/`EN` come from the bundles too.
- **PWA (D5):** `manifest.webmanifest`, icons (192/512, maskable), `sw.js` that only caches
  static assets — no offline data, never cache HTML pages with user data. The manifest is served
  by `ManifestController` (Tomcat does not know `.webmanifest`).
- Accessibility: semantic HTML, labels on inputs, visible focus, sufficient contrast.
