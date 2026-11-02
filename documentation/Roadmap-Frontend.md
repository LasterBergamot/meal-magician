# Roadmap: Frontend

Planned UI work (JTE + htmx + Alpine + Tailwind), by `PLAN.md` phase. Move items to
[[Current State: Frontend]] when they land.

- **UI redesign (all phases):** a storybook look (violet, gold, outlined "sticker" shapes, light and dark
  themes), a bottom tab bar on phones and a sidebar on desktop, a shared component set and a restyle of
  every existing page. It is planned in `plan-ui-redesign.md` (phases R0–R9) in the repository root,
  tracked by epic #57. Waves 1–3 have landed and Wave 4 (the restyles of the sign-in, household and
  admin pages, and the theme preference) is under way; the Phase 2 meal pages (#88) were built from
  its components, and the Phase 3–5 pages are built from them too. It runs in five **waves** so
  several subagents can work at once (tokens and icons first, then the components, then the shell,
  then the page restyles side by side, then the clean-up); each wave lists which files each item may
  touch, so parallel branches do not collide.
- **Phase 0 (rest):** real PWA artwork comes in Phase 5.
- **Phase 1:** household create/switch/rename landed in #41, the invite section and join page in
  #42, the members page in #43 and the allowlist admin page in #45; a "copy link" button is still
  open.
- **Phase 3:** week view (grid on desktop, day list on phone), slot editing, week navigation,
  conflict messages.
- **Phase 4:** copy day/week dialogs, template picker, occasion pages.
- **Phase 5:** rating prompts, print view, share link page, PWA polish (icons, install hint).
- **Phase 7:** "suggest my week" and chat-style refinement UI.
