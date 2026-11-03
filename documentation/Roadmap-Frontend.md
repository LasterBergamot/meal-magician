# Roadmap: Frontend

Planned UI work (JTE + htmx + Alpine + Tailwind), by `PLAN.md` phase. Move items to
[[Current State: Frontend]] when they land.

- **UI redesign (all phases):** a storybook look (violet, gold, outlined "sticker" shapes, light and dark
  themes), a bottom tab bar on phones and a sidebar on desktop, a shared component set and a restyle of
  every existing page. It is planned in `plan-ui-redesign.md` (phases R0–R9) in the repository root,
  tracked by epic #57. Waves 1–5 have landed, so the components, the shell and every restyled page
  are in `main`; the Phase 2 meal pages (#88) and the Phase 3 week view (#97, #98) were built from
  them. What is left is R9, the owner's close-out on a real phone, where the gold on the phone's Plan
  and Meals screens is judged live. The work ran in five **waves** so several subagents could work at
  once; each wave listed which files each item may touch, so parallel branches did not collide.
- **Phase 1:** household create/switch/rename landed in #41, the invite section and join page in
  #42, the members page in #43 and the allowlist admin page in #45; a "copy link" button is still
  open.
- **Phase 4:** done (#110–#113), see [[Current State: Frontend]]: the reuse sheets on the plan,
  the plan template pages, and the occasion pages with their menus and templates.
- **Phase 5:** done (#124 to #132), see [[Current State: Frontend]]: the rating prompt and form, the
  meal's "How it went" block, the print page and the share sheet, the public share page and the PWA
  install hint. What is left is the owner's Lighthouse check and install on a phone (#134).
- **Phase 7:** "suggest my week" and chat-style refinement UI.
