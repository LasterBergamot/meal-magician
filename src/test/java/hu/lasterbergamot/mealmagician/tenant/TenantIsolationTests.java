package hu.lasterbergamot.mealmagician.tenant;

import static hu.lasterbergamot.mealmagician.household.HouseholdTestSupport.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import hu.lasterbergamot.mealmagician.household.CurrentHousehold;
import hu.lasterbergamot.mealmagician.household.HouseholdService;
import hu.lasterbergamot.mealmagician.household.HouseholdTestSupport;
import hu.lasterbergamot.mealmagician.household.InviteExpiry;
import hu.lasterbergamot.mealmagician.household.InviteService;
import hu.lasterbergamot.mealmagician.tenant.TenantWorld.Actor;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Guard test (PLAN.md D8, tests.md): every household-scoped endpoint answers 404 across tenants and
 * 403 to a plain member on an owner-only action.
 *
 * <p>Two things are checked. {@link #everyHouseholdScopedEndpointIsRegistered} walks the mapped
 * controllers, finds the household-scoped ones (a {@link CurrentHousehold} parameter or a {@code
 * {householdId}} path variable) and fails for any that is missing from {@link TenantEndpoints}.
 * {@link #registeredEndpointsAnswerAsTheTenantRulesRequire} then sends the registered requests as
 * the people of a {@link TenantWorld} and compares the status codes. The decision logic is plain
 * methods, tested at the bottom with made-up leaky endpoints, so the guard cannot silently stop
 * guarding.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class TenantIsolationTests {

  @Autowired private MockMvc mvc;
  @Autowired private List<RequestMappingHandlerMapping> handlerMappings;
  @Autowired private HouseholdService households;
  @Autowired private InviteService invites;
  @Autowired private JdbcTemplate jdbc;

  static Stream<TenantEndpoint> registered() {
    return TenantEndpoints.ALL.stream();
  }

  // ---- the guard ----

  @Test
  void everyHouseholdScopedEndpointIsRegistered() {
    Set<String> scoped = householdScopedEndpoints();

    assertThat(scoped)
        .as("the walk found the household controllers")
        .contains("GET /households/members", "POST /households/invites");
    assertThat(unregistered(scoped, TenantEndpoints.ALL))
        .as("add each one to TenantEndpoints (how: see its Javadoc and tests.md)")
        .isEmpty();
    assertThat(staleRegistrations(scoped, TenantEndpoints.ALL))
        .as("these registrations match no household-scoped endpoint any more")
        .isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("registered")
  void registeredEndpointsAnswerAsTheTenantRulesRequire(TenantEndpoint endpoint) {
    TenantWorld world = buildWorld();

    assertThat(violations(endpoint, world, this::send)).isEmpty();
  }

  // ---- the guard guards: tests of the decision logic ----

  @Test
  void anEndpointThatShowsAnotherHouseholdsDataIsAViolation() {
    TenantEndpoint endpoint = leakable(false);
    TenantWorld world = world();

    assertThat(violations(endpoint, world, (actor, method, path) -> 200))
        .anyMatch(message -> message.contains("OTHER_OWNER") && message.contains("404"))
        .anyMatch(message -> message.contains("OUTSIDER") && message.contains("404"));
  }

  @Test
  void anEndpointWithoutIdsIsNotCheckedAgainstTheOtherHouseholdsOwner() {
    TenantEndpoint current = TenantEndpoint.forMembers(HttpMethod.GET, "/meals", w -> Map.of());

    // The other owner works on their own household there, so a 200 is right for them.
    assertThat(
            violations(
                current, world(), (actor, method, path) -> actor == Actor.OUTSIDER ? 404 : 200))
        .isEmpty();
  }

  @Test
  void anOwnerOnlyEndpointThatLetsAMemberInIsAViolation() {
    TenantEndpoint endpoint = leakable(true);

    List<String> found =
        violations(
            endpoint,
            world(),
            (actor, method, path) ->
                actor == Actor.HOME_OWNER || actor == Actor.HOME_MEMBER ? 200 : 404);

    assertThat(found).singleElement().asString().contains("HOME_MEMBER").contains("403");
  }

  @Test
  void aMemberGettingNotFoundInsteadOfForbiddenIsAViolation() {
    List<String> found =
        violations(
            leakable(true),
            world(),
            (actor, method, path) -> actor == Actor.HOME_OWNER ? 200 : 404);

    assertThat(found).singleElement().asString().contains("HOME_MEMBER").contains("403");
  }

  @Test
  void aPathThatTheOwnerCannotReachProvesNothing() {
    List<String> found = violations(leakable(false), world(), (actor, method, path) -> 404);

    assertThat(found)
        .singleElement()
        .asString()
        .contains("HOME_OWNER")
        .contains("neither 403 nor 404");
  }

  @Test
  void aServerErrorIsNeverTheRightAnswer() {
    List<String> found = violations(leakable(false), world(), (actor, method, path) -> 500);

    assertThat(found).isNotEmpty();
  }

  @Test
  void anEndpointThatAnswersEveryoneAsTheRulesRequireHasNoViolation() {
    TenantEndpoint endpoint = leakable(true);

    assertThat(
            violations(
                endpoint,
                world(),
                (actor, method, path) ->
                    switch (actor) {
                      case HOME_OWNER -> 200;
                      case HOME_MEMBER -> 403;
                      case OTHER_OWNER, OUTSIDER -> 404;
                    }))
        .isEmpty();
  }

  @Test
  void aScopedEndpointNobodyRegisteredIsReported() {
    Set<String> scoped = Set.of("GET /meals/{mealId}", "POST /households/leave");
    List<TenantEndpoint> registeredOnes =
        List.of(TenantEndpoint.forMembers(HttpMethod.POST, "/households/leave", w -> Map.of()));

    assertThat(unregistered(scoped, registeredOnes)).containsExactly("GET /meals/{mealId}");
  }

  @Test
  void aRegistrationForAnEndpointThatNoLongerExistsIsReported() {
    List<TenantEndpoint> registeredOnes =
        List.of(TenantEndpoint.forMembers(HttpMethod.GET, "/gone", w -> Map.of()));

    assertThat(staleRegistrations(Set.of("GET /here"), registeredOnes))
        .containsExactly("GET /gone");
  }

  @Test
  void aPathVariableWithoutAValueIsAnIncompleteRegistration() {
    TenantEndpoint incomplete =
        TenantEndpoint.forMembers(HttpMethod.GET, "/meals/{mealId}", w -> Map.of());

    assertThatThrownBy(() -> incomplete.pathIn(world()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("{mealId}");
  }

  @Test
  void pathVariablesAreFilledWithTheHomeHouseholdsIds() {
    TenantEndpoint endpoint =
        TenantEndpoint.forMembers(
            HttpMethod.GET,
            "/h/{householdId}/days/{date:\\d{4}}",
            w -> Map.of("householdId", w.homeId(), "date", 2026));

    assertThat(endpoint.pathIn(world())).isEqualTo("/h/1/days/2026");
  }

  // ---- implementation ----

  /** What a request answered; the stub in the self-tests and MockMvc below both are one. */
  @FunctionalInterface
  interface Sender {
    int status(Actor actor, HttpMethod method, String path);
  }

  /**
   * Sends the checks of one endpoint and lists every answer that breaks a tenant rule. The home
   * owner goes last because some actions (remove, transfer) change the world.
   */
  static List<String> violations(TenantEndpoint endpoint, TenantWorld world, Sender sender) {
    String path = endpoint.pathIn(world);
    List<String> found = new ArrayList<>();
    expect(found, endpoint, path, Actor.OUTSIDER, 404, sender);
    if (endpoint.addressesResources()) {
      // Without ids in the path the request works on the caller's own current household, which is
      // legitimate for the other owner; with ids, the id must be looked up inside that household.
      expect(found, endpoint, path, Actor.OTHER_OWNER, 404, sender);
    }
    if (endpoint.ownerOnly()) {
      expect(found, endpoint, path, Actor.HOME_MEMBER, 403, sender);
    }
    int status = sender.status(Actor.HOME_OWNER, endpoint.method(), path);
    if (status == 403 || status == 404 || status >= 500) {
      found.add(
          "%s as HOME_OWNER answered %d, neither 403 nor 404 nor an error was expected: the path %s is not a real home resource"
              .formatted(endpoint.key(), status, path));
    }
    return found;
  }

  private static void expect(
      List<String> found,
      TenantEndpoint endpoint,
      String path,
      Actor actor,
      int wanted,
      Sender sender) {
    int status = sender.status(actor, endpoint.method(), path);
    if (status != wanted) {
      found.add(
          "%s as %s answered %d, not %d (%s)"
              .formatted(endpoint.key(), actor, status, wanted, path));
    }
  }

  /** Household-scoped endpoints that no registration covers. */
  static List<String> unregistered(Set<String> scoped, List<TenantEndpoint> registrations) {
    Set<String> keys = registrations.stream().map(TenantEndpoint::key).collect(toSet());
    return scoped.stream().filter(key -> !keys.contains(key)).toList();
  }

  /** Registrations whose endpoint is gone (renamed, removed or no longer household-scoped). */
  static List<String> staleRegistrations(Set<String> scoped, List<TenantEndpoint> registrations) {
    return registrations.stream()
        .map(TenantEndpoint::key)
        .filter(key -> !scoped.contains(key))
        .toList();
  }

  private static java.util.stream.Collector<String, ?, Set<String>> toSet() {
    return java.util.stream.Collectors.toSet();
  }

  private static TenantEndpoint leakable(boolean ownerOnly) {
    return new TenantEndpoint(
        HttpMethod.GET, "/meals/{mealId}", ownerOnly, w -> Map.of("mealId", 7));
  }

  private static TenantWorld world() {
    return new TenantWorld(1, 2, 10, 11, 20, 30, 40);
  }

  /** "METHOD /pattern" of every mapped handler that works on a household. */
  private Set<String> householdScopedEndpoints() {
    Set<String> keys = new TreeSet<>();
    for (RequestMappingHandlerMapping mapping : handlerMappings) {
      mapping
          .getHandlerMethods()
          .forEach((info, handler) -> {
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
              if (isHouseholdScoped(handler, pattern)) {
                for (String verb : verbs(info)) {
                  keys.add(verb + " " + pattern);
                }
              }
            }
          });
    }
    return keys;
  }

  private static boolean isHouseholdScoped(HandlerMethod handler, String pattern) {
    boolean takesCurrentHousehold =
        java.util.Arrays.stream(handler.getMethodParameters())
            .anyMatch(parameter -> CurrentHousehold.class.equals(parameter.getParameterType()));
    return takesCurrentHousehold || pattern.contains("{householdId}");
  }

  private static List<String> verbs(RequestMappingInfo info) {
    Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
    return methods.isEmpty() ? List.of("GET") : methods.stream().map(Enum::name).toList();
  }

  private int send(Actor actor, HttpMethod method, String path) {
    try {
      long userId = currentWorld.userId(actor);
      return mvc.perform(request(method, path).with(signedInAs(userId)).with(csrf()))
          .andReturn()
          .getResponse()
          .getStatus();
    } catch (Exception e) {
      throw new IllegalStateException("The request " + method + " " + path + " threw", e);
    }
  }

  private TenantWorld currentWorld;

  /**
   * Two households with their people and one pending invite in the home household. The other
   * household's owner is deliberately in no other household, so the session's default household is
   * the other one.
   */
  private TenantWorld buildWorld() {
    long homeOwner = HouseholdTestSupport.insertUser(jdbc, "home-owner");
    long homeMember = HouseholdTestSupport.insertUser(jdbc, "home-member");
    long otherOwner = HouseholdTestSupport.insertUser(jdbc, "other-owner");
    long outsider = HouseholdTestSupport.insertUser(jdbc, "outsider");
    long homeId = households.create(homeOwner, "Home").id();
    long otherId = households.create(otherOwner, "Other").id();
    jdbc.update(
        "insert into membership (household_id, user_id, role, joined_at) values (?, ?, 'MEMBER', ?)",
        homeId,
        homeMember,
        OffsetDateTime.now());
    long inviteId =
        invites.create(homeId, homeOwner, "guest@example.com", InviteExpiry.HOURS_24).invite().id();
    currentWorld =
        new TenantWorld(homeId, otherId, homeOwner, homeMember, otherOwner, outsider, inviteId);
    return currentWorld;
  }
}
