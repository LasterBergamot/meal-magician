---
paths:
  - "src/main/java/**"
  - "build.gradle.kts"
  - "gradle/libs.versions.toml"
  - "lombok.config"
---

# Java modules (Spring Modulith)

- Base package `hu.lasterbergamot.mealmagician` (PLAN.md D20). Modules: `identity`,
  `household`, `catalog`, `planning`, `feedback`, `sharing`, `assistant`; `shared` is the
  shared kernel. The module table (who owns what, which events) is PLAN.md §4.1.
- **`assistant`** depends on `planning`, `feedback`, `catalog`, `household`, `identity` (the
  controllers of #160 use `SignedInUser`; amends the plan's list) and `shared`, and nothing depends
  on it. It listens to no events. `assistant.internal.ModelGateway` is the only class that calls the
  model. The model's tools (`AssistantTools`) are read-only, bound to one user and household in the
  constructor and never take an id from the model; its only write into another module is
  `PlanningService.applyDraft`, called when a member accepts a draft. Never put a display name or
  e-mail address into a prompt (`Pseudonyms`), and keep model calls outside database transactions.
- **A module's root package is its public API** (services, DTO/command records, events).
  Everything else lives in `<module>/internal/` (entities, repositories, controllers). Never
  import another module's `internal` package; `ApplicationModules.of(...).verify()` in
  `ModularityTests` fails the build if you do.
- Cross-module side effects go through **application events** (`@ApplicationModuleListener`),
  not direct calls, e.g. `MealChanged` → `assistant`. Name events in past tense, as records.
- **Needing data from a module that depends on you:** `planning` depends on `catalog`, so
  `catalog` cannot call `PlanningService` (that would be a cycle). The module that is depended on
  declares an interface in its public package, and the other module implements it in its `internal`
  package: `catalog.RecentMealsProvider`, implemented by `planning.internal.PlanningRecentMeals`.
  The arrow stays `planning -> catalog`. Shared types follow the same reasoning: `OccasionType`
  lives in `catalog`.
- **Unchecked readers (D28):** the rule "every public service method checks household membership"
  has exactly two guarded exceptions, both for the public share-link page where nobody is signed
  in: `planning.SharedWeekReader` (used only by `sharing`) and `catalog.MealNameLookup` (used only
  by `planning`). They take a household id from a stored, active share link, return minimal
  record types (`SharedWeek`) and still scope every query by household. `UncheckedReadersTest`
  (ArchUnit) fails if any other module depends on them. Do not add a third one without the owner's
  OK, and keep each one tiny and read-only.
- Every package, including each `internal/`, needs a `package-info.java` with Javadoc (Checkstyle
  `JavadocPackage`). A module's root `package-info.java` carries `@ApplicationModule(displayName = …)`;
  `shared` is `type = OPEN` because every module may use it.
- Controllers return JTE views; an htmx request (`HX-Request` header) gets a fragment, otherwise
  the full page. Keep business rules in services, not controllers or templates.
- Use **records** for DTOs, commands, events and value objects. **Lombok** is for JPA entities
  (`@Getter`, `@NoArgsConstructor(access = PROTECTED)`, `@Builder` where useful); never
  `@Data` on entities (equals/hashCode on lazy relations). `lombok.config` sets
  `lombok.addLombokGeneratedAnnotation = true` so JaCoCo skips generated code.
- Build: Gradle wrapper + toolchain `languageVersion = 25`, `vendor = ADOPTIUM` with the foojay
  resolver (PLAN.md D21); never rely on the machine's default JDK.
- Constructor injection only (no field `@Autowired`). Configuration via `@ConfigurationProperties`
  records with `mm.` prefix.
- Keep it native-friendly (PLAN.md D17): no runtime classpath scanning tricks, no reflection
  without hints, JTE precompiled.
- Javadoc on every public type and method: what it is for, the non-obvious why, what it throws.
- Time: `Clock` bean (Europe/Budapest), never `LocalDate.now()` without it — tests need a fixed clock.
