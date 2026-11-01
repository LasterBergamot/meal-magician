package hu.lasterbergamot.mealmagician;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code scripts/next_release.sh} decides, for the automatic release (#51), whether a commit on
 * {@code main} is deployed and as which version. It runs here against a throw-away git repository
 * whose last release is {@code v0.1.1}, so a wrong rule shows up before it reaches production.
 */
class ReleasePlanScriptTest {

  private static final Path SCRIPT = Path.of("scripts/next_release.sh").toAbsolutePath();

  @TempDir Path repo;

  @BeforeEach
  void repositoryWithOneRelease() throws Exception {
    git("init", "-q", "-b", "main");
    git("config", "user.email", "test@example.com");
    git("config", "user.name", "Test");
    commit("src/App.java", "first");
    // As release.yml does it: the tag sits on a commit that only bumps the version, off main.
    String released = git("rev-parse", "HEAD").trim();
    commit("build.gradle.kts", "Release v0.1.1");
    git("tag", "v0.1.1");
    git("checkout", "-q", "--detach", released);
    git("branch", "-f", "main", released);
    git("checkout", "-q", "main");
  }

  @Test
  void aCodeChangeGetsTheNextPatchVersion() throws Exception {
    commit("src/Other.java", "code");

    assertThat(plan("workflow_run", "", "", "patch"))
        .containsEntry("release", "true")
        .containsEntry("version", "0.1.2");
  }

  @Test
  void aReleaseLabelRaisesTheMinorOrMajorPart() throws Exception {
    commit("src/Other.java", "code");

    assertThat(plan("workflow_run", "bug\nrelease:minor", "", "patch"))
        .containsEntry("version", "0.2.0");
    assertThat(plan("workflow_run", "release:minor,release:major", "", "patch"))
        .containsEntry("version", "1.0.0");
  }

  @Test
  void documentationAndCiOnlyChangesAreNotDeployed() throws Exception {
    commit("README.md", "docs");
    commit(".github/workflows/ci.yml", "ci");
    commit("documentation/Setup.md", "wiki");

    assertThat(plan("workflow_run", "", "", "patch")).containsEntry("release", "false");
  }

  @Test
  void aDocsOnlyMergeStillShipsEarlierCodeThatWasNeverReleased() throws Exception {
    commit("src/Other.java", "code whose deploy failed");
    commit("README.md", "docs");

    assertThat(plan("workflow_run", "", "", "patch"))
        .containsEntry("release", "true")
        .containsEntry("version", "0.1.2");
  }

  @Test
  void theReleasedCommitItselfIsNotReleasedAgain() throws Exception {
    assertThat(plan("workflow_run", "", "", "patch")).containsEntry("release", "false");
  }

  @Test
  void aCommitOlderThanTheLastReleaseIsNotDeployed() throws Exception {
    commit("src/Other.java", "released later");
    String newer = git("rev-parse", "HEAD").trim();
    git("tag", "-f", "v0.1.2", newer);
    git("checkout", "-q", "--detach", "HEAD~1");

    assertThat(plan("workflow_run", "", "", "patch")).containsEntry("release", "false");
  }

  @Test
  void aManualRunReleasesTheTypedVersionOrABump() throws Exception {
    assertThat(plan("workflow_dispatch", "", "2.0.0", "patch"))
        .containsEntry("release", "true")
        .containsEntry("version", "2.0.0");
    assertThat(plan("workflow_dispatch", "", "", "minor")).containsEntry("version", "0.2.0");
  }

  @Test
  void aTypedVersionMustBeNewAndWellFormed() throws Exception {
    assertThat(run("workflow_dispatch", "", "0.1.1", "patch").exit()).isNotZero();
    assertThat(run("workflow_dispatch", "", "v1.0", "patch").exit()).isNotZero();
    assertThat(run("workflow_dispatch", "", "", "huge").exit()).isNotZero();
  }

  private void commit(String file, String message) throws Exception {
    Path path = repo.resolve(file);
    Files.createDirectories(path.getParent());
    Files.writeString(path, message + System.nanoTime());
    git("add", "-A");
    git("commit", "-q", "-m", message);
  }

  private Map<String, String> plan(String event, String labels, String version, String bump)
      throws Exception {
    Result result = run(event, labels, version, bump);
    assertThat(result.exit()).as(result.output()).isZero();
    return result
        .output()
        .lines()
        .filter(line -> line.contains("="))
        .collect(
            java.util.stream.Collectors.toMap(
                line -> line.substring(0, line.indexOf('=')),
                line -> line.substring(line.indexOf('=') + 1),
                (first, second) -> first));
  }

  private Result run(String event, String labels, String version, String bump) throws Exception {
    ProcessBuilder builder = new ProcessBuilder("bash", SCRIPT.toString()).directory(repo.toFile());
    builder.redirectErrorStream(true);
    builder.environment().put("EVENT_NAME", event);
    builder.environment().put("SHA", git("rev-parse", "HEAD").trim());
    builder.environment().put("LABELS", labels);
    builder.environment().put("VERSION_INPUT", version);
    builder.environment().put("BUMP_INPUT", bump);
    builder.environment().remove("GITHUB_OUTPUT");
    Process process = builder.start();
    String output = new String(process.getInputStream().readAllBytes());
    return new Result(process.waitFor(), output);
  }

  private String git(String... args) throws IOException, InterruptedException {
    List<String> command = new java.util.ArrayList<>(List.of("git"));
    command.addAll(List.of(args));
    ProcessBuilder builder = new ProcessBuilder(command).directory(repo.toFile());
    builder.redirectErrorStream(true);
    Process process = builder.start();
    String output = new String(process.getInputStream().readAllBytes());
    assertThat(process.waitFor()).as("git %s: %s", String.join(" ", args), output).isZero();
    return output;
  }

  private record Result(int exit, String output) {}
}
