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
 */
public record TenantWorld(
    long homeId,
    long otherId,
    long homeOwner,
    long homeMember,
    long otherOwner,
    long outsider,
    long homeInviteId) {

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
