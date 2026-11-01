package hu.lasterbergamot.mealmagician;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Guard tests for the agent-facing docs (CLAUDE.md, tests.md, claude-code.md). CLAUDE.md is a
 * router and the rule files load by {@code paths:} globs, so a renamed file or a typo in a glob
 * breaks them silently: the rule just stops loading. These tests catch that. {@code task
 * docs:check} runs this class.
 *
 * <ul>
 *   <li>CLAUDE.md stays under 100 lines;
 *   <li>every path and glob in its router table exists / matches a file;
 *   <li>every rule file has {@code paths:}, every glob matches a file and CLAUDE.md routes to it.
 * </ul>
 *
 * The checks are plain methods over a repository root, so the tests at the bottom run them on a
 * deliberately broken fixture in a temporary folder: the guard cannot silently stop guarding.
 */
class DocsToolingTests {

  private static final Path REPO = Path.of("").toAbsolutePath();
  private static final int MAX_CLAUDE_MD_LINES = 100;
  private static final Set<String> SKIPPED_FOLDERS =
      Set.of(".git", ".gradle", ".tools", ".idea", "build", "apm_modules", "node_modules");
  private static final Pattern BACKTICKED = Pattern.compile("`([^`]+)`");

  /**
   * Globs that match nothing yet because the code they describe arrives in a later phase. A glob
   * that starts matching must be removed from here: {@link #theAllowedDeadGlobsAreStillDead()}
   * fails otherwise, so this list cannot rot.
   */
  private static final Set<String> NOT_YET =
      Set.of(
          // The first JPA entity arrives with Phase 1; identity uses plain JDBC records so far.
          "src/main/java/**/internal/*Entity.java",
          "**/internal/*Entity.java",
          // No configuration package yet; configuration lives next to the module that owns it.
          "src/main/java/**/config/**",
          "**/config/**");

  // ---- the guards over the real repository ----

  @Test
  void claudeMdStaysUnderTheLineLimit() {
    assertThat(claudeMdProblems(REPO)).isEmpty();
  }

  @Test
  void everyPathInTheRouterTableExistsAndEveryGlobMatchesAFile() {
    assertThat(routerProblems(REPO, NOT_YET)).isEmpty();
  }

  @Test
  void everyRuleFileHasPathsAndEveryGlobMatchesAFile() {
    assertThat(ruleProblems(REPO, NOT_YET)).isEmpty();
  }

  @Test
  void claudeMdRoutesToEveryRuleFile() {
    assertThat(unroutedRuleFiles(REPO)).isEmpty();
  }

  @Test
  void theAllowedDeadGlobsAreStillDead() {
    List<String> files = repositoryFiles(REPO);
    List<String> alive = NOT_YET.stream().filter(glob -> matchesAny(glob, files)).sorted().toList();

    assertThat(alive).as("these globs now match files: remove them from NOT_YET").isEmpty();
  }

  // ---- the guards guard: a deliberately broken repository ----

  @Test
  void aTooLongClaudeMdIsReported(@TempDir Path repo) {
    write(repo, "CLAUDE.md", "line\n".repeat(MAX_CLAUDE_MD_LINES + 1));

    assertThat(claudeMdProblems(repo)).singleElement().asString().contains("101 lines");
  }

  @Test
  void aClaudeMdAtTheLimitIsFine(@TempDir Path repo) {
    write(repo, "CLAUDE.md", "line\n".repeat(MAX_CLAUDE_MD_LINES));

    assertThat(claudeMdProblems(repo)).isEmpty();
  }

  @Test
  void aMissingRouterPathAndADeadRouterGlobAreReported(@TempDir Path repo) {
    write(
        repo,
        "CLAUDE.md",
        """
        | Working on | Read |
        |---|---|
        | Plans | `PLAN.md` |
        | Java | `.claude/rules/java.md` (loads for `src/main/java/**`) |
        """);
    write(repo, "PLAN.md", "plan");

    assertThat(routerProblems(repo, Set.of()))
        .containsExactlyInAnyOrder(
            "CLAUDE.md router: .claude/rules/java.md does not exist",
            "CLAUDE.md router: glob src/main/java/** matches no file");
  }

  @Test
  void aWorkingRouterIsFine(@TempDir Path repo) {
    write(repo, "CLAUDE.md", "| Plans | `PLAN.md` |\n| Java | `src/main/java/**` |\n");
    write(repo, "PLAN.md", "plan");
    write(repo, "src/main/java/a/B.java", "class B {}");

    assertThat(routerProblems(repo, Set.of())).isEmpty();
  }

  @Test
  void aRuleWithoutPathsAndARuleWithADeadGlobAreReported(@TempDir Path repo) {
    write(repo, ".claude/rules/loose.md", "# No front matter\n");
    write(repo, ".claude/rules/dead.md", "---\npaths:\n  - \"src/nowhere/**\"\n---\n# Dead\n");
    write(repo, ".claude/rules/fine.md", "---\npaths:\n  - \"src/**\"\n---\n# Fine\n");
    write(repo, "src/A.java", "class A {}");

    assertThat(ruleProblems(repo, Set.of()))
        .containsExactlyInAnyOrder(
            ".claude/rules/loose.md has no paths: list (it would load in every session)",
            ".claude/rules/dead.md: glob src/nowhere/** matches no file");
  }

  @Test
  void anAllowedDeadGlobIsNotReported(@TempDir Path repo) {
    write(repo, ".claude/rules/later.md", "---\npaths:\n  - \"src/later/**\"\n---\n");

    assertThat(ruleProblems(repo, Set.of("src/later/**"))).isEmpty();
  }

  @Test
  void aRuleFileThatClaudeMdDoesNotMentionIsReported(@TempDir Path repo) {
    write(repo, "CLAUDE.md", "| Java | `.claude/rules/java.md` |\n");
    write(repo, ".claude/rules/java.md", "---\npaths:\n  - \"a\"\n---\n");
    write(repo, ".claude/rules/orphan.md", "---\npaths:\n  - \"a\"\n---\n");

    assertThat(unroutedRuleFiles(repo)).containsExactly(".claude/rules/orphan.md");
  }

  @Test
  void globsUnderBuildOutputDoNotCountAsMatches(@TempDir Path repo) {
    write(repo, "build/generated/Thing.java", "class Thing {}");
    write(repo, ".claude/rules/gen.md", "---\npaths:\n  - \"**/Thing.java\"\n---\n");

    assertThat(ruleProblems(repo, Set.of())).hasSize(1);
  }

  // ---- implementation ----

  /** CLAUDE.md is loaded in every session, so it has a line budget. */
  static List<String> claudeMdProblems(Path repo) {
    long lines = read(repo.resolve("CLAUDE.md")).lines().count();
    return lines > MAX_CLAUDE_MD_LINES
        ? List.of(
            "CLAUDE.md has %d lines, the limit is %d: move detail into .claude/rules or .claude/context"
                .formatted(lines, MAX_CLAUDE_MD_LINES))
        : List.of();
  }

  /** Every backticked path in a router-table row must exist; every backticked glob must match. */
  static List<String> routerProblems(Path repo, Set<String> allowedDead) {
    List<String> files = repositoryFiles(repo);
    List<String> problems = new ArrayList<>();
    read(repo.resolve("CLAUDE.md"))
        .lines()
        .filter(line -> line.startsWith("|"))
        .flatMap(line -> backticked(line).stream())
        .filter(token -> token.contains("*") || token.contains("/") || token.matches(".*\\.\\w+"))
        .forEach(token -> {
          if (token.contains("*")) {
            if (!allowedDead.contains(token) && !matchesAny(token, files)) {
              problems.add("CLAUDE.md router: glob %s matches no file".formatted(token));
            }
          } else if (!Files.exists(repo.resolve(token))) {
            problems.add("CLAUDE.md router: %s does not exist".formatted(token));
          }
        });
    return problems;
  }

  /** Every rule file needs a {@code paths:} list (or it loads always) and live globs. */
  static List<String> ruleProblems(Path repo, Set<String> allowedDead) {
    List<String> files = repositoryFiles(repo);
    List<String> problems = new ArrayList<>();
    for (String rule : ruleFiles(repo)) {
      List<String> globs = pathGlobs(read(repo.resolve(rule)));
      if (globs.isEmpty()) {
        problems.add("%s has no paths: list (it would load in every session)".formatted(rule));
      }
      for (String glob : globs) {
        if (!allowedDead.contains(glob) && !matchesAny(glob, files)) {
          problems.add("%s: glob %s matches no file".formatted(rule, glob));
        }
      }
    }
    return problems;
  }

  /** A rule file CLAUDE.md never names would never be found by the router. */
  static List<String> unroutedRuleFiles(Path repo) {
    String claudeMd = read(repo.resolve("CLAUDE.md"));
    return ruleFiles(repo).stream().filter(rule -> !claudeMd.contains(rule)).toList();
  }

  private static List<String> ruleFiles(Path repo) {
    Path rules = repo.resolve(".claude/rules");
    if (!Files.isDirectory(rules)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(rules)) {
      return files
          .filter(file -> file.toString().endsWith(".md"))
          .map(file -> repo.relativize(file).toString().replace('\\', '/'))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The list items under {@code paths:} in a file's front matter. */
  private static List<String> pathGlobs(String ruleFile) {
    List<String> lines = ruleFile.lines().toList();
    if (lines.isEmpty() || !lines.getFirst().equals("---")) {
      return List.of();
    }
    List<String> globs = new ArrayList<>();
    boolean inPaths = false;
    for (String line : lines.subList(1, lines.size())) {
      if (line.equals("---")) {
        break;
      }
      if (line.startsWith("paths:")) {
        inPaths = true;
      } else if (inPaths && line.matches("\\s*-\\s+.*")) {
        globs.add(line.replaceFirst("\\s*-\\s+", "").strip().replaceAll("^\"|\"$", ""));
      } else if (!line.isBlank() && !line.startsWith(" ")) {
        inPaths = false;
      }
    }
    return globs;
  }

  private static List<String> backticked(String line) {
    List<String> tokens = new ArrayList<>();
    Matcher matcher = BACKTICKED.matcher(line);
    while (matcher.find()) {
      tokens.add(matcher.group(1));
    }
    return tokens;
  }

  private static boolean matchesAny(String glob, List<String> files) {
    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
    return files.stream().anyMatch(file -> matcher.matches(Path.of(file)));
  }

  /** Repository files as slash-separated relative paths, without build output and tool folders. */
  private static List<String> repositoryFiles(Path repo) {
    List<String> files = new ArrayList<>();
    try {
      Files.walkFileTree(
          repo,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
              boolean skipped = SKIPPED_FOLDERS.contains(dir.getFileName().toString());
              return skipped && !dir.equals(repo)
                  ? FileVisitResult.SKIP_SUBTREE
                  : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
              files.add(repo.relativize(file).toString().replace('\\', '/'));
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return files;
  }

  private static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void write(Path repo, String relative, String content) {
    try {
      Path file = repo.resolve(relative);
      Files.createDirectories(file.getParent());
      Files.writeString(file, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
