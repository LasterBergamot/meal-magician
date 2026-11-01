# Roadmap: Frontend

Planned UI work (JTE + htmx + Alpine + Tailwind), by `PLAN.md` phase. Move items to
[[Current State: Frontend]] when they land.

- **UI redesign (all phases):** a storybook look (violet, gold, outlined "sticker" shapes, light and dark
  themes), a bottom tab bar on phones and a sidebar on desktop, a shared component set and a restyle of
  every existing page. It is planned in `plan-ui-redesign.md` (phases R0–R9) in the repository root;
  build the Phase 2–5 pages from its components.
- **Phase 0 (rest):** a sign-out button in the header and applying the stored per-user language
  (`app_user.locale` exists, nothing uses it yet); real PWA artwork comes in Phase 5.
- **Phase 1:** household create/switch/rename landed in #41, the invite section and join page in
  #42, the members page in #43 and the allowlist admin page in #45; a switcher inside the header
  and a "copy link" button are still open.
- **Phase 2:** meal library list/search, meal form, phone quick-add.
- **Phase 3:** week view (grid on desktop, day list on phone), slot editing, week navigation,
  conflict messages.
- **Phase 4:** copy day/week dialogs, template picker, occasion pages.
- **Phase 5:** rating prompts, print view, share link page, PWA polish (icons, install hint).
- **Phase 7:** "suggest my week" and chat-style refinement UI.
