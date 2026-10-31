# Current State: Backend

What exists in `main` today: **the build skeleton only**, no business logic yet.

- **Application class** `MealMagicianApplication` starts Spring Boot; everything lives below its
  package `hu.lasterbergamot.mealmagician`.
- **Modules** (Spring Modulith): `identity`, `household`, `catalog`, `planning`, `feedback`,
  `sharing` and the open shared kernel `shared`. Each is an empty package with a
  `package-info.java` that says what the module will own. The rule: a module's root package is
  its public API, everything else goes in `internal/` and other modules may not import it.
  `ModularityTests` fails the build on a violation and writes module docs to
  `build/spring-modulith-docs`.
- **`shared.ClockConfig`** provides the one `Clock` bean (Europe/Budapest). Code asks it for
  "today" instead of calling `LocalDate.now()`, so tests can fix the date.
- **Config:** `application.yml` turns on virtual threads and graceful shutdown, and tells JTE to
  use its precompiled templates (there are none yet).

The planned modules, data model and request flow are in [[Roadmap: Backend]] and `PLAN.md` §4.
