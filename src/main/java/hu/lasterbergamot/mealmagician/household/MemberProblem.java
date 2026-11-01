package hu.lasterbergamot.mealmagician.household;

/** Why a membership change was refused. The members page shows a message for each. */
public enum MemberProblem {
  /** The person is the only owner, so leaving would leave the household with nobody in charge. */
  LAST_OWNER("household.members.error.last-owner"),
  /** An owner tried to remove or hand ownership to themselves; leaving has its own action. */
  SELF("household.members.error.self");

  private final String messageKey;

  MemberProblem(String messageKey) {
    this.messageKey = messageKey;
  }

  /**
   * The key of the message that explains the problem to the person.
   *
   * @return a key of {@code messages_*.properties}
   */
  public String messageKey() {
    return messageKey;
  }
}
