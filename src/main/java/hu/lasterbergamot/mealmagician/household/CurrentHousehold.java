package hu.lasterbergamot.mealmagician.household;

/**
 * The household the current request works on, already checked against the signed-in person's
 * membership. Controllers of other modules take it as a method parameter and pass {@link #id()} to
 * every repository call, which is how household data stays scoped (persistence.md).
 *
 * @param id the household id, to scope queries with
 * @param name the household's display name
 * @param role the signed-in person's role in it
 */
public record CurrentHousehold(long id, String name, HouseholdRole role) {

  /**
   * Whether the signed-in person may run owner-only actions here.
   *
   * @return {@code true} for an {@link HouseholdRole#OWNER}
   */
  public boolean isOwner() {
    return role == HouseholdRole.OWNER;
  }
}
