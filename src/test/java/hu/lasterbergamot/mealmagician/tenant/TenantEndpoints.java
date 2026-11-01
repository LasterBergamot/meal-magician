package hu.lasterbergamot.mealmagician.tenant;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import java.util.List;
import java.util.Map;

/**
 * The registry of household-scoped endpoints. {@link TenantIsolationTests} fails when a controller
 * method that takes a {@code CurrentHousehold} (or a {@code {householdId}} path variable) is not
 * listed here, so a new endpoint cannot ship without its 404 and 403 checks.
 *
 * <p><b>To register an endpoint</b>, add one line to {@link #ALL}: the method, the mapping exactly
 * as the controller declares it, and the path variables that point at a resource of the home
 * household ({@link TenantWorld}). Use {@link TenantEndpoint#ownerOnly} when a plain member must
 * get 403. The guard then checks, with no further code:
 *
 * <ul>
 *   <li>a person in no household gets 404,
 *   <li>the owner of another household gets 404 for the home household's ids,
 *   <li>a plain member gets 403 on an owner-only endpoint,
 *   <li>the home owner is let through (the path is real, so the checks above are not vacuous).
 * </ul>
 *
 * <p>If the world has no fixture for your resource yet (a meal, a plan), add it to {@link
 * TenantWorld} and to {@link TenantIsolationTests#buildWorld}.
 */
public final class TenantEndpoints {

  private TenantEndpoints() {}

  private static final Map<String, Object> NONE = Map.of();

  /** Every registered endpoint. */
  public static final List<TenantEndpoint> ALL =
      List.of(
          // household: the person's own list is not household data, only these two take an id
          TenantEndpoint.forMembers(
              POST, "/households/{householdId}/switch", w -> Map.of("householdId", w.homeId())),
          TenantEndpoint.ownerOnly(
              POST, "/households/{householdId}/rename", w -> Map.of("householdId", w.homeId())),
          // members
          TenantEndpoint.forMembers(GET, "/households/members", w -> NONE),
          TenantEndpoint.ownerOnly(
              POST,
              "/households/members/{memberId}/remove",
              w -> Map.of("memberId", w.homeMember())),
          TenantEndpoint.ownerOnly(
              POST,
              "/households/members/{memberId}/transfer",
              w -> Map.of("memberId", w.homeMember())),
          TenantEndpoint.forMembers(POST, "/households/members/leave", w -> NONE),
          // invites
          TenantEndpoint.ownerOnly(POST, "/households/invites", w -> NONE),
          TenantEndpoint.ownerOnly(
              POST,
              "/households/invites/{inviteId}/revoke",
              w -> Map.of("inviteId", w.homeInviteId())));
}
