# Meal Magician

A private, household-only meal planner: plan breakfasts, lunches, dinners and special occasions,
reuse meals, days and weeks, rate what you ate, and (when the owner switches it on) get AI-drafted
plans from an optional assistant.

**Status:** Phases 0–5, the ingredients (Phase 6) and the AI assistant (Phase 7, off by default) are
built: planning the week, reusing days and weeks, templates and occasions with their menus, ratings,
read-only share links and the print view, the PWA install hint, and week drafts, a chat and occasion
menus from the Wizard. What is left is the owner's phone checks (#134), the review of the assistant's
eval report with the model sign-off (D33), and v1.0.0. The working plan lives in the
repo's `PLAN.md`.

## Pages

- [[Setup]]: from a fresh clone to a working dev environment (people and agents).
- [[Newcomer Guide]]: tools and why, glossary, how a request flows, your first change.
- [[References]]: official docs for everything we use.
- Runbooks: [[Auth Setup]] (Google OAuth client), [[Hosting Setup]] (Cloud Run + Neon, deploys),
  [[Backup and Restore]] (what is backed up, the one-time setup, how to restore).
- [[AI Assistant]]: what the optional assistant does, what data it sees, costs and caps, how to turn it
  off, how to widen the chat, and how the owner runs and reviews the model eval.
- Current state: [[Current State: Backend]], [[Current State: Frontend]], [[Current State: Everything Else]].
- Roadmap: [[Roadmap: Backend]], [[Roadmap: Frontend]], [[Roadmap: Everything Else]].
