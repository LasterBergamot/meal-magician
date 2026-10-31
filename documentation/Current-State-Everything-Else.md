# Current State: Everything Else

What exists in `main` today:

- **Claude Code setup:** router `CLAUDE.md`, `.claude/context/` (workflow, PR checklist, issues,
  docs map, audit, claude-code), path-scoped `.claude/rules/`, `.claude/settings.json`.
- **Shared skills** from `ai-common` `workflow#v0.1.1` via APM (`apm.yml`, `apm.lock.yaml`):
  `create-pr`, `new-issue`, `repo-audit`, `setup-project`, `triage-dependabot`,
  `update-documentation`, and the `convention-reviewer` agent.
- **Docs:** `README.md`, `PLAN.md`, this wiki source in `documentation/`, the PR template.
- **GitHub:** public repo `LasterBergamot/meal-magician`. `main` is protected (PRs required,
  no force-push), merges are merge commits only, the wiki is on. Dependabot alerts and secret
  scanning are on, and the labels, milestones and phase epics (#1–#8) exist.
- **Workflow conventions:** `main`-only branching, merge commits, `#N - subject` commits
  (`.claude/context/workflow.md`).

- **Build (#9):** Gradle 9.8.2 (Kotlin DSL, committed wrapper) with versions in
  `gradle/libs.versions.toml`. The Java toolchain (Temurin 25, via the foojay resolver) is
  downloaded automatically, so only *some* JDK is needed to start Gradle. `./gradlew build` runs:
  - **Spotless** (google-java-format for Java, ktlint for `*.gradle.kts`); `./gradlew spotlessApply` fixes formatting.
  - **Checkstyle**, which only checks Javadoc: every public type and method in `src/main/java`
    needs one, and every package needs a `package-info.java`.
  - **Tests** (JUnit 5, AssertJ) and a **JaCoCo gate** requiring 100 % line and branch coverage.
    Excluded, with reasons in `build.gradle.kts`: the class holding `main()` and the generated
    JTE classes. Lombok-generated code is skipped through `lombok.config`.
- **Lombok** is configured (`lombok.config`) but not used yet.

Not yet: Taskfile, CI, container, hosting — see [[Roadmap: Everything Else]].
