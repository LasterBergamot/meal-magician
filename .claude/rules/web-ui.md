---
paths:
  - "src/main/jte/**"
  - "src/main/resources/static/**"
  - "src/main/resources/messages*.properties"
  - "tailwind.config.js"
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
- **Tailwind** via the standalone CLI (`task serve` watches; `task build` minifies). The output
  `static/css/app.css` is generated: don't edit it.
- **i18n (D14):** every visible string uses `t("key")` from `messages_hu.properties` (default)
  and `messages_en.properties`; both files must have the same keys (a test checks it, and
  another fails on hard-coded text in templates). Dates via the locale's formatter; weeks start
  Monday.
- **PWA (D5):** `manifest.webmanifest`, icons (192/512, maskable), `sw.js` that only caches
  static assets — no offline data, never cache HTML pages with user data.
- Accessibility: semantic HTML, labels on inputs, visible focus, sufficient contrast.
