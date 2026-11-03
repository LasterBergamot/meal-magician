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
              w -> Map.of("inviteId", w.homeInviteId())),
          // occasions (#112): every member may plan occasions
          TenantEndpoint.forMembers(GET, "/occasions", w -> NONE),
          TenantEndpoint.forMembers(GET, "/occasions/new", w -> NONE),
          TenantEndpoint.forMembers(POST, "/occasions", w -> NONE),
          TenantEndpoint.forMembers(
              GET, "/occasions/{occasionId}", w -> Map.of("occasionId", w.homeOccasionId())),
          TenantEndpoint.forMembers(
              GET, "/occasions/{occasionId}/edit", w -> Map.of("occasionId", w.homeOccasionId())),
          TenantEndpoint.forMembers(
              POST, "/occasions/{occasionId}", w -> Map.of("occasionId", w.homeOccasionId())),
          TenantEndpoint.forMembers(
              POST,
              "/occasions/{occasionId}/delete",
              w -> Map.of("occasionId", w.homeOccasionId())),
          // occasion menus and occasion templates (#113): every member may edit and reuse them
          TenantEndpoint.forMembers(
              GET, "/occasions/{occasionId}/pick", w -> Map.of("occasionId", w.homeOccasionId())),
          TenantEndpoint.forMembers(
              POST, "/occasions/{occasionId}/menu", w -> Map.of("occasionId", w.homeOccasionId())),
          TenantEndpoint.forMembers(
              POST,
              "/occasions/{occasionId}/menu/quick",
              w -> Map.of("occasionId", w.homeOccasionId())),
          TenantEndpoint.forMembers(
              POST,
              "/occasions/{occasionId}/template",
              w -> Map.of("occasionId", w.homeOccasionId())),
          TenantEndpoint.forMembers(
              POST,
              "/occasions/{occasionId}/menu/{itemId}/remove",
              w -> Map.of("occasionId", w.homeOccasionId(), "itemId", w.homeOccasionItemId())),
          TenantEndpoint.forMembers(
              POST,
              "/occasions/{occasionId}/menu/{itemId}/move",
              w -> Map.of("occasionId", w.homeOccasionId(), "itemId", w.homeOccasionItemId())),
          TenantEndpoint.forMembers(
              GET,
              "/occasions/templates/{templateId}",
              w -> Map.of("templateId", w.homeOccasionTemplateId())),
          TenantEndpoint.forMembers(
              POST,
              "/occasions/templates/{templateId}/use",
              w -> Map.of("templateId", w.homeOccasionTemplateId())),
          TenantEndpoint.forMembers(
              POST,
              "/occasions/templates/{templateId}/delete",
              w -> Map.of("templateId", w.homeOccasionTemplateId())),
          // plan (planning): every member may plan
          TenantEndpoint.forMembers(GET, "/plan", w -> NONE),
          // plan templates (#111): every member may rename and delete
          TenantEndpoint.forMembers(GET, "/plan/templates", w -> NONE),
          TenantEndpoint.forMembers(
              GET, "/plan/templates/{templateId}", w -> Map.of("templateId", w.homeTemplateId())),
          TenantEndpoint.forMembers(
              POST,
              "/plan/templates/{templateId}/rename",
              w -> Map.of("templateId", w.homeTemplateId())),
          TenantEndpoint.forMembers(
              POST,
              "/plan/templates/{templateId}/delete",
              w -> Map.of("templateId", w.homeTemplateId())),
          TenantEndpoint.forMembers(GET, "/plan/pick", w -> NONE),
          TenantEndpoint.forMembers(POST, "/plan/slots", w -> NONE),
          TenantEndpoint.forMembers(POST, "/plan/slots/quick", w -> NONE),
          TenantEndpoint.forMembers(
              GET, "/plan/slots/{slotId}", w -> Map.of("slotId", w.homeSlotId())),
          TenantEndpoint.forMembers(
              POST, "/plan/slots/{slotId}/note", w -> Map.of("slotId", w.homeSlotId())),
          TenantEndpoint.forMembers(
              POST, "/plan/slots/{slotId}/move", w -> Map.of("slotId", w.homeSlotId())),
          TenantEndpoint.forMembers(
              POST, "/plan/slots/{slotId}/reorder", w -> Map.of("slotId", w.homeSlotId())),
          TenantEndpoint.forMembers(
              POST, "/plan/slots/{slotId}/remove", w -> Map.of("slotId", w.homeSlotId())),
          // plan reuse (#110): every member may copy and use templates
          TenantEndpoint.forMembers(GET, "/plan/reuse/day", w -> NONE),
          TenantEndpoint.forMembers(GET, "/plan/reuse/week", w -> NONE),
          TenantEndpoint.forMembers(POST, "/plan/days/copy", w -> NONE),
          TenantEndpoint.forMembers(POST, "/plan/weeks/copy", w -> NONE),
          TenantEndpoint.forMembers(POST, "/plan/templates", w -> NONE),
          TenantEndpoint.forMembers(
              POST,
              "/plan/templates/{templateId}/apply",
              w -> Map.of("templateId", w.homeTemplateId())),
          // ratings (#128): every member may rate and read the others' ratings
          TenantEndpoint.forMembers(GET, "/feedback/prompt", w -> NONE),
          TenantEndpoint.forMembers(POST, "/feedback/prompt/snooze", w -> NONE),
          TenantEndpoint.forMembers(
              GET, "/feedback/slots/{slotId}", w -> Map.of("slotId", w.homeSlotId())),
          TenantEndpoint.forMembers(
              POST, "/feedback/slots/{slotId}", w -> Map.of("slotId", w.homeSlotId())),
          TenantEndpoint.forMembers(
              POST, "/feedback/slots/{slotId}/clear", w -> Map.of("slotId", w.homeSlotId())),
          // meal history (#132): the lazy "How it went" block of a meal's page
          TenantEndpoint.forMembers(
              GET, "/feedback/meals/{mealId}", w -> Map.of("mealId", w.homeMealId())),
          // meals (catalog): every member may use the library, so none is owner-only
          TenantEndpoint.forMembers(GET, "/meals", w -> NONE),
          TenantEndpoint.forMembers(GET, "/meals/new", w -> NONE),
          TenantEndpoint.forMembers(POST, "/meals", w -> NONE),
          TenantEndpoint.forMembers(POST, "/meals/quick", w -> NONE),
          TenantEndpoint.forMembers(GET, "/meals/{mealId}", w -> Map.of("mealId", w.homeMealId())),
          TenantEndpoint.forMembers(
              GET, "/meals/{mealId}/edit", w -> Map.of("mealId", w.homeMealId())),
          TenantEndpoint.forMembers(POST, "/meals/{mealId}", w -> Map.of("mealId", w.homeMealId())),
          TenantEndpoint.forMembers(
              POST, "/meals/{mealId}/archive", w -> Map.of("mealId", w.homeMealId())),
          TenantEndpoint.forMembers(
              POST, "/meals/{mealId}/restore", w -> Map.of("mealId", w.homeMealId())));
}
