package hu.lasterbergamot.mealmagician.tenant;

/**
 * The two households and four people every tenant-isolation check runs against.
 *
 * <p>Everything an endpoint can address belongs to the <em>home</em> household. The guard then asks
 * who may reach it: its owner must, its plain member may except on owner-only actions (403), and
 * neither the owner of the <em>other</em> household nor someone in no household at all may (404).
 *
 * @param homeId the household whose resources the registered paths point at
 * @param otherId an unrelated household that must never see the home household's data
 * @param homeOwner owner of the home household
 * @param homeMember plain member of the home household
 * @param otherOwner owner of the other household (and only of it)
 * @param outsider a signed-in person who belongs to no household
 * @param homeInviteId a pending invite of the home household
 * @param homeMealId a meal in the home household's library
 * @param homeSlotId a planned slot of the home household
 * @param homeTemplateId a day template of the home household
 * @param homeOccasionId an occasion of the home household
 * @param homeOccasionItemId the occasion's one menu item
 * @param homeOccasionTemplateId an occasion template of the home household
 * @param homeShareLinkId an active share link of the home household's week of 2026-11-02
 */
public record TenantWorld(
    long homeId,
    long otherId,
    long homeOwner,
    long homeMember,
    long otherOwner,
    long outsider,
    long homeInviteId,
    long homeMealId,
    long homeSlotId,
    long homeTemplateId,
    long homeOccasionId,
    long homeOccasionItemId,
    long homeOccasionTemplateId,
    long homeShareLinkId) {

  /**
   * Who sends a request in a check.
   *
   * @see TenantWorld
   */
  public enum Actor {
    HOME_OWNER,
    HOME_MEMBER,
    OTHER_OWNER,
    OUTSIDER
  }

  /**
   * The {@code app_user} id of an actor.
   *
   * @param actor who is asking
   * @return their user id
   */
  public long userId(Actor actor) {
    return switch (actor) {
      case HOME_OWNER -> homeOwner;
      case HOME_MEMBER -> homeMember;
      case OTHER_OWNER -> otherOwner;
      case OUTSIDER -> outsider;
    };
  }
}
