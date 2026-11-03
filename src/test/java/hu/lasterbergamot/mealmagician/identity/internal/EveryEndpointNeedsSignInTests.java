package hu.lasterbergamot.mealmagician.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Guard test (security.md): walks every endpoint a controller maps and fails if one that is not in
 * {@link SecurityConfig#PUBLIC_PATHS} can be used without signing in.
 *
 * <p>A new controller is covered the moment it exists; nobody has to remember to add a test. Each
 * mapping is called anonymously (with a CSRF token, so the answer comes from the authorization rule
 * and not from the CSRF filter). A protected endpoint answers with a redirect to the login page.
 * The decision logic is a plain method, tested below with a made-up leaky endpoint, so the guard
 * cannot silently stop guarding.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class EveryEndpointNeedsSignInTests {

  private static final AntPathMatcher PATHS = new AntPathMatcher();

  @Autowired private MockMvc mvc;
  @Autowired private List<RequestMappingHandlerMapping> handlerMappings;

  @Test
  void noMappedEndpointOutsideThePublicListWorksAnonymously() throws Exception {
    List<Probe> probes = probeEveryMappedEndpoint();

    assertThat(probes)
        .as("the walk found the app's controllers")
        .extracting(Probe::path)
        .contains("/", "/login", "/error");
    assertThat(
            anonymousLeaks(
                probes,
                List.of(SecurityConfig.PUBLIC_PATHS),
                List.of(SecurityConfig.PUBLIC_GET_PATHS)))
        .as("add the path to SecurityConfig.PUBLIC_PATHS only if it is meant to be public")
        .isEmpty();
  }

  // ---- the guard guards: tests of the decision logic ----

  @Test
  void anOpenEndpointThatIsNotListedAsPublicIsALeak() {
    Probe leaky = new Probe("GET", "/admin/allowlist", 200, null);

    assertThat(anonymousLeaks(List.of(leaky), List.of("/login"), List.of()))
        .singleElement()
        .asString()
        .contains("GET /admin/allowlist");
  }

  @Test
  void aServerErrorIsNotProofThatTheEndpointIsProtected() {
    Probe broken = new Probe("POST", "/households", 500, null);

    assertThat(anonymousLeaks(List.of(broken), List.of("/login"), List.of())).hasSize(1);
  }

  @Test
  void aRedirectToAnotherPageIsNotProtection() {
    Probe elsewhere = new Probe("GET", "/share/1", 302, "/somewhere-else");

    assertThat(anonymousLeaks(List.of(elsewhere), List.of("/login"), List.of())).hasSize(1);
  }

  @Test
  void aRedirectToTheLoginPageIsProtection() {
    Probe protectedEndpoint = new Probe("GET", "/households", 302, "/login");

    assertThat(anonymousLeaks(List.of(protectedEndpoint), List.of("/login"), List.of())).isEmpty();
  }

  @Test
  void anOpenEndpointListedAsPublicIsAllowed() {
    Probe loginPage = new Probe("GET", "/login", 200, null);
    Probe asset = new Probe("GET", "/css/app.css", 200, null);

    assertThat(anonymousLeaks(List.of(loginPage, asset), List.of("/login", "/css/**"), List.of()))
        .isEmpty();
  }

  @Test
  void aGetOnlyPublicPatternDoesNotOpenOtherMethods() {
    Probe read = new Probe("GET", "/share/1", 404, null);
    Probe write = new Probe("POST", "/share/1", 200, null);

    assertThat(anonymousLeaks(List.of(read), List.of("/login"), List.of("/share/*"))).isEmpty();
    assertThat(anonymousLeaks(List.of(write), List.of("/login"), List.of("/share/*")))
        .singleElement()
        .asString()
        .contains("POST /share/1");
  }

  @Test
  void aGetOnlyPublicPatternDoesNotOpenLongerPaths() {
    Probe deeper = new Probe("GET", "/share/1/2", 200, null);

    assertThat(anonymousLeaks(List.of(deeper), List.of("/login"), List.of("/share/*"))).hasSize(1);
  }

  @Test
  void pathVariablesAndWildcardsBecomeConcretePaths() {
    assertThat(concretePath("/households/{id}/days/{date:\\d{4}-\\d{2}-\\d{2}}"))
        .isEqualTo("/households/1/days/1");
    assertThat(concretePath("/files/**")).isEqualTo("/files/x");
  }

  // ---- implementation ----

  /** One anonymous call to one mapped endpoint and what came back. */
  record Probe(String method, String path, int status, String redirect) {}

  /**
   * Endpoints that answered an anonymous caller with anything but "go and sign in", minus the paths
   * the security configuration declares public. {@code publicGetPatterns} open a path for GET only,
   * so a POST to the same path still has to be sent to the login page.
   */
  static List<String> anonymousLeaks(
      List<Probe> probes, List<String> publicPatterns, List<String> publicGetPatterns) {
    List<String> leaks = new ArrayList<>();
    for (Probe probe : probes) {
      boolean sentToLogin = probe.status() == 302 && "/login".equals(probe.redirect());
      boolean declaredPublic =
          publicPatterns.stream().anyMatch(pattern -> PATHS.match(pattern, probe.path()))
              || ("GET".equals(probe.method())
                  && publicGetPatterns.stream()
                      .anyMatch(pattern -> PATHS.match(pattern, probe.path())));
      if (!sentToLogin && !declaredPublic) {
        leaks.add(
            "%s %s answered %d to an anonymous caller"
                .formatted(probe.method(), probe.path(), probe.status()));
      }
    }
    return leaks;
  }

  /** Turns {@code /a/{id}/b/**} into a path a request can use. */
  static String concretePath(String pattern) {
    return pattern.replaceAll("\\{[^/]*}", "1").replace("**", "x").replace("*", "x");
  }

  private List<Probe> probeEveryMappedEndpoint() throws Exception {
    List<Probe> probes = new ArrayList<>();
    for (RequestMappingHandlerMapping handlerMapping : handlerMappings) {
      for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        List<String> verbs =
            methods.isEmpty() ? List.of("GET") : methods.stream().map(Enum::name).toList();
        for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
          for (String verb : verbs) {
            probes.add(probe(verb, concretePath(pattern)));
          }
        }
      }
    }
    return probes;
  }

  private Probe probe(String verb, String path) throws Exception {
    var response =
        mvc.perform(request(HttpMethod.valueOf(verb), path).with(csrf())).andReturn().getResponse();
    return new Probe(verb, path, response.getStatus(), response.getRedirectedUrl());
  }
}
